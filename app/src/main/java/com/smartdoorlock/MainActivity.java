package com.smartdoorlock;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
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
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;

import net.glxn.qrgen.android.QRCode;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.Random;

public class MainActivity extends AppCompatActivity {
    //로그를 구분하기 위한 태그
    private static String TAG = "MainActivity";
    //UDP 통신을 위한 라즈베리파이의 IP 정보
    private static final String DESTINATION = "192.168.137.111";
    private static final int PORT = 50005;
    //오디오 입출력을 위한 샘플링, 채널, 포맷, 버퍼 정보
    private static final int RECORDING_RATE = 44100;
    private static final int CHANNEL = AudioFormat.CHANNEL_IN_MONO;
    private static final int FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private static int BUFFER_SIZE = AudioRecord.getMinBufferSize(RECORDING_RATE, CHANNEL, FORMAT);
    private static int Rx_BUFFER_SIZE = 2048;
    //오디오 스트리밍 상태를 나타내고 제어해주는 변수
    private boolean streamingState = false;
    private boolean serverState = false;
    //Thread 안에서 MainActivity 의 Context 를 호출해주기 위한 변수
    private Context context = this;
    //Thread 안에서 Dialog 를 호출해주기 위한 Handler
    private Handler H = new Handler(Looper.getMainLooper());
    //일회용 키값과 그에 대한 QR을 저장해주는 변수
    private Bitmap tempQR;
    private String tempKey;
    //알림을 띄우기 위한 매니져 및 ID
    private NotificationManager notificationManager;
    private final static String CHANNEL_ID = "SmartDoorLock";


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //기본 베이스 레이아웃 지정
        setContentView(R.layout.activity_main);
        //알림 초기 설정
        notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        NotificationChannel notificationChannel = new NotificationChannel(CHANNEL_ID, "name",
                NotificationManager.IMPORTANCE_HIGH);
        notificationChannel.enableVibration(true);
        notificationManager.createNotificationChannel(notificationChannel);
        //명령을 받는 UDP 서버 시작
        startUDPCommandServer();
        //통화 버튼 동작 할당
        findViewById(R.id.bt_VOIP).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                sendUDPCommand("SCALL");
                startCalling();
            }
        });
        //일회용 QR 생성 버튼 동작 할당
        findViewById(R.id.bt_key_gen).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //랜덤한 ASCII 문자열을 생성함
                StringBuilder temp = new StringBuilder();
                Random rnd = new Random();
                for (int i = 0; i < 10; i++) {
                    temp.append((char) (33 + rnd.nextInt(93)));
                }
                //임시키로 저장하고 라즈베리파이에 키 생성 요청
                tempKey = temp.toString();
                sendUDPCommand("KYGEN" + tempKey);
                Toast.makeText(context, "비밀번호 생성을 요청합니다.",
                        Toast.LENGTH_SHORT).show();
            }
        });
    }
    //영상통화 시작 메서드
    private void startCalling() {
        //라즈베리 파이의 동작 시간으로 인해, 1초의 딜레이 후 실행
        H.postDelayed(new Runnable() {
            @Override
            public void run() {
                //오디오 스트리밍 및 수신 시작
                streamingState = true;
                receiveAudio();
                streamAudio();
                //다이얼로그를 띄워 라즈베리파이가 송출하는 영상 수신
                CamDialog camDialog = new CamDialog(context, DESTINATION);
                //다이얼로그를 닫을 때 음성 통신 또한 종료되도록 리스너 설정
                camDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialogInterface) {
                        streamingState = false;
                        try {
                            voiceReceivingSocket.close();
                        } catch (Exception e) {
                            Log.d(TAG, "Audio receiving socket is not opened.");
                        }
                    }
                });
                //다이얼로그의 문 열기 버튼 입력 시 라즈베리파이로 문여는 커맨드 송출
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

    //핸드폰의 음성을 라즈베리파이로 송신해주는 메서드
    private void streamAudio() {
        Log.i(TAG, "Starting the background thread to stream the audio data");
        //백그라운드 동작을 위해 thread 로 정의
        Thread transmit = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    //통신 목적지, 소켓, 버퍼, 패킷, 음성 레코더 정의
                    DatagramSocket socket = new DatagramSocket();
                    final InetAddress destinationAddress = InetAddress
                            .getByName(DESTINATION);
                    byte[] buffer = new byte[BUFFER_SIZE];
                    Log.d(TAG, "Creating the reusable DatagramPacket");
                    DatagramPacket packet;
                    Log.d(TAG, "Creating the AudioRecord");
                    AudioRecord recorder = new AudioRecord(MediaRecorder.AudioSource.MIC,
                            RECORDING_RATE, CHANNEL, FORMAT, BUFFER_SIZE * 10);
                    Log.d(TAG, "AudioRecord recording...");
                    recorder.startRecording();
                    //디버그를 위한 패킷 카운터
                    int pCounter = 0;
                    //thread 가 동작하는 동안 계속 음성을 레코드하여 소켓으로 송신
                    while (streamingState) {
                        //레코드를 버퍼로 읽어들임
                        int read = recorder.read(buffer, 0, buffer.length);
                        //버퍼를 패킷에 실어줌
                        packet = new DatagramPacket(buffer, read,
                                destinationAddress, PORT);
                        //생성된 패킷 전송
                        socket.send(packet);
                        pCounter++;
                        if (pCounter >= 50) {
                            Log.d(TAG, "50 packets transmitted. len : " + packet.getLength());
                            pCounter = 0;
                        }
                    }
                    //종료 후 다음 음성 송신을 위해 소켓과 레코더를 정리
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
        //정의한 thread 시작
        transmit.start();
    }
    //음성 수신 소켓, 수신의 경우 blocking state 가 있어 추가적으로 닫아줄 수 있도록
    // 클래스 내 로컬변수로 정의
    DatagramSocket voiceReceivingSocket;
    //라즈베리파이의 음성을 수신하여 재생해주는 메서드
    public void receiveAudio() {
        //백그라운드 동작을 위해 thread 로 정의함
        Thread receive = new Thread(new Runnable() {
            @Override
            public void run() {
                //재생을 위한 AudioTrack 정의
                Log.i(TAG, "Audio receive thread started. Thread id: " +
                        Thread.currentThread().getId());
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
                    //디버킹을 위한 패킷 카운터
                    int pCounter = 0;
                    //수신 포트, 버퍼를 할당
                    voiceReceivingSocket = new DatagramSocket(PORT + 1);
                    byte[] buf = new byte[Rx_BUFFER_SIZE];
                    while (streamingState) {
                        //패킷 정의 후 수신 대기
                        DatagramPacket packet = new DatagramPacket(buf, Rx_BUFFER_SIZE);
                        voiceReceivingSocket.receive(packet);
                        //수신 되면 AudioTrack 으로 보내 재생함
                        track.write(packet.getData(), 0, Rx_BUFFER_SIZE);
                        pCounter++;
                        if (pCounter >= 50) {
                            Log.i(TAG, "packets received: " + packet.getLength());
                            pCounter = 0;
                        }
                    }
                } catch (SocketException e) {
                    Log.e(TAG, "SocketException: " + e.toString());
                } catch (IOException e) {
                    Log.e(TAG, "IOException: " + e.toString());
                }
                //streamingState 가 false 가 되어 루프를 탈출하면 track 정리
                track.stop();
                track.flush();
                track.release();
                Log.d(TAG, "Finished receiving packets");
            }
        });
        //정의한 thread 시작
        receive.start();
    }
    //명령 수신 소켓 또한 blocking state 를 탈출해주기 위해 클래스 내 로컬 변수로 정의
    DatagramSocket commandReceivingSocket;
    //라즈베리 파이로부터 제어를 받기 위한 서버를 시작하는 메서드
    public void startUDPCommandServer() {
        Thread th = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    //문열림, 잘못된 QR 입력, 택배 도착 3가지의 알림을 정의
                    NotificationCompat.Builder correctNotiBuilder = new NotificationCompat
                            .Builder(context, CHANNEL_ID)
                            .setContentTitle("SmartDoorLock")
                            .setContentText("문이 열렸습니다.")
                            .setPriority(NotificationCompat.PRIORITY_HIGH)
                            .setSmallIcon(R.drawable.ic_padlock_unlocked);
                    NotificationCompat.Builder incorrectNotiBuilder = new NotificationCompat
                            .Builder(context, CHANNEL_ID)
                            .setContentTitle("SmartDoorLock")
                            .setContentText("잘못된 QR이 입력되었습니다. 확인해보세요.")
                            .setPriority(NotificationCompat.PRIORITY_HIGH)
                            .setSmallIcon(R.drawable.ic_padlock_locked);
                    NotificationCompat.Builder deliveryNotiBuilder = new NotificationCompat
                            .Builder(context, CHANNEL_ID)
                            .setContentTitle("SmartDoorLock")
                            .setContentText("택배 또는 배달이 왔습니다. 확인해보세요.")
                            .setPriority(NotificationCompat.PRIORITY_HIGH)
                            .setSmallIcon(R.drawable.ic_box)
                            .setLargeIcon(((BitmapDrawable)getDrawable(R.drawable.ic_box))
                                    .getBitmap());
                    //명령 수신을 위한 포트를 정의하여 포켓에 연결
                    int port = PORT + 2;
                    commandReceivingSocket = new DatagramSocket(port);
                    //serverState 가 false 가 되어 닫히기 전까지 계속 동작함
                    while (serverState) {
                        //버퍼와 패킷 정의
                        byte[] buffer = new byte[32];
                        DatagramPacket dp = new DatagramPacket(buffer, buffer.length);
                        commandReceivingSocket.receive(dp);
                        Log.d(TAG, "Command receiving server is ready");
                        //패킷을 받으면 해석하여 동작
                        String str = new String(dp.getData());
                        System.out.println("수신된 데이터 : " + str);
                        String command = str.substring(0, 5);
                        //명령은 첫 5글자로 정의됨
                        switch (command) {
                            //영상 통화 시작 명령
                            case "SCALL":
                                startCalling();
                                break;
                            //QR 생성에서 KYGEN에 대한 response
                            case "KYCNF":
                                //저장된 tempKey 와 받은 response 의 키가 같아야 동작
                                if (tempKey.equals(str.substring(5, 15))) {
                                    //thread 내에서 dialog 를 호출하기 위해 Handler 이용
                                    H.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            //QR을 생성하여 QR 다이얼로그를 띄움
                                            tempQR = QRCode.from(tempKey)
                                                    .withSize(500, 500).bitmap();
                                            KeyGenDialog dialog = new KeyGenDialog(context, tempQR);
                                            //공유 버튼 동작
                                            KeyGenDialog.KeyGenDialogListener listener = new KeyGenDialog.KeyGenDialogListener() {
                                                @Override
                                                public void onShare() {
                                                    Intent sharingIntent =
                                                            new Intent(Intent.ACTION_SEND);
                                                    sharingIntent.putExtra(Intent.EXTRA_STREAM,
                                                            getImageUri(context, tempQR));
                                                    sharingIntent.setType("image/jpeg");
                                                    startActivity(Intent
                                                            .createChooser(sharingIntent,
                                                                    "일회용 키 공유"));
                                                }
                                            };
                                            dialog.setListener(listener);
                                            dialog.show();
                                        }
                                    });
                                } else {
                                    Toast.makeText(context, "키생성에 문제가 발생했습니다.",
                                            Toast.LENGTH_SHORT).show();
                                }
                                break;
                            //맞는 QR 입력 시 알림 발생
                            case "CRTQR":
                                notificationManager.notify(0, correctNotiBuilder.build());
                                break;
                            //틀린 QR 입력 시 알림 발생
                            case "INCQR":
                                notificationManager.notify(1, incorrectNotiBuilder.build());
                                break;
                            //택배 도착 시 알림 발생
                            case "FEDEX":
                                notificationManager.notify(2, deliveryNotiBuilder.build());
                            default:
                                break;
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, e + " on UDP server");
                }
            }
        });
        //동작 변수를 활성화하고 정의한 thread 동작
        serverState = true;
        th.start();
    }
    //라즈베리 파이로 커맨드를 보내는 메서드
    private void sendUDPCommand(String command) {
        //command 를 thread 로 전달하기 위한 String
        final String message = command;
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    //UDP 소켓을 이용하여 커맨드를 라즈베리파이로 전달
                    DatagramSocket commandSendingSocket = new DatagramSocket();
                    InetAddress destinationAddress = InetAddress.getByName(DESTINATION);
                    byte[] commandBuffer = message.getBytes();
                    DatagramPacket commandPacket = new DatagramPacket(commandBuffer,
                            commandBuffer.length, destinationAddress, PORT + 3);
                    commandSendingSocket.send(commandPacket);
                    Log.d(TAG, "Command : " + message + "  TRANSMITTED");
                } catch (Exception e) {
                    Log.e(TAG, e + " on UDP command sender");
                }
            }
        });
        thread.start();
    }
    //QR 공유를 위해 Bitmap 이미지를 Uri 로 바꿔주는 메서트
    private Uri getImageUri(Context context, Bitmap inImage) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        inImage.compress(Bitmap.CompressFormat.JPEG, 100, bytes);
        String path = MediaStore.Images.Media.insertImage(context.getContentResolver(), inImage,
                "Title", null);
        return Uri.parse(path);
    }
    //앱 종료 시 남은 thread 가 종료 될 수 있도록 함
    @Override
    protected void onDestroy() {
        commandReceivingSocket.close();
        serverState = false;
        super.onDestroy();
    }
}
