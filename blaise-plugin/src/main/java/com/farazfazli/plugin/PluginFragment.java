package com.farazfazli.plugin;

import android.app.Fragment;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.os.Bundle;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Created by farazfazli on 10/3/16.
 */

public class PluginFragment extends Fragment {
    final String LAYOUT = "layout";
    static String apkPath;
    Resources resources;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        loadResources();
    }

    protected void loadResources() {
        if (apkPath == null) {
            apkPath = getActivity().getFilesDir() + "/plugin.apk";
        }
        try {
            AssetManager assetManager = AssetManager.class.newInstance();
            Method addAssetPath = assetManager.getClass().getMethod("addAssetPath", String.class);
            addAssetPath.invoke(assetManager, apkPath);
            resources = new Resources(assetManager, super.getResources().getDisplayMetrics(), super.getResources().getConfiguration());
            resources.newTheme().setTo(super.getActivity().getTheme());
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

    // We need to pass in the package name because
    // getCallingPackage(), getPackageName(), BuildConfig.APPLICATION_ID, etc.
    // don't work
    private int getLayoutId(String packageName, String layoutId) {
        return resources.getIdentifier(layoutId, LAYOUT, packageName);
    }

    public XmlResourceParser getLayout(String packageName, String layoutId) {
        return resources.getLayout(getLayoutId(packageName, layoutId));
    }
}
