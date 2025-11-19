package com.pipiqiang.qcamera.app;

import android.app.Application;

import io.sentry.android.core.SentryAndroid;

public class CameraApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        SentryAndroid.init(this, options -> {
            options.setDebug(false);
            options.setEnableAutoSessionTracking(true);
            options.setSessionTrackingIntervalMillis(30000);
        });
        AppLogger.init(getApplicationContext());
        AppLogger.enqueueUpload(getApplicationContext());
    }
}
