package com.smartdoorlock;

import androidx.appcompat.app.AppCompatActivity;

import android.graphics.Bitmap;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;

import net.glxn.qrgen.android.QRCode;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;

public class MainActivity extends AppCompatActivity {
    private static String TAG = "MainActivityWithAudio";

    private static final String DESTINATION = "192.168.35.100";
    private static final String LOCALHOST = "192.168.35.98";
    private static final int PORT = 50005;

    private static final int RECORDING_RATE = 44100;
    private static final int CHANNEL = AudioFormat.CHANNEL_IN_MONO;
    private static final int FORMAT = AudioFormat.ENCODING_PCM_16BIT;

    private AudioRecord recorder;

    private static int BUFFER_SIZE = AudioRecord.getMinBufferSize(RECORDING_RATE, CHANNEL, FORMAT);
    private static int Rx_BUFFER_SIZE = 2048;

    private boolean currentlySendingAudio = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Bitmap bt = QRCode.from("Go Yeongu JonJal").bitmap();
        ImageView iv = findViewById(R.id.iv_QR);
        iv.setImageBitmap(bt);

        Log.i(TAG, "Starting the background thread to stream the audio data");
        startStreaming();
        startSpeakers();


    }

    private void startStreaming() {

        Log.i(TAG, "Starting the background thread to stream the audio data");

        Thread streamThread = new Thread(new Runnable() {

            @Override
            public void run() {
                try {

                    Log.d(TAG, "Creating the datagram socket");
                    DatagramSocket socket = new DatagramSocket();

                    Log.d(TAG, "Creating the buffer of size " + BUFFER_SIZE);
                    byte[] buffer = new byte[BUFFER_SIZE];

                    Log.d(TAG, "Connecting to " + DESTINATION + ":" + PORT);
                    final InetAddress serverAddress = InetAddress
                            .getByName(DESTINATION);
                    Log.d(TAG, "Connected to " + DESTINATION + ":" + PORT);

                    Log.d(TAG, "Creating the reuseable DatagramPacket");
                    DatagramPacket packet;

                    Log.d(TAG, "Creating the AudioRecord");
                    recorder = new AudioRecord(MediaRecorder.AudioSource.MIC,
                            RECORDING_RATE, CHANNEL, FORMAT, BUFFER_SIZE * 10);

                    Log.d(TAG, "AudioRecord recording...");
                    recorder.startRecording();

                    while (currentlySendingAudio) {

                        // read the data into the buffer
                        int read = recorder.read(buffer, 0, buffer.length);

                        // place contents of buffer into the packet
                        packet = new DatagramPacket(buffer, read,
                                serverAddress, PORT);

                        // send the packet
                        socket.send(packet);
                    }

                    Log.d(TAG, "AudioRecord finished recording");

                } catch (Exception e) {
                    Log.e(TAG, "Exception: " + e);
                }
            }
        });

        // start the thread
        streamThread.start();
    }

    public void startSpeakers() {
        // Creates the thread for receiving and playing back audio

        Thread receiveThread = new Thread(new Runnable() {

            @Override
            public void run() {

                // Create an instance of AudioTrack, used for playing back audio
                Log.i(TAG, "Receive thread started. Thread id: " + Thread.currentThread().getId());
/*                AudioTrack track = new AudioTrack(
                        new AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                .build(),
                        new AudioFormat.Builder()
                                .setChannelIndexMask(1)
                                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                .setSampleRate(RECORDING_RATE)
                                .build(),
                        BUFFER_SIZE, AudioTrack.MODE_STREAM, 123123);*/
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
/*                AudioTrack track = new AudioTrack(AudioManager.STREAM_MUSIC, RECORDING_RATE, AudioFormat.CHANNEL_OUT_MONO,
                        AudioFormat.ENCODING_PCM_16BIT, BUFFER_SIZE, AudioTrack.MODE_STREAM);*/
                track.play();

                try {
                    // Define a socket to receive the audio
                    DatagramSocket socket = new DatagramSocket(PORT + 1);
                    byte[] buf = new byte[Rx_BUFFER_SIZE];
                    while (true) {
                        // Play back the audio received from packets
                        DatagramPacket packet = new DatagramPacket(buf, Rx_BUFFER_SIZE);
                        socket.receive(packet);
                        Log.i(TAG, "Packet received: " + packet.getLength());
                        track.write(packet.getData(), 0, Rx_BUFFER_SIZE);
                    }
                    // Stop playing back and release resources
/*                        socket.disconnect();
                        socket.close();
                        track.stop();
                        track.flush();
                        track.release();
                        return;*/
                } catch (SocketException e) {

                    Log.e(TAG, "SocketException: " + e.toString());
                } catch (IOException e) {

                    Log.e(TAG, "IOException: " + e.toString());
                }
            }
        });
        receiveThread.start();
    }
}
