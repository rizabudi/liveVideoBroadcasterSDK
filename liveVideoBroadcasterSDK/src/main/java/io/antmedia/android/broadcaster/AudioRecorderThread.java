package io.antmedia.android.broadcaster;

import android.media.AudioFormat;
import android.media.MediaRecorder;
import android.os.Message;
import android.util.Log;

import io.antmedia.android.broadcaster.encoder.AudioHandler;

/**
 * Created by mekya on 28/03/2017.
 */

class AudioRecorderThread extends Thread {

    private static final String TAG = AudioRecorderThread.class.getSimpleName();
    private final int mSampleRate;
    private final long startTime;
    private volatile boolean stopThread = false;

    private android.media.AudioRecord audioRecord;
    private AudioHandler audioHandler;
    private IByteReceive byteReceive;

    public AudioRecorderThread(int sampleRate, long recordStartTime, AudioHandler audioHandler, IByteReceive byteReceive) {
        this.mSampleRate = sampleRate;
        this.startTime = recordStartTime;
        this.audioHandler = audioHandler;
        this.byteReceive = byteReceive;
    }


    @Override
    public void run() {
        //Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO);

        int bufferSize = android.media.AudioRecord
                .getMinBufferSize(mSampleRate,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT);
        byte[][] audioData;
        int bufferReadResult;

        audioRecord = new android.media.AudioRecord(MediaRecorder.AudioSource.MIC,
                mSampleRate, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, bufferSize);
        Log.d("AUDIO_SESSIONID", String.valueOf(audioRecord.getAudioSessionId()));

        // divide byte buffersize to 2 to make it short buffer
        audioData = new byte[1000][bufferSize];

        audioRecord.startRecording();

        int i = 0;
        byte[] data;
        while ((bufferReadResult = audioRecord.read(audioData[i], 0, audioData[i].length)) > 0) {

            data = audioData[i];
            byteReceive.doReceive(data);

            Message msg = Message.obtain(audioHandler, AudioHandler.RECORD_AUDIO, data);
            msg.arg1 = bufferReadResult;
            msg.arg2 = (int)(System.currentTimeMillis() - startTime);
            audioHandler.sendMessage(msg);

            double amplitude = 0;
            for (int j = 0; j < audioData[i].length/2; j++) {
                double y = (audioData[i][j*2] | audioData[i][j*2+1] << 8) / 32768.0;
                // depending on your endianness:
                // double y = (audioData[i*2]<<8 | audioData[i*2+1]) / 32768.0
                amplitude += Math.abs(y);
            }
            amplitude = amplitude / 2;

            Log.d("amplitude", String.valueOf(amplitude));

            i++;
            if (i == 1000) {
                i = 0;
            }
            if (stopThread) {
                break;
            }
        }

        Log.d(TAG, "AudioThread Finished, release audioRecord");

    }

    public void stopAudioRecording() {

        if (audioRecord != null && audioRecord.getRecordingState() == android.media.AudioRecord.RECORDSTATE_RECORDING) {
            stopThread = true;
            audioRecord.stop();
            audioRecord.release();
            audioRecord = null;
        }
    }

    public int getAudioSessionID() {
        return audioRecord.getAudioSessionId();
    }

}
