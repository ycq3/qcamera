package com.camera.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import androidx.core.app.NotificationCompat;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

public class CameraService extends Service {
    
    public static final String ACTION_START_CAPTURE = "com.camera.app.action.START_CAPTURE";
    public static final String ACTION_STOP_CAPTURE = "com.camera.app.action.STOP_CAPTURE";
    
    private static final int NOTIFICATION_ID = 1;
    private static final String CHANNEL_ID = "CameraServiceChannel";
    
    private WorkManager workManager;
    private PeriodicWorkRequest captureWorkRequest;
    
    @Override
    public void onCreate() {
        super.onCreate();
        workManager = WorkManager.getInstance(this);
        createNotificationChannel();
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();
            if (ACTION_START_CAPTURE.equals(action)) {
                startCapture();
            } else if (ACTION_STOP_CAPTURE.equals(action)) {
                stopCapture();
            }
        }
        return START_STICKY; // 服务被杀死后会重启
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    
    private void startCapture() {
        // 创建并启动周期性工作请求
        createCaptureWorkRequest();
        
        if (captureWorkRequest != null) {
            workManager.enqueue(captureWorkRequest);
        }
        
        // 显示前台服务通知
        startForeground(NOTIFICATION_ID, createNotification());
    }
    
    private void stopCapture() {
        // 取消所有相关工作
        if (workManager != null) {
            workManager.cancelAllWorkByTag("capture_work");
        }
        
        // 停止前台服务
        stopForeground(true);
        stopSelf();
    }
    
    private void createCaptureWorkRequest() {
        // 获取设置的拍摄间隔时间（秒）
        int intervalSeconds = getCaptureInterval();
        
        // 创建周期性工作请求
        captureWorkRequest = new PeriodicWorkRequest.Builder(
                CameraWorker.class, 
                intervalSeconds, 
                TimeUnit.SECONDS)
                .addTag("capture_work")
                .build();
    }
    
    private int getCaptureInterval() {
        SettingsManager settingsManager = new SettingsManager(this);
        return settingsManager.getCaptureInterval();
    }
    
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "定时拍照服务",
                    NotificationManager.IMPORTANCE_LOW
            );
            
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }
    
    private Notification createNotification() {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 
                0, 
                notificationIntent, 
                PendingIntent.FLAG_IMMUTABLE
        );
        
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("定时拍照助手")
                .setContentText("正在后台拍照...")
                .setSmallIcon(R.drawable.ic_camera)
                .setContentIntent(pendingIntent)
                .build();
    }
}