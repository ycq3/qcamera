package com.camera.app;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

public class CameraWorker extends Worker {
    
    private static final String TAG = "CameraWorker";
    
    public CameraWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }
    
    @NonNull
    @Override
    public Result doWork() {
        Log.d(TAG, "执行拍照任务");
        
        try {
            // 初始化管理器
            CustomCameraManager cameraManager = new CustomCameraManager(getApplicationContext());
            SettingsManager settingsManager = new SettingsManager(getApplicationContext());
            StorageManager storageManager = new StorageManager(getApplicationContext());
            EmailManager emailManager = new EmailManager(getApplicationContext());
            
            // 启动后台线程
            cameraManager.startBackgroundThread();
            
            // 执行拍照操作
            String photoPath = takePicture(cameraManager);
            
            // 处理照片（保存或发送邮件）
            processPicture(photoPath, settingsManager, storageManager, emailManager);
            
            // 检查存储空间并清理
            checkAndCleanStorage(settingsManager, storageManager);
            
            // 停止后台线程
            cameraManager.stopBackgroundThread();
            
            return Result.success();
        } catch (Exception e) {
            Log.e(TAG, "拍照任务失败", e);
            return Result.failure();
        }
    }
    
    private String takePicture(CustomCameraManager cameraManager) throws Exception {
        // 实现拍照逻辑
        Log.d(TAG, "正在拍照...");
        
        final String[] photoPath = {null};
        final Exception[] captureError = {null};
        final Object lock = new Object();
        
        cameraManager.setCaptureCallback(new CustomCameraManager.CaptureCallback() {
            @Override
            public void onCaptureSuccess(String imagePath) {
                photoPath[0] = imagePath;
                synchronized (lock) {
                    lock.notify();
                }
            }
            
            @Override
            public void onCaptureError(Exception e) {
                captureError[0] = e;
                synchronized (lock) {
                    lock.notify();
                }
            }
        });
        
        // 打开相机
        cameraManager.openCamera();
        
        // 拍照
        cameraManager.takePicture();
        
        // 等待拍照完成
        synchronized (lock) {
            lock.wait(10000); // 最多等待10秒
        }
        
        // 关闭相机
        cameraManager.closeCamera();
        
        if (captureError[0] != null) {
            throw captureError[0];
        }
        
        return photoPath[0];
    }
    
    private void processPicture(String photoPath, SettingsManager settingsManager, 
                               StorageManager storageManager, EmailManager emailManager) {
        if (photoPath == null) {
            Log.e(TAG, "照片路径为空");
            return;
        }
        
        // 根据设置决定是保存照片还是发送邮件
        Log.d(TAG, "处理照片: " + photoPath);
        
        if (settingsManager.isEmailSendingEnabled()) {
            // 发送邮件
            String emailAddress = settingsManager.getEmailAddress();
            if (emailManager.isValidEmail(emailAddress)) {
                boolean success = emailManager.sendPhotoByEmail(photoPath, emailAddress);
                if (success) {
                    Log.d(TAG, "照片已发送到邮箱");
                } else {
                    Log.e(TAG, "发送邮件失败");
                }
            } else {
                Log.e(TAG, "邮箱地址无效");
            }
        } else {
            // 照片已保存到默认位置，无需额外操作
            Log.d(TAG, "照片已保存到: " + photoPath);
        }
    }
    
    private void checkAndCleanStorage(SettingsManager settingsManager, StorageManager storageManager) {
        // 检查存储空间并在必要时清理旧照片
        Log.d(TAG, "检查存储空间...");
        
        boolean autoClean = settingsManager.isAutoCleanEnabled();
        int minSpaceMB = settingsManager.getMinSpaceMB();
        
        storageManager.checkAndCleanStorage(autoClean, minSpaceMB);
    }
}