package net.hogelab.dozetest;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Messenger;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.ResultReceiver;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.RatingCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

public class PreventDozeService extends Service {
    private static final String TAG = PreventDozeService.class.getSimpleName();

    public static final String COMMAND = "command";
    public static final int COMMAND_NOP = 0;
    public static final int COMMAND_START = 1;
    public static final int COMMAND_STOP = 2;

//    public static final String ACTION_START = "net.hogelab.dozetest.ACTION_START";
//    public static final String ACTION_STOP = "net.hogelab.dozetest.ACTION_STOP";

    private PowerManager mPowerManager;
    private WifiManager.WifiLock mWiFiLock;

    private Handler mHandler = new Handler();
    private Messenger mMessenger = new Messenger(mHandler);
    private Runnable mPeriodicTask;

    private MediaSessionCompat mMediaSession;

    private MediaSessionCompat.Token mSessionToken;
    private MediaControllerCompat mController;
    private MediaControllerCompat.TransportControls mTransportControls;
    private MediaControllerCallback mMediaControllerCallback = new MediaControllerCallback();

    private NotificationManagerCompat mNotificationManager;
//    private PendingIntent mStartIntent;
//    private PendingIntent mStopIntent;
    private MediaMetadataCompat mMetadata;

    private DeviceIdleModeIntentReceiver mDeviceIdleModeIntentReceiver;


    public static Intent newCommandIntent(Context context, int command) {
        Intent intent = new Intent(context, PreventDozeService.class);
        Bundle extras = new Bundle();
        extras.putInt(COMMAND, command);
        intent.putExtras(extras);

        return intent;
    }


    @Override
    public void onCreate() {
        Log.d(TAG, "onCreate");

        super.onCreate();

        mPowerManager = (PowerManager) getApplicationContext().getSystemService(Context.POWER_SERVICE);
        WifiManager wiFiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        mWiFiLock = wiFiManager.createWifiLock(getPackageName());

        mPeriodicTask = new Runnable() {
            @Override
            public void run() {
                onPeriodicTask();
            }
        };

        mMediaSession = new MediaSessionCompat(this, "PlayerService");
        mMediaSession.setCallback(new MediaSessionCallback());
        mMediaSession.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS |
                MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);

        try {
            mSessionToken = mMediaSession.getSessionToken();
            mController = new MediaControllerCompat(this, mSessionToken);
            mTransportControls = mController.getTransportControls();
        } catch (RemoteException e) {
        }

        mNotificationManager = NotificationManagerCompat.from(this);
//        mStartIntent = PendingIntent.getBroadcast(this, 1000,
//                new Intent(ACTION_START).setPackage(getPackageName()), PendingIntent.FLAG_CANCEL_CURRENT);
//        mStopIntent = PendingIntent.getBroadcast(this, 1000,
//                new Intent(ACTION_STOP).setPackage(getPackageName()), PendingIntent.FLAG_CANCEL_CURRENT);

