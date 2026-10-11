/*
 * Copyright (C) 2018 Peter Gregus for GravityBox Project (C3C076@xda)
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ceco.r.gravitybox;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.ResultReceiver;
import android.provider.MediaStore;

public class RecordingService extends Service {
    private static final String TAG = "GB:RecordingService";

    public static final String ACTION_RECORDING_START = "gravitybox.intent.action.RECORDING_START";
    public static final String ACTION_RECORDING_STOP = "gravitybox.intent.action.RECORDING_STOP";
    public static final String ACTION_RECORDING_GET_STATUS = "gravitybox.intent.action.RECORDING_GET_STATUS";
    public static final String ACTION_RECORDING_STATUS_CHANGED = "gravitybox.intent.action.RECORDING_STATUS_CHANGED";
    public static final String EXTRA_RECORDING_STATUS = "recordingStatus";
    public static final String EXTRA_STATUS_MESSAGE = "statusMessage";
    public static final String EXTRA_AUDIO_URI = "audioUri";
    public static final String EXTRA_SAMPLING_RATE = "samplingRate";

    public static final int RECORDING_STATUS_IDLE = 0;
    public static final int RECORDING_STATUS_STARTED = 1;
    public static final int RECORDING_STATUS_STOPPED = 2;
    public static final int RECORDING_STATUS_ERROR = -1;

    public static final int DEFAULT_SAMPLING_RATE = 22050;

    private MediaRecorder mRecorder;
    private int mRecordingStatus = RECORDING_STATUS_IDLE;
    private Notification mRecordingNotif;
    private PendingIntent mPendingIntent;
    private int mSamplingRate = DEFAULT_SAMPLING_RATE;
    private File mLastAudioFile;
    private Uri mLastAudioUri;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        mRecordingStatus = RECORDING_STATUS_IDLE;

        Notification.Builder builder = new Notification.Builder(
                this, GravityBoxApplication.NOTIF_CHANNEL_SERVICES);
        builder.setContentTitle(getString(R.string.quick_settings_qr_recording));
        builder.setContentText(getString(R.string.quick_settings_qr_recording_notif));
        builder.setSmallIcon(R.drawable.ic_qs_qr_recording);
        Bitmap b = BitmapFactory.decodeResource(getResources(), R.drawable.ic_qs_qr_recording);
        builder.setLargeIcon(b);
        Intent intent = new Intent(ACTION_RECORDING_STOP);
        mPendingIntent = PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_IMMUTABLE);
        builder.setContentIntent(mPendingIntent);
        mRecordingNotif = builder.build();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            if (intent.getAction().equals(ACTION_RECORDING_START)) {
                if (intent.hasExtra(EXTRA_SAMPLING_RATE)) {
                    mSamplingRate = intent.getIntExtra(EXTRA_SAMPLING_RATE, DEFAULT_SAMPLING_RATE);
                }
                startRecording();
                return START_STICKY;
            } else if (intent.getAction().equals(ACTION_RECORDING_STOP)) {
                stopRecording();
                return START_STICKY;
            } else if (intent.getAction().equals(ACTION_RECORDING_GET_STATUS)) {
                ResultReceiver receiver = intent.getParcelableExtra("receiver");
                Bundle data = new Bundle();
                data.putInt(EXTRA_RECORDING_STATUS, mRecordingStatus);
                if (mLastAudioUri != null) {
                    data.putString(EXTRA_AUDIO_URI, mLastAudioUri.toString());
                }
                receiver.send(0, data);
                return START_STICKY;
            }
        }

        stopSelf();
        return START_NOT_STICKY;
    }

    MediaRecorder.OnErrorListener mOnErrorListener = (mr, what, extra) -> {
        mRecordingStatus = RECORDING_STATUS_ERROR;

        String statusMessage = "Error in MediaRecorder while recording: " + what + "; " + extra;
        Intent i = new Intent(ACTION_RECORDING_STATUS_CHANGED);
        i.putExtra(EXTRA_RECORDING_STATUS, mRecordingStatus);
        i.putExtra(EXTRA_STATUS_MESSAGE, statusMessage);
        sendBroadcast(i);
        stopForeground(true);
    };

    private void startRecording() {
        String statusMessage = "";
        mLastAudioFile = new File(Utils.getCacheDir(this) + "/AUDIO_" + new SimpleDateFormat(
                "yyyyMMdd_HHmmss", Locale.US).format(new Date()));

        try {
            mRecorder = new MediaRecorder();
            mRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            mRecorder.setOutputFile(mLastAudioFile);
            mRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            mRecorder.setAudioEncodingBitRate(96000);
            mRecorder.setAudioSamplingRate(mSamplingRate);
            mRecorder.setOnErrorListener(mOnErrorListener);
            mRecorder.prepare();
            mRecorder.start();
            mRecordingStatus = RECORDING_STATUS_STARTED;
            startForeground(1, mRecordingNotif);
        } catch (Exception e) {
            e.printStackTrace();
            mRecordingStatus = RECORDING_STATUS_ERROR;
            statusMessage = e.getMessage();
        } finally {
            Intent i = new Intent(ACTION_RECORDING_STATUS_CHANGED);
            i.putExtra(EXTRA_RECORDING_STATUS, mRecordingStatus);
            i.putExtra(EXTRA_STATUS_MESSAGE, statusMessage);
            sendBroadcast(i);
        }
    }

    private void stopRecording() {
        if (mRecorder == null) return;

        String statusMessage = "";
        Intent i = new Intent(ACTION_RECORDING_STATUS_CHANGED);
        try {
            mRecorder.stop();
            mRecorder.release();
            mRecorder = null;
            mRecordingStatus = RECORDING_STATUS_STOPPED;
            mLastAudioUri = insertIntoMediaStore();
            i.putExtra(EXTRA_AUDIO_URI, mLastAudioUri.toString());
        } catch (Exception e) {
            e.printStackTrace();
            mRecordingStatus = RECORDING_STATUS_ERROR;
            statusMessage = e.getMessage();
        } finally {
            i.putExtra(EXTRA_RECORDING_STATUS, mRecordingStatus);
            i.putExtra(EXTRA_STATUS_MESSAGE, statusMessage);
            sendBroadcast(i);
            stopForeground(true);
        }
    }

    private Uri insertIntoMediaStore() throws Exception {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Audio.Media.DISPLAY_NAME, mLastAudioFile.getName());
        values.put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4");
        values.put(MediaStore.Audio.Media.IS_PENDING, 1);
        values.put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/AudioRecordings");

        ContentResolver resolver = getContentResolver();
        Uri collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
        Uri item = resolver.insert(collection, values);

        ParcelFileDescriptor pfd = resolver.openFileDescriptor(item, "w", null);
        InputStream is = new FileInputStream(mLastAudioFile);
        OutputStream os = new FileOutputStream(pfd.getFileDescriptor());
        byte[] buffer = new byte[4096];
        int length;
        while((length = is.read(buffer)) > 0) {
            os.write(buffer, 0, length);
        }
        os.flush();
        is.close();
        os.close();

        values.clear();
        values.put(MediaStore.Audio.Media.IS_PENDING, 0);
        resolver.update(item, values, null, null);

        return item;
    }

    @Override
    public void onDestroy() {
        stopRecording();
        super.onDestroy();
    }
}