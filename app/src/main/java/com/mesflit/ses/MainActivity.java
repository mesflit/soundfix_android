package com.mesflit.ses;

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class MainActivity extends AppCompatActivity {
    private static final int RECORD_AUDIO_PERMISSION_REQUEST_CODE = 101;
    private static final String TAG = "MainActivity";
    private Button startButton;
    private Button stopButton;
    private static final int BUFFER_SIZE = 1048576; // 1 MB'lık önbellek boyutu
    private byte[] audioBuffer = new byte[BUFFER_SIZE];
    private int bytesRead;
    private boolean isRecording = false;
    private AudioRecord audioRecorder;
    private int sampleRate = 44100;
    private int channelConfig = AudioFormat.CHANNEL_IN_MONO;
    private int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
    private int bufferSize;
    private File outputFile;
    private DataOutputStream dataOutputStream;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        startButton = findViewById(R.id.btn_start);
        stopButton = findViewById(R.id.btn_stop);

        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (checkPermission()) {
                    startBackgroundService();
                } else {
                    requestPermission();
                }
            }
        });

        stopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopBackgroundService();
            }
        });
    }

    private boolean checkPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    private void requestPermission() {
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.RECORD_AUDIO)) {
            new AlertDialog.Builder(this)
                    .setTitle("Permission Required")
                    .setMessage("App requires microphone access permission to work")
                    .setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.RECORD_AUDIO}, RECORD_AUDIO_PERMISSION_REQUEST_CODE);
                        }
                    })
                    .setNegativeButton("Cancel", null)
                    .create()
                    .show();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, RECORD_AUDIO_PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == RECORD_AUDIO_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startBackgroundService();
            } else {
                showPermissionDeniedDialog();
            }
        }
    }

    private void showPermissionDeniedDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Permission Denied")
                .setMessage("The application does not work because there is no microphone access.")
                .setPositiveButton("Settings", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                        Uri uri = Uri.fromParts("package", getPackageName(), null);
                        intent.setData(uri);
                        startActivity(intent);
                    }
                })
                .setNegativeButton("Cancel", null)
                .create()
                .show();
    }

    private void startBackgroundService() {
        bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat);
        audioRecorder = new AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, channelConfig, audioFormat, bufferSize);
        audioRecorder.startRecording();
        isRecording = true;

        // Create the output file for saving audio data
        String filePath = getExternalFilesDir(null).getAbsolutePath() + "/recorded_audio.pcm";
        outputFile = new File(filePath);

        try {
            dataOutputStream = new DataOutputStream(new FileOutputStream(outputFile));
        } catch (IOException e) {
            e.printStackTrace();
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                writeAudioDataToFile();
            }
        }).start();
    }

    private void stopBackgroundService() {
        if (!isRecording) {
            return;
        }

        isRecording = false;
        audioRecorder.stop();
        audioRecorder.release();
        audioRecorder = null;

        try {
            dataOutputStream.close();
            dataOutputStream = null;
            audioBuffer = null;
            bytesRead = 0;
            Log.d(TAG, "Recording stopped. Audio file saved at: " + outputFile.getAbsolutePath());
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Ses çıkışını tekrar normal moda (MODE_NORMAL) geri getir
        AudioManager audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        audioManager.setMode(AudioManager.MODE_NORMAL);
        audioManager.setSpeakerphoneOn(true); // Hoparlörü açık, normal ses çıkışı
    }

    private void writeAudioDataToFile() {
        while (isRecording) {
            bytesRead = audioRecorder.read(audioBuffer, 0, BUFFER_SIZE);
            if (bytesRead != AudioRecord.ERROR_INVALID_OPERATION && bytesRead > 0) {
                try {
                    dataOutputStream.write(audioBuffer, 0, bytesRead);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
