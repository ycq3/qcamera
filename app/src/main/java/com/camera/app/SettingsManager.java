package com.camera.app;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

public class SettingsManager {
    
    private static final String PREF_CAMERA_RESOLUTION = "camera_resolution";
    private static final String PREF_CAMERA_QUALITY = "camera_quality";
    private static final String PREF_CAPTURE_INTERVAL = "capture_interval";
    private static final String PREF_STOP_CONDITION = "stop_condition";
    private static final String PREF_STOP_TIME = "stop_time";
    private static final String PREF_STOP_COUNT = "stop_count";
    private static final String PREF_AUTO_CLEAN = "auto_clean";
    private static final String PREF_MIN_SPACE = "min_space";
    private static final String PREF_SEND_EMAIL = "send_email";
    private static final String PREF_EMAIL_ADDRESS = "email_address";
    
    // 默认值
    private static final String DEFAULT_RESOLUTION = "1920x1080";
    private static final String DEFAULT_QUALITY = "75";
    private static final String DEFAULT_INTERVAL = "30";
    private static final String DEFAULT_STOP_CONDITION = "never";
    private static final String DEFAULT_STOP_COUNT = "100";
    private static final boolean DEFAULT_AUTO_CLEAN = true;
    private static final String DEFAULT_MIN_SPACE = "100";
    private static final boolean DEFAULT_SEND_EMAIL = false;
    private static final String DEFAULT_EMAIL = "";
    
    private SharedPreferences sharedPreferences;
    
    public SettingsManager(Context context) {
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
    }
    
    // 相机设置
    public String getCameraResolution() {
        return sharedPreferences.getString(PREF_CAMERA_RESOLUTION, DEFAULT_RESOLUTION);
    }
    
    public int getCameraQuality() {
        try {
            return Integer.parseInt(sharedPreferences.getString(PREF_CAMERA_QUALITY, DEFAULT_QUALITY));
        } catch (NumberFormatException e) {
            return 75; // 默认质量
        }
    }
    
    // 拍摄设置
    public int getCaptureInterval() {
        try {
            return Integer.parseInt(sharedPreferences.getString(PREF_CAPTURE_INTERVAL, DEFAULT_INTERVAL));
        } catch (NumberFormatException e) {
            return 30; // 默认30秒
        }
    }
    
    public String getStopCondition() {
        return sharedPreferences.getString(PREF_STOP_CONDITION, DEFAULT_STOP_CONDITION);
    }
    
    public String getStopTime() {
        return sharedPreferences.getString(PREF_STOP_TIME, "");
    }
    
    public int getStopCount() {
        try {
            return Integer.parseInt(sharedPreferences.getString(PREF_STOP_COUNT, DEFAULT_STOP_COUNT));
        } catch (NumberFormatException e) {
            return 100; // 默认100张
        }
    }
    
    // 存储设置
    public boolean isAutoCleanEnabled() {
        return sharedPreferences.getBoolean(PREF_AUTO_CLEAN, DEFAULT_AUTO_CLEAN);
    }
    
    public int getMinSpaceMB() {
        try {
            return Integer.parseInt(sharedPreferences.getString(PREF_MIN_SPACE, DEFAULT_MIN_SPACE));
        } catch (NumberFormatException e) {
            return 100; // 默认100MB
        }
    }
    
    // 邮件设置
    public boolean isEmailSendingEnabled() {
        return sharedPreferences.getBoolean(PREF_SEND_EMAIL, DEFAULT_SEND_EMAIL);
    }
    
    public String getEmailAddress() {
        return sharedPreferences.getString(PREF_EMAIL_ADDRESS, DEFAULT_EMAIL);
    }
    
    // 工具方法
    public String[] parseResolution(String resolution) {
        if (resolution == null || !resolution.contains("x")) {
            return new String[]{"1920", "1080"};
        }
        return resolution.split("x");
    }
}