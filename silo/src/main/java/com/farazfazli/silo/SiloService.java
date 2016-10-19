package com.farazfazli.silo;

import android.app.Activity;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.util.Base64;
import android.util.Log;

import com.farazfazli.silo_downstream.DownstreamService;
import com.farazfazli.silo_upstream.BufferedSocketWriter;
import com.farazfazli.silo_upstream.UpstreamService;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

/**
 * Created by farazfazli on 9/29/16.
 */

public class SiloService extends Service {
    // 10.0.3.2 -> localhost for Genymotion
    // 10.0.2.2 -> localhost for AVD
    private static final String TAG = SiloService.class.getSimpleName();
    private static final String DEFAULT_HOST = "silo.live";
    private static String host = "silo.live";
    private int port = 1337;
    private static String[] modules;
    private static String packageName;
    private String deviceID;
    private String hash;
    static BufferedReader downstream;
    static BufferedSocketWriter upstream;
    static SharedPreferences sharedPreferences;

    public static Intent getIntent(Activity activity, String[] externalModules) {
        modules = externalModules;
        packageName = activity.getPackageName();
        return new Intent(activity, SiloService.class);
    }

    public static Intent getIntent(Activity activity, String[] externalModules, String url) {
        host = url;
        modules = externalModules;
        packageName = activity.getPackageName();
        return new Intent(activity, SiloService.class);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        Log.i(TAG, "Started");
        if (modules != null && packageName != null) {
            sharedPreferences.edit().putString(Constants.PACKAGE_NAME, packageName).apply();
            StringBuilder moduleBuilder = new StringBuilder();
            for (String module : modules) {
                moduleBuilder.append(module).append(",");
            }
            sharedPreferences.edit().putString(Constants.MODULES, moduleBuilder.toString()).apply();
        }
        new Thread() {
            @Override
            public void run() {
                initSocketConnection();
            }
        }.start();
    }

    private void initSocketConnection() {
        Log.i(TAG, "Initializing socket connection");
        if (sharedPreferences == null) {
            sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        }
        if (modules == null || packageName == null) {
            packageName = getName();
            modules = getModules();
        }
        deviceID = getDeviceID();
        hash = getPackageHash();
        try {
            Log.i(TAG, "Connecting to socket - " + host + ":" + port);
            Socket socket = new Socket();
            socket.connect(new InetSocketAddress(host, port), 5000);
            Log.i(TAG, "Connected to socket - " + host + ":" + port);
            try {
                downstream = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                upstream = new BufferedSocketWriter(new OutputStreamWriter(socket.getOutputStream()));
                Intent downstreamService = DownstreamService.getIntent(this, downstream, modules);
                if (host.equals(DEFAULT_HOST) == false) {
                    downstreamService = DownstreamService.getIntent(this, downstream, modules, host);
                }
                // TODO: client side geolocation via https://github.com/fiorix/freegeoip
                // TODO: display metrics http://stackoverflow.com/a/24701063
                // TODO: phone type http://stackoverflow.com/a/11041710
                // TODO: country http://stackoverflow.com/a/18735232 http://stackoverflow.com/a/11872569
                // TODO: language, country http://stackoverflow.com/a/23168383
                Intent upstreamService = UpstreamService.getIntent(this, upstream, hash, deviceID, packageName);
                startService(downstreamService);
                startService(upstreamService);
                UpstreamService.registerDevice();
            } catch (IOException e) {
                Log.e(TAG, e.getMessage());
                stopSelf();
                e.printStackTrace();
            }
        } catch (SocketTimeoutException socketTimeoutException) {
            Log.e(TAG, "SocketTimeoutException while connecting");
            Log.e(TAG, socketTimeoutException.getMessage());
            stopSelf();
        } catch (SocketException socketException) {
            Log.e(TAG, "SocketException while connecting");
            Log.e(TAG, socketException.getMessage());
            stopSelf();
        } catch (IOException e) {
            Log.e(TAG, "IOException while connecting");
            Log.e(TAG, e.getMessage());
            stopSelf();
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private String getPackageHash() {
        try {
            PackageInfo info = this.getApplicationContext().getPackageManager().getPackageInfo(packageName, PackageManager.GET_SIGNATURES);
            for (Signature signature : info.signatures) {
                MessageDigest md = MessageDigest.getInstance("SHA");
                md.update(signature.toByteArray());
                return Base64.encodeToString(md.digest(), Base64.DEFAULT);
            }
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        return "";
    }

    private String getDeviceID() {
        String savedDeviceID = sharedPreferences.getString(Constants.DEVICE_ID, "");
        if (savedDeviceID.equals("") == false) {
            return savedDeviceID;
        }
        savedDeviceID = Settings.Secure.getString(this.getApplicationContext().getContentResolver(),
                Settings.Secure.ANDROID_ID);
        if (savedDeviceID == null) {
            savedDeviceID = "NO-ID" + UUID.randomUUID().toString();
        }
        sharedPreferences.edit().putString(Constants.DEVICE_ID, deviceID).apply();
        return savedDeviceID;
    }

    private String getName() {
        return sharedPreferences.getString(Constants.PACKAGE_NAME, "");
    }

    private String[] getModules() {
        return sharedPreferences.getString(Constants.MODULES, ",").split(",");
    }

    public static void addMessageToQueue(String message) {
        UpstreamService.addMessageToQueue(message);
    }
}
