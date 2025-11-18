package com.pipiqiang.qcamera.app;

import android.content.Context;
import android.content.ContentUris;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;

import com.pipiqiang.qcamera.storage.CloudStorage;
import com.pipiqiang.qcamera.storage.CloudStorageFactory;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

public class CloudUploadHelper {

    private static final String TAG = "CloudUpload";

    public static void upload(Context context, SettingsManager settingsManager, String photoPath) throws Exception {
        if (photoPath == null || photoPath.isEmpty()) return;
        File file = new File(photoPath);
        if (!file.exists()) return;

        String key = buildUploadKey(settingsManager, file);

        CloudStorage.Provider provider;
        String raw = settingsManager.getCloudProviderRaw();
        if ("s3".equalsIgnoreCase(raw)) provider = CloudStorage.Provider.S3;
        else if ("webdav".equalsIgnoreCase(raw)) provider = CloudStorage.Provider.WEBDAV;
        else provider = CloudStorage.Provider.NONE;

        CloudStorage.Config cfg = new CloudStorage.Config(
                provider,
                settingsManager.getCloudAccessKey(),
                settingsManager.getCloudSecretKey(),
                settingsManager.getCloudRegion(),
                settingsManager.getCloudBucket(),
                settingsManager.getCloudEndpoint(),
                settingsManager.isCloudEncryptionEnabled(),
                settingsManager.isCloudResumableEnabled()
        );

        CloudStorage storage = CloudStorageFactory.create(context.getApplicationContext(), cfg);
        storage.upload(key, file);

        if (settingsManager.isCloudDeleteOnSuccessEnabled()) {
            boolean deleted = deleteFileAfterUpload(context, file);
            Log.d(TAG, "上传成功，删除本地文件: " + deleted);
        }
    }

    public static boolean deleteFileAfterUpload(Context context, File file) {
        try {
            if (file == null || !file.exists()) return true;
            if (file.delete()) return true;

            Uri contentUri = getImageContentUri(context, file);
            if (contentUri != null) {
                int rows = context.getContentResolver().delete(contentUri, null, null);
                if (rows > 0) return true;
            } else {
                int rows = context.getContentResolver().delete(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        MediaStore.Images.Media.DATA + "=?",
                        new String[]{file.getAbsolutePath()}
                );
                if (rows > 0) return true;
            }
        } catch (Exception e) {
            Log.e(TAG, "删除文件失败", e);
        }
        return false;
    }

    private static Uri getImageContentUri(Context context, File file) {
        Uri uri = null;
        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    new String[]{MediaStore.Images.Media._ID},
                    MediaStore.Images.Media.DATA + "=?",
                    new String[]{file.getAbsolutePath()},
                    null
            );
            if (cursor != null && cursor.moveToFirst()) {
                long id = cursor.getLong(0);
                uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id);
            }
        } catch (Exception ignore) {
        } finally {
            if (cursor != null) {
                try { cursor.close(); } catch (Exception ignored) {}
            }
        }
        return uri;
    }

    public static String buildUploadKey(SettingsManager settingsManager, File file) {
        String filename = file.getName();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd");
        String datePath = sdf.format(new Date(file.lastModified()));
        String prefix = settingsManager.getCloudPathPrefix();
        if (prefix == null || prefix.trim().isEmpty()) {
            prefix = "photos";
        }
        prefix = prefix.replaceAll("^/+", "").replaceAll("/+$", "");
        return prefix + "/" + datePath + "/" + filename;
    }
    public static String buildUploadKeyWithPrefix(SettingsManager settingsManager, File file, String customPrefix) {
        String filename = file.getName();
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy/MM/dd");
        String datePath = sdf.format(new java.util.Date(file.lastModified()));
        String prefix = customPrefix;
        if (prefix == null || prefix.trim().isEmpty()) {
            prefix = settingsManager.getCloudPathPrefix();
            if (prefix == null || prefix.trim().isEmpty()) {
                prefix = "photos";
            }
        }
        prefix = prefix.replaceAll("^/+", "").replaceAll("/+$", "");
        return prefix + "/" + datePath + "/" + filename;
    }
}