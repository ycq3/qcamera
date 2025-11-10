package com.pipiqiang.qcamera.app;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

public class PhotoProcessingService extends Service {
    
    @Override
    public void onCreate() {
        super.onCreate();
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // 处理照片（保存到存储或发送邮件）
        processPhoto();
        
        // 任务完成后停止服务
        stopSelf();
        
        return START_NOT_STICKY;
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    
    private void processPhoto() {
        // 实现照片处理逻辑
        // 1. 保存到设备存储
        // 2. 或发送到指定邮箱
    }
}