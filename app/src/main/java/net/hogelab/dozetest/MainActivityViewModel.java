package net.hogelab.dozetest;

import android.content.Context;
import android.content.Intent;
import android.databinding.BaseObservable;
import android.databinding.Bindable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import android.os.PowerManager;
import android.provider.Settings;
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

    public void onClickStop() {
        Intent commandIntent = PreventDozeService.newCommandIntent(context, PreventDozeService.COMMAND_STOP);
        context.startService(commandIntent);
    }

    public void onClickBattery() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent intent = new Intent();
            String packageName = context.getPackageName();
            if (powerManager.isIgnoringBatteryOptimizations(packageName))
                intent.setAction(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
            else {
                intent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(Uri.parse("package:" + packageName));
            }

            context.startActivity(intent);
        }
    }

    public void onClickTest() {
        AsyncTask task = new ParentAsyncTask();
        task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
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



    // TEST
    private static class ParentAsyncTask extends AsyncTask<Void, Void, Void> {

        @Override
        public Void doInBackground(Void ... params) {
            return null;
        }
    }

    private static class ChildAsyncTask extends AsyncTask<Void, Void, Void> {

        @Override
        public Void doInBackground(Void ... params) {
            return null;
        }
    }
}
