package net.hogelab.dozetest;

import android.annotation.TargetApi;
import android.app.Application;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;

public class DozeTestApplication extends Application {
    public static final String NOTIFICATION_CHANNEL_ID = "CHANNEL1";

    @Override
    public void onCreate() {
        super.onCreate();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            registerChannels();
        }
    }


    @TargetApi(Build.VERSION_CODES.O)
    private void registerChannels() {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            NotificationChannel channel = new NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    getString(R.string.app_name),
                    NotificationManager.IMPORTANCE_DEFAULT
            );

            channel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);

            manager.createNotificationChannel(channel);
        }
    }
}
