package com.camera.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

import androidx.core.app.NotificationCompat;

public class CameraService extends Service {
    
    private static final String TAG = "CameraService";
    
    public static final String ACTION_START_CAPTURE = "com.camera.app.action.START_CAPTURE";
    public static final String ACTION_STOP_CAPTURE = "com.camera.app.action.STOP_CAPTURE";
    public static final String ACTION_CAPTURE_COMPLETED = "com.camera.app.action.CAPTURE_COMPLETED";
    public static final String ACTION_SHOW_LAST_IMAGE = "com.camera.app.action.SHOW_LAST_IMAGE"; // 新增广播动作
    
    private static final int NOTIFICATION_ID = 1;
    private static final String CHANNEL_ID = "CameraServiceChannel";
    
    private Handler handler;
    private Runnable captureRunnable;
    private boolean isCapturing = false;
    private int cameraIndex = 0; // 摄像头索引
    private boolean isFlashEnabled = false; // 闪光灯设置
    
    // 电源管理
    private PowerManager.WakeLock wakeLock;
    
    // 拍照计数器
    private CaptureCounter captureCounter;
    
    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        handler = new Handler();
        
        // 初始化计数器
        captureCounter = new CaptureCounter(getApplicationContext());
        
        // 获取电源锁
        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "CameraApp::ServiceWakelockTag");
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();
            if (ACTION_START_CAPTURE.equals(action)) {
                // 获取摄像头索引和闪光灯设置
                cameraIndex = intent.getIntExtra("cameraIndex", 0);
                isFlashEnabled = intent.getBooleanExtra("flashEnabled", false);
                Log.d(TAG, "接收启动拍照命令 - 摄像头索引: " + cameraIndex + ", 闪光灯启用: " + isFlashEnabled);
                Log.d(TAG, "确认从Intent接收到的flashEnabled值: " + intent.getBooleanExtra("flashEnabled", false));
                startCapture();
            } else if (ACTION_STOP_CAPTURE.equals(action)) {
                Log.d(TAG, "接收停止拍照命令");
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
        if (isCapturing) {
            Log.d(TAG, "拍照已在进行中");
            return;
        }
        
        isCapturing = true;
        Log.d(TAG, "开始拍照服务");
        
        // 获取电源锁
        if (!wakeLock.isHeld()) {
            wakeLock.acquire(60*60*1000L /*60分钟*/);
        }
        
        // 发送广播通知MainActivity服务已启动
        Intent statusIntent = new Intent("com.camera.app.SERVICE_STATUS");
        statusIntent.putExtra("isRunning", true);
        sendBroadcast(statusIntent);
        
        // 创建并启动周期性拍照任务
        createCaptureTask();
        
        // 显示前台服务通知
        startForeground(NOTIFICATION_ID, createNotification());
    }
    
    private void stopCapture() {
        Log.d(TAG, "停止拍照服务");
        isCapturing = false;
        
        // 释放电源锁
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
        
        // 发送广播通知MainActivity服务已停止
        Intent statusIntent = new Intent("com.camera.app.SERVICE_STATUS");
        statusIntent.putExtra("isRunning", false);
        sendBroadcast(statusIntent);
        
        // 取消定时任务
        if (handler != null && captureRunnable != null) {
            handler.removeCallbacks(captureRunnable);
        }
        
        // 重置会话计数
        if (captureCounter != null) {
            captureCounter.resetSessionCount();
        }
        
        // 发送计数重置广播
        Intent countResetIntent = new Intent("com.camera.app.COUNT_RESET");
        sendBroadcast(countResetIntent);
        
        // 停止前台服务
        stopForeground(true);
        stopSelf();
    }
    
    private void createCaptureTask() {
        // 获取设置的拍摄间隔时间（秒）
        int intervalSeconds = getCaptureInterval();
        Log.d(TAG, "设置拍照间隔: " + intervalSeconds + " 秒");
        Log.d(TAG, "创建拍照任务时的参数 - 摄像头索引: " + cameraIndex + ", 闪光灯启用: " + isFlashEnabled);
        
        captureRunnable = new Runnable() {
            @Override
            public void run() {
                if (isCapturing) {
                    Log.d(TAG, "执行拍照任务 - 摄像头索引: " + cameraIndex + ", 闪光灯启用: " + isFlashEnabled);
                    // 执行拍照
                    executeCapture();
                    
                    // 继续下一次拍照
                    handler.postDelayed(this, intervalSeconds * 1000L);
                }
            }
        };
        
        // 立即开始第一次拍照
        handler.post(captureRunnable);
    }
    
    private void executeCapture() {
        // 直接执行拍照任务
        Thread captureThread = new Thread(new Runnable() {
            @Override
            public void run() {
                CustomCameraManager cameraManager = null;
                try {
                    Log.d(TAG, "开始执行拍照任务 - 当前摄像头索引: " + cameraIndex + ", 闪光灯启用: " + isFlashEnabled);
                    
                    // 初始化管理器
                    cameraManager = new CustomCameraManager(CameraService.this);
                    SettingsManager settingsManager = new SettingsManager(CameraService.this);
                    StorageManager storageManager = new StorageManager(CameraService.this);
                    EmailManager emailManager = new EmailManager(CameraService.this);
                    
                    // 设置摄像头索引
                    cameraManager.setSelectedCameraIndex(cameraIndex);
                    // 设置闪光灯状态
                    cameraManager.setFlashEnabled(isFlashEnabled);
                    Log.d(TAG, "设置相机管理器参数 - 摄像头索引: " + cameraIndex + ", 闪光灯启用: " + isFlashEnabled);
                    Log.d(TAG, "确认闪光灯状态 - isFlashEnabled变量值: " + isFlashEnabled);
                    
                    // 设置TextureView（虽然在服务中不直接使用，但保持接口一致性）
                    cameraManager.setTextureView(null);
                    
                    // 设置预览显示回调（在服务中不需要显示预览）
                    cameraManager.setPreviewDisplayCallback(new CustomCameraManager.PreviewDisplayCallback() {
                        @Override
                        public void onPreviewDisplay(Bitmap bitmap) {
                            // 在服务中不需要显示预览，但可以发送广播通知
                            Log.d(TAG, "拍照完成，图片已生成");
                        }
                    });
                    
                    // 启动后台线程
                    cameraManager.startBackgroundThread();
                    
                    // 执行拍照操作
                    String photoPath = takePicture(cameraManager);
                    
                    // 处理照片（保存或发送邮件）
                    processPicture(photoPath, settingsManager, storageManager, emailManager);
                    
                    // 检查存储空间并清理
                    checkAndCleanStorage(settingsManager, storageManager);
                    
                    // 递增计数器
                    if (captureCounter != null) {
                        captureCounter.incrementCount();
                    }
                    
                    // 发送拍照完成广播
                    Intent captureCompletedIntent = new Intent(ACTION_CAPTURE_COMPLETED);
                    captureCompletedIntent.putExtra("photoPath", photoPath);
                    sendBroadcast(captureCompletedIntent);
                    
                    // 发送显示最后图片的广播
                    Intent showLastImageIntent = new Intent(ACTION_SHOW_LAST_IMAGE);
                    showLastImageIntent.putExtra("photoPath", photoPath);
                    sendBroadcast(showLastImageIntent);
                    
                    Log.d(TAG, "拍照任务完成");
                } catch (Exception e) {
                    Log.e(TAG, "执行拍照任务时出错", e);
                } finally {
                    // 确保在出现异常时也停止后台线程
                    try {
                        if (cameraManager != null) {
                            // 关闭相机
                            cameraManager.closeCamera();
                            cameraManager.stopBackgroundThread();
                        }
                    } catch (Exception ex) {
                        Log.e(TAG, "停止后台线程时出错", ex);
                    }
                }
            }
        });
        captureThread.start();
    }
    
    private String takePicture(CustomCameraManager cameraManager) throws Exception {
        // 实现拍照逻辑
        Log.d(TAG, "正在拍照...");
        
        final String[] photoPath = {null};
        final Exception[] captureError = {null};
        final Object lock = new Object();
        final boolean[] captureCompleted = {false};
        
        cameraManager.setCaptureCallback(new CustomCameraManager.CaptureCallback() {
            @Override
            public void onCaptureSuccess(String imagePath) {
                Log.d(TAG, "拍照成功，图片路径: " + imagePath);
                photoPath[0] = imagePath;
                captureCompleted[0] = true;
                synchronized (lock) {
                    lock.notify();
                }
            }
            
            @Override
            public void onCaptureError(Exception e) {
                Log.e(TAG, "拍照失败", e);
                captureError[0] = e;
                captureCompleted[0] = true;
                synchronized (lock) {
                    lock.notify();
                }
            }
        });
        
        // 打开相机
        Log.d(TAG, "打开相机");
        cameraManager.openCamera();
        
        // 等待一段时间确保相机打开
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            // 忽略中断异常
        }
        
        // 检查相机是否成功打开
        if (!cameraManager.isCameraOpened()) {
            Log.e(TAG, "相机打开失败");
            throw new Exception("相机打开失败");
        }
        
        // 拍照
        Log.d(TAG, "执行拍照");
        cameraManager.takePicture();
        
        // 等待拍照完成，增加超时处理
        Log.d(TAG, "等待拍照完成");
        synchronized (lock) {
            long startTime = System.currentTimeMillis();
            long timeout = 15000; // 15秒超时
            
            while (!captureCompleted[0] && (System.currentTimeMillis() - startTime) < timeout) {
                try {
                    lock.wait(timeout - (System.currentTimeMillis() - startTime));
                } catch (InterruptedException e) {
                    // 忽略中断异常
                    break;
                }
            }
            
            // 检查是否超时
            if (!captureCompleted[0]) {
                Log.e(TAG, "拍照超时");
                throw new Exception("拍照超时");
            }
        }
        
        // 注意：这里不再关闭相机，以便保持预览
        Log.d(TAG, "拍照完成，相机保持开启状态以维持预览");
        
        if (captureError[0] != null) {
            throw captureError[0];
        }
        
        Log.d(TAG, "返回照片路径: " + photoPath[0]);
        return photoPath[0];
    }
    
    private void processPicture(String photoPath, SettingsManager settingsManager, 
                               StorageManager storageManager, EmailManager emailManager) {
        if (photoPath == null || photoPath.isEmpty()) {
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
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        stopCapture();
        Log.d(TAG, "拍照服务已销毁");
    }
}