package com.mesflit.ses;

import android.app.Service;
import android.content.Intent;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.IBinder;
import android.util.Log;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class MyBackgroundService extends Service {
    private static final String TAG = "MyBackgroundService";
    private AudioRecord audioRecorder;
    private AudioManager audioManager;
    private int sampleRate = 44100;
    private int channelConfig = AudioFormat.CHANNEL_IN_MONO;
    private int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
    private int bufferSize;
    private boolean isRecording = false;
    private File outputFile;
    private DataOutputStream dataOutputStream;

    @Override
    public void onCreate() {
        super.onCreate();
        bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startRecording();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        stopRecording();
        super.onDestroy();
    }

    private void startRecording() {
        if (isRecording) {
            return;
        }

        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
        audioManager.setSpeakerphoneOn(false); // Hoparlörü kapalı, kulaklıkta ses çıkaracak

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

        Log.d(TAG, "Recording started...");
    }

    private void stopRecording() {
        if (!isRecording) {
            return;
        }

        isRecording = false;
        audioRecorder.stop();
        audioRecorder.release();
        audioRecorder = null;

        try {
            dataOutputStream.close();
            Log.d(TAG, "Recording stopped. Audio file saved at: " + outputFile.getAbsolutePath());
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Ses çıkışını tekrar normal moda (MODE_NORMAL) geri getir
        audioManager.setMode(AudioManager.MODE_NORMAL);
        audioManager.setSpeakerphoneOn(true); // Hoparlörü açık, normal ses çıkışı
    }

    private void writeAudioDataToFile() {
        byte[] audioData = new byte[bufferSize];

        while (isRecording) {
            int bytesRead = audioRecorder.read(audioData, 0, bufferSize);
            if (bytesRead != AudioRecord.ERROR_INVALID_OPERATION) {
                try {
                    dataOutputStream.write(audioData);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
