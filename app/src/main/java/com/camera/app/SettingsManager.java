package com.pipiqiang.qcamera.app;

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
    // 云存储设置
    private static final String PREF_CLOUD_ENABLED = "cloud_enabled";
    private static final String PREF_CLOUD_PROVIDER = "cloud_provider"; // s3/gcs/azure/oss/cos/none
    private static final String PREF_CLOUD_ACCESS_KEY = "cloud_access_key";
    private static final String PREF_CLOUD_SECRET_KEY = "cloud_secret_key";
    private static final String PREF_CLOUD_REGION = "cloud_region";
    private static final String PREF_CLOUD_BUCKET = "cloud_bucket";
    private static final String PREF_CLOUD_ENDPOINT = "cloud_endpoint";
    private static final String PREF_CLOUD_ENCRYPTION = "cloud_encryption_enabled";
    private static final String PREF_CLOUD_RESUMABLE = "cloud_resumable_enabled";
    private static final String PREF_CLOUD_AUTO_UPLOAD = "cloud_auto_upload";
    private static final String PREF_CLOUD_DELETE_ON_SUCCESS = "cloud_delete_on_success";
    private static final String PREF_CLOUD_PATH_PREFIX = "cloud_path_prefix";

    // SMTP设置
    private static final String PREF_SMTP_ENABLED = "smtp_enabled";
    private static final String PREF_SMTP_HOST = "smtp_host";
    private static final String PREF_SMTP_PORT = "smtp_port";
    private static final String PREF_SMTP_USERNAME = "smtp_username";
    private static final String PREF_SMTP_PASSWORD = "smtp_password";
    private static final String PREF_SMTP_ENCRYPTION = "smtp_encryption"; // none/tls/ssl
    private static final String PREF_SMTP_AUTH_METHOD = "smtp_auth_method"; // plain/login/cram_md5
    private static final String PREF_SMTP_FROM = "smtp_from";
    private static final String PREF_SMTP_HTML = "smtp_html_enabled";
    private static final String PREF_SMTP_RETRY_COUNT = "smtp_retry_count";
    private static final String PREF_SMTP_RETRY_BACKOFF = "smtp_retry_backoff";
    
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
    private static final boolean DEFAULT_CLOUD_ENABLED = false;
    private static final String DEFAULT_CLOUD_PROVIDER = "none";
    private static final String DEFAULT_CLOUD_REGION = "us-east-1";
    private static final boolean DEFAULT_CLOUD_ENCRYPTION = true;
    private static final boolean DEFAULT_CLOUD_RESUMABLE = true;
    private static final boolean DEFAULT_CLOUD_AUTO_UPLOAD = false;
    private static final boolean DEFAULT_CLOUD_DELETE_ON_SUCCESS = false;
    private static final String DEFAULT_CLOUD_PATH_PREFIX = "photos";
    private static final boolean DEFAULT_SMTP_ENABLED = false;
    private static final String DEFAULT_SMTP_HOST = "";
    private static final String DEFAULT_SMTP_PORT = "587";
    private static final String DEFAULT_SMTP_USERNAME = "";
    private static final String DEFAULT_SMTP_PASSWORD = "";
    private static final String DEFAULT_SMTP_ENCRYPTION = "tls";
    private static final String DEFAULT_SMTP_AUTH_METHOD = "login";
    private static final String DEFAULT_SMTP_FROM = "";
    private static final boolean DEFAULT_SMTP_HTML = true;
    private static final String DEFAULT_SMTP_RETRY_COUNT = "3";
    private static final String DEFAULT_SMTP_RETRY_BACKOFF = "30";
    
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

    // 云存储设置读取
    public boolean isCloudEnabled() { return sharedPreferences.getBoolean(PREF_CLOUD_ENABLED, DEFAULT_CLOUD_ENABLED); }
    public String getCloudProviderRaw() { return sharedPreferences.getString(PREF_CLOUD_PROVIDER, DEFAULT_CLOUD_PROVIDER); }
    public String getCloudAccessKey() { return sharedPreferences.getString(PREF_CLOUD_ACCESS_KEY, ""); }
    public String getCloudSecretKey() { return sharedPreferences.getString(PREF_CLOUD_SECRET_KEY, ""); }
    public String getCloudRegion() { return sharedPreferences.getString(PREF_CLOUD_REGION, DEFAULT_CLOUD_REGION); }
    public String getCloudBucket() { return sharedPreferences.getString(PREF_CLOUD_BUCKET, ""); }
    public String getCloudEndpoint() { return sharedPreferences.getString(PREF_CLOUD_ENDPOINT, ""); }
    public boolean isCloudEncryptionEnabled() { return sharedPreferences.getBoolean(PREF_CLOUD_ENCRYPTION, DEFAULT_CLOUD_ENCRYPTION); }
    public boolean isCloudResumableEnabled() { return sharedPreferences.getBoolean(PREF_CLOUD_RESUMABLE, DEFAULT_CLOUD_RESUMABLE); }
    public boolean isCloudAutoUploadEnabled() { return sharedPreferences.getBoolean(PREF_CLOUD_AUTO_UPLOAD, DEFAULT_CLOUD_AUTO_UPLOAD); }
    public boolean isCloudDeleteOnSuccessEnabled() { return sharedPreferences.getBoolean(PREF_CLOUD_DELETE_ON_SUCCESS, DEFAULT_CLOUD_DELETE_ON_SUCCESS); }
    public String getCloudPathPrefix() { return sharedPreferences.getString(PREF_CLOUD_PATH_PREFIX, DEFAULT_CLOUD_PATH_PREFIX); }

    // SMTP设置读取
    public boolean isSmtpEnabled() { return sharedPreferences.getBoolean(PREF_SMTP_ENABLED, DEFAULT_SMTP_ENABLED); }
    public String getSmtpHost() { return sharedPreferences.getString(PREF_SMTP_HOST, DEFAULT_SMTP_HOST); }
    public int getSmtpPort() {
        try { return Integer.parseInt(sharedPreferences.getString(PREF_SMTP_PORT, DEFAULT_SMTP_PORT)); }
        catch (Exception e) { return 587; }
    }
    public String getSmtpUsername() { return sharedPreferences.getString(PREF_SMTP_USERNAME, DEFAULT_SMTP_USERNAME); }
    public String getSmtpPassword() { return sharedPreferences.getString(PREF_SMTP_PASSWORD, DEFAULT_SMTP_PASSWORD); }
    public String getSmtpEncryption() { return sharedPreferences.getString(PREF_SMTP_ENCRYPTION, DEFAULT_SMTP_ENCRYPTION); }
    public String getSmtpAuthMethod() { return sharedPreferences.getString(PREF_SMTP_AUTH_METHOD, DEFAULT_SMTP_AUTH_METHOD); }
    public String getSmtpFrom() { return sharedPreferences.getString(PREF_SMTP_FROM, DEFAULT_SMTP_FROM); }
    public boolean isSmtpHtmlEnabled() { return sharedPreferences.getBoolean(PREF_SMTP_HTML, DEFAULT_SMTP_HTML); }
    public int getSmtpRetryCount() {
        try { return Integer.parseInt(sharedPreferences.getString(PREF_SMTP_RETRY_COUNT, DEFAULT_SMTP_RETRY_COUNT)); }
        catch (Exception e) { return 3; }
    }
    public int getSmtpRetryBackoffSec() {
        try { return Integer.parseInt(sharedPreferences.getString(PREF_SMTP_RETRY_BACKOFF, DEFAULT_SMTP_RETRY_BACKOFF)); }
        catch (Exception e) { return 30; }
    }
    
    // 工具方法
    public String[] parseResolution(String resolution) {
        if (resolution == null || !resolution.contains("x")) {
            return new String[]{"1920", "1080"};
        }
        return resolution.split("x");
    }
}