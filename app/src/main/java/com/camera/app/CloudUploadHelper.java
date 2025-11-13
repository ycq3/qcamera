package com.pipiqiang.qcamera.app;

import android.content.Context;
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

        String filename = file.getName();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd");
        String datePath = sdf.format(new Date(file.lastModified()));
        String prefix = settingsManager.getCloudPathPrefix();
        if (prefix == null || prefix.trim().isEmpty()) {
            prefix = "photos";
        }
        prefix = prefix.replaceAll("^/+", "").replaceAll("/+$", "");
        String key = prefix + "/" + datePath + "/" + filename;

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
            boolean deleted = file.delete();
            Log.d(TAG, "上传成功，删除本地文件: " + deleted);
        }
    }
}