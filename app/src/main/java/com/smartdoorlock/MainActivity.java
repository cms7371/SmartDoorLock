package com.smartdoorlock;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;

import net.glxn.qrgen.android.QRCode;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.Random;

public class MainActivity extends AppCompatActivity{
    private static String TAG = "MainActivity";

    private static final String DESTINATION = "192.168.137.111";
    private static final String LOCALHOST = "192.168.35.169";
    private static final int PORT = 50005;

    private static final int RECORDING_RATE = 44100;
    private static final int CHANNEL = AudioFormat.CHANNEL_IN_MONO;
    private static final int FORMAT = AudioFormat.ENCODING_PCM_16BIT;


    private static int BUFFER_SIZE = AudioRecord.getMinBufferSize(RECORDING_RATE, CHANNEL, FORMAT);
    private static int Rx_BUFFER_SIZE = 2048;

    private boolean streamingState = false;
    private boolean serverState = false;

    private Context context = this;

    private Handler H = new Handler(Looper.getMainLooper());

    private Bitmap tempQR;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Bitmap bt = QRCode.from("Prof. Yang09").withSize(500,500).bitmap();
        ImageView iv = findViewById(R.id.iv_QR);
        iv.setImageBitmap(bt);
        startUDPCommandServer();
        findViewById(R.id.bt_VOIP).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startCalling();
            }
        });
        findViewById(R.id.bt_key_gen).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                StringBuffer temp = new StringBuffer();
                Random rnd = new Random();
                for (int i = 0; i < 10; i++) {
                    temp.append((char) (33 + rnd.nextInt(93)));
                }
                sendUDPCommand("KYGEN:"+ temp.toString());
                //KYCNF로 옮겨야 하는 코드
                tempQR = QRCode.from(temp.toString()).withSize(500, 500).bitmap();
                KeyGenDialog dialog = new KeyGenDialog(context, tempQR);
                KeyGenDialog.KeyGenDialogListener listener = new KeyGenDialog.KeyGenDialogListener() {
                    @Override
                    public void onShare() {
                        Intent sharingIntent = new Intent(Intent.ACTION_SEND);
                        sharingIntent.putExtra(Intent.EXTRA_STREAM, getImageUri(context, tempQR));
                        sharingIntent.setType("image/jpeg");
                        startActivity(Intent.createChooser(sharingIntent, "일회용 키 공유"));
                    }
                };
                dialog.setListener(listener);
                dialog.show();
            }
        });
    }


    private void startCalling(){

        H.postDelayed(new Runnable() {
            @Override
            public void run() {
                receiveAudio();
                streamAudio();
                CamDialog camDialog = new CamDialog(context, DESTINATION);
                camDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialogInterface) {
                        streamingState = false;
                        try {
                            voiceReceivingSocket.close();
                        } catch (Exception e){
                            Log.d(TAG, "Audio receiving socket is not opened.");
                        }
                    }
                });
                CamDialog.CamDialogListener listener = new CamDialog.CamDialogListener() {
                    @Override
                    public void onCamDialogOpen() {
                        sendUDPCommand("DOPEN");
                    }
                };
                camDialog.setListener(listener);
                camDialog.show();
            }
        }, 1000);


    }

    private void streamAudio() {

        Log.i(TAG, "Starting the background thread to stream the audio data");

        // read the data into the buffer
        // place contents of buffer into the packet
        // send the packet
        Thread transmit = new Thread(new Runnable() {

            @Override
            public void run() {
                try {

                    Log.d(TAG, "Creating the datagram socket");
                    DatagramSocket socket = new DatagramSocket();

                    Log.d(TAG, "Creating the buffer of size " + BUFFER_SIZE);
                    byte[] buffer = new byte[BUFFER_SIZE];

                    Log.d(TAG, "Connecting to " + DESTINATION + ":" + PORT);
                    final InetAddress destinationAddress = InetAddress
                            .getByName(DESTINATION);
                    Log.d(TAG, "Connected to " + DESTINATION + ":" + PORT);

                    Log.d(TAG, "Creating the reuseable DatagramPacket");
                    DatagramPacket packet;

                    Log.d(TAG, "Creating the AudioRecord");
                    AudioRecord recorder = new AudioRecord(MediaRecorder.AudioSource.MIC,
                            RECORDING_RATE, CHANNEL, FORMAT, BUFFER_SIZE * 10);

                    Log.d(TAG, "AudioRecord recording...");
                    recorder.startRecording();
                    int pCounter = 0;

                    while (streamingState) {

                        // read the data into the buffer
                        int read = recorder.read(buffer, 0, buffer.length);

                        // place contents of buffer into the packet
                        packet = new DatagramPacket(buffer, read,
                                destinationAddress, PORT);

                        // send the packet
                        socket.send(packet);
                        pCounter++;
                        if (pCounter >= 50) {
                            Log.d(TAG, "50 packets transmitted. len : " + packet.getLength());
                            pCounter = 0;
                        }
                    }
                    socket.disconnect();
                    socket.close();
                    recorder.stop();
                    recorder.release();
                    Log.d(TAG, "AudioRecord finished recording");

                } catch (Exception e) {
                    Log.e(TAG, "Exception: " + e);
                }
            }
        });
        streamingState = true;
        // start the thread
        transmit.start();
    }

    DatagramSocket voiceReceivingSocket;

    public void receiveAudio() {
        // Creates the thread for receiving and playing back audio

        Thread receive = new Thread(new Runnable() {

            @Override
            public void run() {

                // Create an instance of AudioTrack, used for playing back audio
                Log.i(TAG, "Receive thread started. Thread id: " + Thread.currentThread().getId());
                AudioTrack track = new AudioTrack.Builder()
                        .setAudioAttributes(new AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                .build())
                        .setAudioFormat(new AudioFormat.Builder()
                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                .setSampleRate(RECORDING_RATE)
                                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO
                                ).build())
                        .setBufferSizeInBytes(Rx_BUFFER_SIZE)
                        .build();
                track.play();

                try {
                    // Define a socket to receive the audio
                    int pCounter = 0;
                    voiceReceivingSocket = new DatagramSocket(PORT + 1);
                    byte[] buf = new byte[Rx_BUFFER_SIZE];
                    while (streamingState) {
                        // Play back the audio received from packets
                        DatagramPacket packet = new DatagramPacket(buf, Rx_BUFFER_SIZE);
                        voiceReceivingSocket.receive(packet);

                        track.write(packet.getData(), 0, Rx_BUFFER_SIZE);
                        pCounter++;
                        if (pCounter >= 50) {
                            Log.i(TAG, "packets received: " + packet.getLength());
                            pCounter = 0;
                        }
                    }
                    // Stop playing back and release resources

                } catch (SocketException e) {
                    Log.e(TAG, "SocketException: " + e.toString());
                } catch (IOException e) {
                    Log.e(TAG, "IOException: " + e.toString());
                }
                track.stop();
                track.flush();
                track.release();

                Log.d(TAG, "Finished receiving packets");
            }
        });
        streamingState = true;
        receive.start();
    }

    DatagramSocket commandReceivingSocket;

    public void startUDPCommandServer() {
        Thread th = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    int port = PORT + 2;
                    commandReceivingSocket = new DatagramSocket(port);
                    while (serverState) {
                        byte[] buffer = new byte[32];
                        DatagramPacket dp = new DatagramPacket(buffer, buffer.length);
                        System.out.println("ready");
                        commandReceivingSocket.receive(dp);
                        String str = new String(dp.getData());
                        System.out.println("수신된 데이터 : " + str);
                        String command = str.substring(0, 5);
                        switch (command){
                            case "SCALL" :
                                startCalling();
                                break;
                            case "KYCNF" :

                            default:
                                break;
                        }
                        InetAddress ia = dp.getAddress();
                        port = dp.getPort();
                        System.out.println("client ip : " + ia + " , client port : " + port);
                        dp = new DatagramPacket(dp.getData(), dp.getData().length, ia, port);
                        commandReceivingSocket.send(dp);
                    }
                } catch (Exception e) {
                    Log.e(TAG, e + " on UDP server");
                }
            }
        });
        serverState = true;
        th.start();
    }



    private void sendUDPCommand(String command){
        final String message = command;
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {

                    DatagramSocket commandSendingSocket = new DatagramSocket();
                    InetAddress destinationAddress = InetAddress.getByName(DESTINATION);
                    byte[] commandBuffer = message.getBytes();
                    DatagramPacket commandPacket = new DatagramPacket(commandBuffer, commandBuffer.length, destinationAddress, PORT + 3);
                    commandSendingSocket.send(commandPacket);
                    Log.d(TAG, "Command : " + message + "  TRANSMITTED");


                } catch (Exception e) {
                    Log.e(TAG, e + " on UDP command sender");
                }
            }
        });
        thread.start();
    }

    private Uri getImageUri(Context context, Bitmap inImage) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        inImage.compress(Bitmap.CompressFormat.JPEG, 100, bytes);
        String path = MediaStore.Images.Media.insertImage(context.getContentResolver(), inImage, "Title", null);
        return Uri.parse(path);
    }


    @Override
    protected void onDestroy() {
        commandReceivingSocket.close();
        serverState = false;
        super.onDestroy();
    }


}
