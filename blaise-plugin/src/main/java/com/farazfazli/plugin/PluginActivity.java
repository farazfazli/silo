package com.farazfazli.plugin;

import android.app.Activity;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.os.Bundle;
import android.os.PersistableBundle;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Created by farazfazli on 10/7/16.
 */

public class PluginActivity extends Activity {
    // TODO: Test PluginActivity
    final String LAYOUT = "layout";
    static String apkPath;
    Resources resources;

    @Override
    public void onCreate(Bundle savedInstanceState, PersistableBundle persistentState) {
        super.onCreate(savedInstanceState, persistentState);
        loadResources();
    }

    protected void loadResources() {
        if (apkPath == null) {
            apkPath = getFilesDir() + "/plugin.apk";
        }
        try {
            AssetManager assetManager = AssetManager.class.newInstance();
            Method addAssetPath = assetManager.getClass().getMethod("addAssetPath", String.class);
            addAssetPath.invoke(assetManager, apkPath);
            resources = new Resources(assetManager, super.getResources().getDisplayMetrics(), super.getResources().getConfiguration());
            resources.newTheme().setTo(super.getTheme());
        } catch (java.lang.InstantiationException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
    }

    private int getLayoutId(String packageName, String layoutId) {
        return resources.getIdentifier(layoutId, LAYOUT, packageName);
    }

    public XmlResourceParser getLayout(String packageName, String layoutId) {
        return resources.getLayout(getLayoutId(packageName, layoutId));
    }
}
