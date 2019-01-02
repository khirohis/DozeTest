package net.hogelab.dozetest;

import android.content.Context;
import android.content.Intent;
import android.databinding.BaseObservable;
import android.databinding.Bindable;
import android.os.Build;
import android.os.Handler;
import android.os.PowerManager;
import android.util.Log;

public class MainActivityViewModel extends BaseObservable {
    private static final String TAG = MainActivityViewModel.class.getSimpleName();

    private final Context context;

    private PowerManager powerManager;
    private boolean deviceIdleMode;

    private Handler handler = new Handler();
    private Runnable periodicTask;

    private String logText;


    MainActivityViewModel(Context context) {
        this.context = context;

        powerManager = (PowerManager) context.getApplicationContext().getSystemService(Context.POWER_SERVICE);

        periodicTask = new Runnable() {

            @Override
            public void run() {
                onPeriodicTask();
            }
        };

        handler.postDelayed(periodicTask, 1000);
    }


    @Bindable
    public String getLogText() {
        return logText;
    }

    public void setLogText(String logText) {
        this.logText = logText;
        notifyPropertyChanged(BR.logText);
    }


    public void onClickStart() {
        Intent commandIntent = PreventDozeService.newCommandIntent(context, PreventDozeService.COMMAND_START);
        context.startService(commandIntent);
    }


    private void onPeriodicTask() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            boolean deviceIdleMode = powerManager.isDeviceIdleMode();
            if (deviceIdleMode != this.deviceIdleMode) {
                Log.d(TAG, "onPeriodicTask: devideIdleMode changed: " + deviceIdleMode);
                this.deviceIdleMode = deviceIdleMode;
            }
        }

        handler.postDelayed(periodicTask, 1000);
    }
}
