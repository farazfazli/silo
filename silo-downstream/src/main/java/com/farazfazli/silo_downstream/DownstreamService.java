package com.farazfazli.silo_downstream;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import com.farazfazli.blaise.BlaiseService;

import java.io.BufferedReader;
import java.io.IOException;

/**
 * Created by farazfazli on 9/29/16.
 */

public class DownstreamService extends Service {
    static final String TAG = "[" + DownstreamService.class.getSimpleName() + "]";
    private static BufferedReader bufferedReader;
    private static String[] modules;
    private static String host = "";

    public static Intent getIntent(Service mService, BufferedReader externalBufferedReader, String[] externalModules) {
        bufferedReader = externalBufferedReader;
        modules = externalModules;
        return new Intent(mService, DownstreamService.class);
    }

    public static Intent getIntent(Service mService, BufferedReader externalBufferedReader, String[] externalModules, String host) {
        bufferedReader = externalBufferedReader;
        modules = externalModules;
        return new Intent(mService, DownstreamService.class);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "Started");
        if (bufferedReader == null) {
            Log.e(TAG, "Stopping self");
            stopSelf();
            return;
        }
        new Thread() {
            @Override
            public void run() {
                receiveMessages();
            }
        }.start();
    }

    private void receiveMessages() {
        try {
            String message;
            while ((message = bufferedReader.readLine()) != null) {
                Log.i(TAG, message);
                handleModules(message);
            }
            throw new IOException("Received -1, socket closed");
        } catch (IOException e) {
            Log.e(TAG, e.getMessage());
            e.printStackTrace();
            stopSelf();
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void handleModules(String message) {
        for (String module : modules) {
            if (message.contains(module)) {
                switch (unwrap(message)) {
                    case BlaiseService.MODULE_NAME:
                        Log.i(TAG, "Launching update");
                        Intent blaise = BlaiseService.getIntent(DownstreamService.this);
                        if (host.equals("") == false) {
                            blaise = BlaiseService.getIntent(DownstreamService.this, host);
                        }
                        startService(blaise);
                        break;
                }
            } else {
                // Message is not delegated to a module
                Log.i(TAG, message);
            }
        }
    }

    private String unwrap(String module) {
        return module.substring(3, module.length() - 3);
    }
}
