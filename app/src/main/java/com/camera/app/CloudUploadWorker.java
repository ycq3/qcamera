package com.pipiqiang.qcamera.app;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.NetworkType;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.pipiqiang.qcamera.storage.CloudStorage;
import com.pipiqiang.qcamera.storage.CloudStorageFactory;

import java.io.File;

public class CloudUploadWorker extends Worker {

    public static final String KEY_PHOTO_PATH = "photo_path";
    public static final String TAG_UPLOAD_WORK = "cloud_upload";
    private static final String TAG = "CloudUploadWorker";

    public CloudUploadWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        String photoPath = getInputData().getString(KEY_PHOTO_PATH);
        if (photoPath == null || photoPath.trim().isEmpty()) {
            Log.e(TAG, "缺少照片路径");
            return Result.failure();
        }
        try {
            SettingsManager settingsManager = new SettingsManager(getApplicationContext());
            File file = new File(photoPath);
            if (!file.exists()) {
                Log.w(TAG, "文件不存在，视为成功: " + photoPath);
                return Result.success();
            }

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

            // 复用 CloudUploadHelper 计算 key
            String key = CloudUploadHelper.buildUploadKey(settingsManager, file);
            CloudStorage storage = CloudStorageFactory.create(getApplicationContext(), cfg);
            storage.upload(key, file);

            if (settingsManager.isCloudDeleteOnSuccessEnabled()) {
                boolean deleted = CloudUploadHelper.deleteFileAfterUpload(getApplicationContext(), file);
                Log.d(TAG, "上传成功，删除本地文件: " + deleted);
            }

            return Result.success();
        } catch (Exception e) {
            Log.e(TAG, "自动上传失败", e);
            return Result.retry();
        }
    }

    public static Constraints connectedNetworkConstraints() {
        return new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
    }
}
