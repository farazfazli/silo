package com.farazfazli.silo_upstream;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.LinkedList;

/**
 * Created by farazfazli on 9/27/16.
 */

public class UpstreamService extends Service {
    static final String TAG = "[" + UpstreamService.class.getSimpleName() + "]";
    private static BufferedSocketWriter mBufferedSocketWriter;
    static String mPackageHash;
    static String mDeviceID;
    static String mPackageName;
    static LinkedList<String> messageQueue = new LinkedList<>();

    public static Intent getIntent(Service service, BufferedSocketWriter bufferedSocketWriter, String packageHash, String deviceID, String packageName) {
        mBufferedSocketWriter = bufferedSocketWriter;
        mPackageHash = packageHash;
        mDeviceID = deviceID;
        mPackageName = packageName;
        return new Intent(service, UpstreamService.class);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "Started");
        if (mBufferedSocketWriter == null) {
            Log.e(TAG, "Stopping self");
            stopSelf();
            return;
        }
        new Thread() {
            @Override
            public void run() {
                sendMessages();
            }
        }.start();
    }

    private static void sendMessage(String message) {
        if (message == null || message.length() == 0) {
            return;
        }

        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("PackageName", mPackageName);
            jsonObject.put("PackageHash", mPackageHash);
            jsonObject.put("DeviceID", mDeviceID);
            jsonObject.put("Message", message);
            Log.i(TAG, "Sending: " + jsonObject.toString());
            mBufferedSocketWriter.sendJson(jsonObject);
        } catch (JSONException e) {
            Log.e(TAG, "Error creating JSONObject");
            e.printStackTrace();
        } catch (IOException e) {
            Log.e(TAG, "Error writing to Socket");
            e.printStackTrace();
        }
    }

    public static void addMessageToQueue(String message) {
        Log.i(TAG, "Adding - " + messageQueue.toString() + " - to queue");
        messageQueue.add(message);
        if (mBufferedSocketWriter != null) {
            sendMessages();
        }
    }

    public static void sendMessages() {
        if (messageQueue.isEmpty()) {
            return;
        }
        Log.i(TAG, "Sending messages from queue -> " + messageQueue.toString());
        for (String msg : messageQueue) {
            sendMessage(msg);
            messageQueue.remove(msg);
            Log.i(TAG, "Sent and removed -> " + msg);
        }
    }

    public static void registerDevice() {
        messageQueue.offerFirst("Registering device");
        sendMessages();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