        MediaMetadataCompat.Builder builder = new MediaMetadataCompat.Builder();
        builder.putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, "mediaId")
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, "title")
                .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, "title")
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, 300)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "artist")
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, "album");
        mMetadata = builder.build();
    }

    @Override
    public int onStartCommand(Intent startIntent, int flags, int startId) {
        Log.d(TAG, "onStartCommand");

        if (startIntent != null) {
            Bundle extras = startIntent.getExtras();
            if (extras != null) {
                int command = extras.getInt(COMMAND, COMMAND_NOP);
                switch (command) {
                    case COMMAND_START:
                        startPrevent();
                        break;

                    case COMMAND_STOP:
                        stopPrevent();
                        break;
                }
            }

        }

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy");

        mMediaSession.release();
    }


    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "onBind");

        return mMessenger.getBinder();
    }

    @Override
    public void onRebind(Intent intent) {
        Log.d(TAG, "onRebind");
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.d(TAG, "onUnbind");
        return false;
    }


    // --------------------------------------------------
    // private functions

    private void startPrevent() {
        Log.d(TAG, "startPrevent");

        if (!mWiFiLock.isHeld()) {
            mWiFiLock.acquire();
        }

        startReceive();
        startNotification();

        mHandler.removeCallbacks(mPeriodicTask);
        mHandler.postDelayed(mPeriodicTask, 5000);
    }

    private void stopPrevent() {
        Log.d(TAG, "stopPrevent");

        if (!mWiFiLock.isHeld()) {
            mWiFiLock.release();
        }

        stopReceive();
        stopNotification();

        mHandler.removeCallbacks(mPeriodicTask);
    }


    private void onPeriodicTask() {
//        Log.d(TAG, "onPeriodicTask");

        Thread pingTask = new Thread(new Runnable() {
            @Override
            public void run() {
                ping();
            }
        });
        pingTask.start();

        mHandler.postDelayed(mPeriodicTask, 5000);
    }


    private void startReceive() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (mDeviceIdleModeIntentReceiver == null) {
                mDeviceIdleModeIntentReceiver = new DeviceIdleModeIntentReceiver();
                IntentFilter intentFilter = new IntentFilter();
                intentFilter.addAction(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED);
                registerReceiver(mDeviceIdleModeIntentReceiver, intentFilter);
            }
        }
    }

    private void stopReceive() {
        if (mDeviceIdleModeIntentReceiver != null) {
            unregisterReceiver(mDeviceIdleModeIntentReceiver);
            mDeviceIdleModeIntentReceiver = null;
        }
    }


    private void startNotification() {
        mController.registerCallback(mMediaControllerCallback);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, "channelId");
        builder.setWhen(System.currentTimeMillis())
                .setShowWhen(true)
                .setUsesChronometer(true);
        android.support.v4.media.app.NotificationCompat.MediaStyle mediaStyle = new android.support.v4.media.app.NotificationCompat.MediaStyle();
        mediaStyle.setMediaSession(mSessionToken);
