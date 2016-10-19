package com.farazfazli.blaise;

import android.app.Activity;
import android.app.Fragment;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Base64;
import android.util.Log;

import com.farazfazli.silo_upstream.UpstreamService;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;

import javax.net.ssl.HttpsURLConnection;

import dalvik.system.DexClassLoader;

public class BlaiseService extends Service {
    final static String TAG = "[" + BlaiseService.class.getSimpleName() + "]";
    public final static String MODULE_NAME = "BLAISE";
    final String NAME = "name";
    final String APK = "apk";
    static String packageName;
    static boolean silo;
    String packageHash;
    HttpsURLConnection urlConnection;
    SharedPreferences sharedPreferences;
    static DexClassLoader dexClassLoader;
    static HashMap<String, Fragment> fragments;
    private String apkToLoad = "plugin.apk";
    static boolean receivedUpdate = false;
    private static String host = "https://silo.live/query";

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (receivedUpdate) {
            Log.i(TAG, "Received update from Silo");
            new Thread() {
                public void run() {
                    checkForUpdates();
                }
            }.start();
        } else {
            Log.i(TAG, "Started from Activity");
        }
        return START_STICKY;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        packageName = sharedPreferences.getString(NAME, "");
        if (noApkExists()) {
            new Thread() {
                public void run() {
                    checkForUpdates();
                }
            }.start();
        } else {
            prepareApp();
        }
    }

    // TODO: Reuse DexClassLoader if needed, otherwise update
    private void prepareApp() {
        final File optimizedDexOutputPath = getDir("outdex", 0);
        Log.i(TAG, "Loading " + packageName + " with DexClassLoader from " + apkToLoad);
        dexClassLoader = new DexClassLoader(getFilesDir() + "/" + apkToLoad, optimizedDexOutputPath.getAbsolutePath(),
                null, ClassLoader.getSystemClassLoader().getParent());
        Intent update = new Intent(Constants.UPDATE_BROADCAST);
        // sendBroadcastSync blocks until code in Activity is executed
        LocalBroadcastManager.getInstance(this).sendBroadcastSync(update);
        Log.i(TAG, "Sent update broadcast");
    }

    // Given fragments as strings, return fragments from APK
    public static HashMap<String, Fragment> getFragments(String[] fragmentsToLoad) {
        if (receivedUpdate == false && fragments != null) {
            return fragments; // If update hasn't been received and fragments are present
        }
        fragments = new HashMap<>(fragmentsToLoad.length);
        for (String fragmentName : fragmentsToLoad) {
            fragments.put(fragmentName, getFragment(fragmentName));
            Log.i(TAG, "Loaded " + fragmentName + " from " + packageName);
        }
        return fragments;
    }

    public static Fragment getFragment(String fragmentToLoad) {
        if (dexClassLoader == null) {
            Log.w(TAG, "dexClassLoader is null");
            return null;
        }
        try {
            Class<?> loadedClass = dexClassLoader.loadClass(packageName + "." + fragmentToLoad);
            return (Fragment) loadedClass.newInstance();
        } catch (InstantiationException e) {
            Log.e(TAG, e.getMessage());
        } catch (IllegalAccessException e) {
            Log.e(TAG, e.getMessage());
        } catch (ClassNotFoundException e) {
            Log.e(TAG, "Class not found - " + e.getMessage());
        }
        return null;
    }

    // To be called initially from Activity
    public static Intent getIntent(Activity activity, boolean usingSilo) {
        silo = usingSilo;
        return new Intent(activity, BlaiseService.class);
    }

    // To be called initially from Activity for self-hosted instances
    public static Intent getIntent(Activity activity, boolean usingSilo, String url) {
        silo = usingSilo;
        host = url;
        return new Intent(activity, BlaiseService.class);
    }

    // To be called on called from downstream message
    public static Intent getIntent(Service service) {
        receivedUpdate = true;
        return new Intent(service, BlaiseService.class);
    }

    // To be called on called from downstream message for
    // self-hosted instances
    public static Intent getIntent(Service service, String url) {
        receivedUpdate = true;
        host = url;
        return new Intent(service, BlaiseService.class);
    }

    private boolean noApkExists() {
        return sharedPreferences.getString(APK, "").equals("") || packageName.equals("");
    }

    private void checkForUpdates() {
        Log.i(TAG, "Checking for updates");
        if (isOnline()) {
            setPackageHash();
            String availableApk = getLatestVersion();
            if (isNewer(availableApk)) {
                downloadNewerApk(availableApk);
                sendUpstream("Updated to version: " + availableApk);
            } else {
                sendUpstream("Client on latest version");
                Log.i(TAG, "Update not available");
                prepareApp();
            }
        }
    }

    private String getLatestVersion() {
        try {
            URL url = new URL(host);
            urlConnection = (HttpsURLConnection) url.openConnection();
            urlConnection.setDoInput(true);
            urlConnection.setDoOutput(true);
            urlConnection.setUseCaches(false);
            urlConnection.setRequestMethod("POST");
            urlConnection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded;");
            packageHash = "hash=" + URLEncoder.encode(packageHash, "UTF-8");
            urlConnection.setFixedLengthStreamingMode(packageHash.length());
            OutputStream out = urlConnection.getOutputStream();
            // TODO: diagnose what is sending new lines here
            out.write((packageHash).getBytes());
            out.close();
            if (url.getHost().equals(urlConnection.getURL().getHost()) == false) {
                    Log.i(TAG, "Url different from ours, return");
                    return "";
                }
            if (urlConnection.getResponseCode() == HttpsURLConnection.HTTP_OK) {
                Log.i(TAG, "200 - Got Response");
                BufferedReader in = new BufferedReader(new InputStreamReader(urlConnection.getInputStream()));
                StringBuilder response = new StringBuilder();
                int line;
                while((line = urlConnection.getInputStream().read()) != -1) {
                    response.append((char)line);
                }
                in.close();
                Log.i(TAG, response.toString());
                JSONObject apkInfo = new JSONObject(response.toString());
                urlConnection.getInputStream().close();
                packageName = apkInfo.getString(Constants.PACKAGE_NAME); // External package name
                return apkInfo.getString(Constants.DOWNLOAD_LINK); // Plugin download link
            } else {
                return "";
            }
        } catch (IOException e) {
            Log.e(TAG, "IO error " + e.getMessage());
            e.printStackTrace();
        } catch (JSONException e) {
            Log.e(TAG, "JSON error " + e.getMessage());
        } finally {
            urlConnection.disconnect();
        }
        return "";
    }

    private void downloadNewerApk(String downloadUrl) {
        Log.i(TAG, "Downloading: " + downloadUrl);
        try {
            URL url = new URL(downloadUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setDoInput(true);
            conn.connect();
            InputStream is = conn.getInputStream();
            FileOutputStream fos = getApplication().openFileOutput(apkToLoad, Context.MODE_PRIVATE);
            // TODO: Check this buffer code
            byte[] buffer = new byte[1024];
            int len1;
            while ((len1 = is.read(buffer)) != -1) {
                fos.write(buffer, 0, len1);
            }
            fos.close();
            is.close();
            sharedPreferences.edit().putString(NAME, packageName).apply();
            sharedPreferences.edit().putString(APK, downloadUrl).apply();
            Log.i(TAG, "New app downloaded, preparing...");
            prepareApp();
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }

    private void sendUpstream(String message) {
        if (!silo) {
            return;
        }
        UpstreamService.addMessageToQueue(message);
    }

    // http://stackoverflow.com/a/27312494
    public boolean isOnline() {
        Runtime runtime = Runtime.getRuntime();
        try {
            Process ipProcess = runtime.exec("/system/bin/ping -c 1 8.8.8.8");
            return (ipProcess.waitFor() == 0); // Check exit value
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
        return false;
    }

    private boolean isNewer(String newUrl) {
        return newUrl.isEmpty() == false && sharedPreferences.getString(APK, "").equals(newUrl) == false;
    }

    private void setPackageHash() {
        try {
            PackageInfo info = this.getApplicationContext().getPackageManager().getPackageInfo(getApplication().getPackageName(), PackageManager.GET_SIGNATURES);
            for (Signature signature : info.signatures) {
                MessageDigest md = MessageDigest.getInstance("SHA");
                md.update(signature.toByteArray());
                packageHash = Base64.encodeToString(md.digest(), Base64.DEFAULT);
            }
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
    }
}
