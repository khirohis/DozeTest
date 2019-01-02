package net.hogelab.dozetest;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.Messenger;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;

import net.hogelab.dozetest.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = MainActivity.class.getSimpleName();

    private MainActivityViewModel viewModel;
    private ActivityMainBinding binding;

    private PreventDozeServiceConnection mServiceConnection = new PreventDozeServiceConnection();
    private boolean mPlayerServiceBound;
    private Messenger mPlayerServiceMessenger;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate");

        super.onCreate(savedInstanceState);

        viewModel = new MainActivityViewModel(this);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        binding.setViewModel(viewModel);
        setContentView(binding.getRoot());

        bindPreventDozeService();
    }

    @Override
    protected void onStart() {
        Log.d(TAG, "onStart");

        super.onStart();
    }

    @Override
    protected void onStop() {
        Log.d(TAG, "onStop");

        super.onStop();
    }

    @Override
    protected void onDestroy() {
        unbindPreventDozeService();

        super.onDestroy();
    }


    // --------------------------------------------------
    // private functions

    private void bindPreventDozeService() {
        if (!mPlayerServiceBound) {
            bindService(new Intent(MainActivity.this, PreventDozeService.class),
                    mServiceConnection, Context.BIND_AUTO_CREATE);
        }
    }

    private void unbindPreventDozeService() {
        if (mPlayerServiceBound) {
            unbindService(mServiceConnection);
            mPlayerServiceBound = false;
        }
    }


    // --------------------------------------------------

    private class PreventDozeServiceConnection implements ServiceConnection {

        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            Log.d(TAG, "PlayerServiceConnection#onServiceConnected");

            mPlayerServiceMessenger = new Messenger(service);
            mPlayerServiceBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName className) {
            Log.d(TAG, "PlayerServiceConnection#onServiceDisconnected");

            mPlayerServiceMessenger = null;
            mPlayerServiceBound = false;
        }
    }
}