//                .setShowActionsInCompactView(0, 1);
        builder.setStyle(mediaStyle);

        MediaDescriptionCompat description = mMetadata.getDescription();
        builder.setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setContentTitle(description.getTitle())
                .setContentText(description.getSubtitle());

        Notification notification = builder.build();
        startForeground(1000, notification);
    }

    private void stopNotification() {
        mNotificationManager.cancel(1000);
        mController.unregisterCallback(mMediaControllerCallback);
    }


    private void ping() {
        try {
            URL pingUrl = new URL("http://www.google.com/");
            HttpURLConnection connection = (HttpURLConnection) pingUrl.openConnection();
            connection.setRequestMethod("HEAD");
            connection.connect();
            int statusCode = connection.getResponseCode();
            Log.d(TAG, "ping: statusCode=" + statusCode);
        } catch (MalformedURLException e) {
            Log.d(TAG, "ping: MalformedURLException");
        } catch (IOException e) {
//            Log.d(TAG, "ping: IOException");
        }
    }


    // --------------------------------------------------

    public class DeviceIdleModeIntentReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED)) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    Log.d(TAG, "DeviceIdleModeIntentReceiver#onReceive: isDeviceIdleMode=" + mPowerManager.isDeviceIdleMode());
                }
            }
        }
    }


    // --------------------------------------------------

    private class MediaSessionCallback extends MediaSessionCompat.Callback {

        @Override
        public void onCommand(String command, Bundle extras, ResultReceiver cb) {
            Log.d(TAG, "MediaSessionCallback#onCommand");
        }

        @Override
        public boolean onMediaButtonEvent(Intent mediaButtonEvent) {
            Log.d(TAG, "MediaSessionCallback#onMediaButtonEvent");
            boolean handled = false;

            return handled;
        }


        @Override
        public void onPrepare() {
            Log.d(TAG, "MediaSessionCallback#onPrepare");
        }

        @Override
        public void onPrepareFromMediaId(String mediaId, Bundle extras) {
            Log.d(TAG, "MediaSessionCallback#onPrepareFromMediaId");
        }

        @Override
        public void onPrepareFromSearch(String query, Bundle extras) {
            Log.d(TAG, "MediaSessionCallback#onPrepareFromSearch");
        }

        @Override
        public void onPrepareFromUri(Uri uri, Bundle extras) {
            Log.d(TAG, "MediaSessionCallback#onPrepareFromUri");
        }


        @Override
        public void onPlay() {
            Log.d(TAG, "MediaSessionCallback#onPlay");
        }

        @Override
        public void onPlayFromMediaId(String mediaId, Bundle extras) {
            Log.d(TAG, "MediaSessionCallback#onPlayFromMediaId");
        }

        @Override
        public void onPlayFromSearch(final String query, final Bundle extras) {
            Log.d(TAG, "MediaSessionCallback#onPlayFromSearch");
        }

        @Override
        public void onPlayFromUri(Uri uri, Bundle extras) {
            Log.d(TAG, "MediaSessionCallback#onPlayFromUri");
        }


        @Override
        public void onSkipToQueueItem(long queueId) {
            Log.d(TAG, "MediaSessionCallback#onSkipToQueueItem");
        }


        @Override
        public void onPause() {
            Log.d(TAG, "MediaSessionCallback#onPause");
        }

        @Override
        public void onSkipToNext() {
            Log.d(TAG, "MediaSessionCallback#onSkipToNext");
        }

        @Override
        public void onSkipToPrevious() {
            Log.d(TAG, "MediaSessionCallback#onSkipToPrevious");
        }

        @Override
        public void onFastForward() {
            Log.d(TAG, "MediaSessionCallback#onFastForward");
        }

        @Override
        public void onRewind() {
            Log.d(TAG, "MediaSessionCallback#onRewind");
        }

        @Override
        public void onStop() {
            Log.d(TAG, "MediaSessionCallback#onStop");
        }

        @Override
        public void onSeekTo(long position) {
            Log.d(TAG, "MediaSessionCallback#onSeekTo");
        }


        @Override
        public void onSetRating(RatingCompat rating) {
            Log.d(TAG, "MediaSessionCallback#onSetRating");
        }

        @Override
        public void onSetRepeatMode(@PlaybackStateCompat.RepeatMode int repeatMode) {
            Log.d(TAG, "MediaSessionCallback#onSetRepeatMode");
        }

        @Override
        public void onSetShuffleModeEnabled(boolean enabled) {
            Log.d(TAG, "MediaSessionCallback#onSetShuffleModeEnabled");
        }


        @Override
        public void onCustomAction(@NonNull String action, Bundle extras) {
            Log.d(TAG, "MediaSessionCallback#onCustomAction");
        }


        @Override
        public void onAddQueueItem(MediaDescriptionCompat description) {
            Log.d(TAG, "MediaSessionCallback#onAddQueueItem");
        }

        @Override
        public void onAddQueueItem(MediaDescriptionCompat description, int index) {
            Log.d(TAG, "MediaSessionCallback#onAddQueueItem");
        }

        @Override
        public void onRemoveQueueItem(MediaDescriptionCompat description) {
            Log.d(TAG, "MediaSessionCallback#onRemoveQueueItem");
        }
    }


    // --------------------------------------------------

    private class MediaControllerCallback extends MediaControllerCompat.Callback {

        @Override
        public void onPlaybackStateChanged(@NonNull PlaybackStateCompat state) {
            Log.d(TAG, "MediaControllerCallback#onPlaybackStateChanged");
        }

        @Override
        public void onMetadataChanged(MediaMetadataCompat metadata) {
            Log.d(TAG, "MediaControllerCallback#onMetadataChanged");

            mMetadata = metadata;
        }

        @Override
        public void onSessionDestroyed() {
            Log.d(TAG, "MediaControllerCallback#onSessionDestroyed");

            super.onSessionDestroyed();
        }
    }
}
