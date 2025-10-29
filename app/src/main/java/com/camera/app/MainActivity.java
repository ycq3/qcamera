package com.camera.app;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.ActionBar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    
    private static final String TAG = "MainActivity";
    private static final int PERMISSION_REQUEST_CODE = 100;
    
    private ProgressButton btnStartStop; // 修改为ProgressButton
    private Button btnSettings;
    private Button btnGallery;
    private Button btnFlashToggle; // 闪光灯切换按钮（文本按钮）
    private Button btnSelectCamera; // 选择摄像头按钮
    private TextView tvStatus;
    private ImageView appIcon; // 应用图标
    private LinearLayout appIconContainer; // 图标容器
    private TextView tvCaptureCount; // 拍照计数器显示
    private TextView tvCaptureTime; // 拍摄时间显示
    private boolean isRunning = false;
    
    // 相机预览相关
    private FrameLayout cameraPreviewContainer;
    private TextureView textureView;
    private ImageView ivCapturedImage; // 用于显示拍摄照片的ImageView
    private ImageButton btnFlashIndicator; // 闪光灯指示器（图标）
    private Handler backgroundHandler;
    private HandlerThread backgroundThread;
    private String cameraId;
    private Size previewSize;
    private boolean isFlashOn = false; // 仅用于预览时的闪光灯指示
    private FlashMode flashModeForCapture = FlashMode.OFF; // 闪光灯三态：关/开/自动
    private boolean isPreviewShowing = false;
    
    // 自定义相机管理器
    private CustomCameraManager customCameraManager;
    
    // 进度条相关
    private Handler progressHandler = new Handler();
    private Runnable progressRunnable;
    private long startTime;
    private long interval; // 从设置中获取的间隔时间（毫秒）
    // 方案A：Activity内驱动的拍照循环
    private Handler captureHandler = new Handler();
    private Runnable captureCycleRunnable;
    private long previewWarmupMs = 1200L; // 预览暖机时间，毫秒
    
    // 电源管理
    private PowerManager.WakeLock wakeLock;
    
    // 多摄像头支持
    private String[] cameraIds;
    private int currentCameraIndex = 0;
    private String[] cameraNames; // 摄像头名称
    
    // 拍照计数器
    private CaptureCounter captureCounter;
    
    // 广播接收器，用于接收拍照服务的状态更新
    private BroadcastReceiver serviceStatusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if ("com.camera.app.SERVICE_STATUS".equals(action)) {
                boolean isServiceRunning = intent.getBooleanExtra("isRunning", false);
                updateUI(isServiceRunning);
            }
        }
    };
    
    // 新增广播接收器，用于接收拍照完成的通知
    private BroadcastReceiver captureCompletedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if ("com.camera.app.action.CAPTURE_COMPLETED".equals(action)) {
                // 更新计数器显示
                updateCaptureCountDisplay();
                
                // 显示最后拍摄的图片
                String photoPath = intent.getStringExtra("photoPath");
                showLastCapturedImage(photoPath);
                // 拍照完成后关闭相机资源，并调度下一次拍照
                closeCameraResourcesOnly();
                scheduleNextCycle();
            } else if ("com.camera.app.COUNT_RESET".equals(action)) {
                // 计数器重置
                updateCaptureCountDisplay();
            } else if ("com.camera.app.action.SHOW_LAST_IMAGE".equals(action)) {
                // 显示最后拍摄的图片
                String photoPath = intent.getStringExtra("photoPath");
                showLastCapturedImage(photoPath);
                // 同步关闭相机并调度下一次拍照
                closeCameraResourcesOnly();
                scheduleNextCycle();
            }
        }
    };
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        // 初始化计数器
        captureCounter = new CaptureCounter(getApplicationContext());
        
        // 设置窗口标志以支持锁屏状态下运行
        setupWindowFlags();
        
        // 获取电源锁
        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "CameraApp::MyWakelockTag");
        
        // 确保ActionBar被隐藏
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.hide();
        }
        
        initViews();
        setupListeners();
        
        // 注册广播接收器
        IntentFilter filter = new IntentFilter("com.camera.app.SERVICE_STATUS");
        registerReceiver(serviceStatusReceiver, filter);
        
        // 注册拍照完成广播接收器
        IntentFilter captureFilter = new IntentFilter();
        captureFilter.addAction("com.camera.app.action.CAPTURE_COMPLETED");
        captureFilter.addAction("com.camera.app.COUNT_RESET");
        captureFilter.addAction("com.camera.app.action.SHOW_LAST_IMAGE"); // 添加新动作
        registerReceiver(captureCompletedReceiver, captureFilter);
        
        // 检查必要权限
        if (checkPermissions()) {
            // 如果权限已授予，检查服务是否正在运行
            checkServiceStatus();
        }
        
        // 初始化计数器显示
        updateCaptureCountDisplay();
    }
    
    // 设置窗口标志以支持锁屏状态下运行
    private void setupWindowFlags() {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
                            WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON |
                            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD |
                            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
    }
    
    private void initViews() {
        btnStartStop = findViewById(R.id.btn_start_stop);
        btnSettings = findViewById(R.id.btn_settings);
        btnGallery = findViewById(R.id.btn_gallery);
        btnFlashToggle = findViewById(R.id.btn_flash_toggle); // 初始化闪光灯切换按钮
        btnSelectCamera = findViewById(R.id.btn_select_camera); // 初始化选择摄像头按钮
        tvStatus = findViewById(R.id.tv_status);
        appIcon = findViewById(R.id.app_icon); // 初始化应用图标
        appIconContainer = findViewById(R.id.app_icon_container); // 初始化图标容器
        tvCaptureCount = findViewById(R.id.tv_capture_count); // 初始化计数器显示
        
        // 初始化相机预览相关视图
        cameraPreviewContainer = findViewById(R.id.camera_preview_container);
        textureView = findViewById(R.id.texture_view);
        ivCapturedImage = findViewById(R.id.iv_captured_image); // 初始化ImageView
        btnFlashIndicator = findViewById(R.id.btn_flash_indicator);
        tvCaptureTime = findViewById(R.id.tv_capture_time); // 初始化拍摄时间显示
    }
    
    private void setupListeners() {
        btnStartStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isRunning) {
                    stopCapture();
                } else {
                    if (checkPermissions()) {
                        startCapture();
                    } else {
                        Toast.makeText(MainActivity.this, "请授予必要权限", Toast.LENGTH_SHORT).show();
                    }
                }
            }
        });
        
        btnSelectCamera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showCameraSelection();
            }
        });
        
        btnFlashToggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleFlashSetting();
            }
        });
        
        btnSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
                startActivity(intent);
            }
        });
        
        btnGallery.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, PhotoGalleryActivity.class);
                startActivity(intent);
            }
        });
        
        // 纹理视图表面纹理监听器
        textureView.setSurfaceTextureListener(surfaceTextureListener);
    }
    
    private boolean checkPermissions() {
        Log.d(TAG, "检查权限");
        
        String[] permissions;
        if (Build.VERSION.SDK_INT >= 33) {
            permissions = new String[]{
                    Manifest.permission.CAMERA,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_EXTERNAL_STORAGE
            };
        } else {
            permissions = new String[]{
                    Manifest.permission.CAMERA,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_EXTERNAL_STORAGE
            };
        }
        
        // 检查是否需要MANAGE_EXTERNAL_STORAGE权限（Android 10及以上）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                // 请求MANAGE_EXTERNAL_STORAGE权限
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                Uri uri = Uri.fromParts("package", getPackageName(), null);
                intent.setData(uri);
                startActivity(intent);
                Toast.makeText(this, "请允许应用访问所有文件权限，以便照片能显示在系统相册中", Toast.LENGTH_LONG).show();
            }
        }
        
        ActivityCompat.requestPermissions(this, permissions, PERMISSION_REQUEST_CODE);
        return true;
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            
            if (!allGranted) {
                Toast.makeText(this, "缺少必要权限，部分功能可能无法使用", Toast.LENGTH_LONG).show();
            } else {
                Log.d(TAG, "所有权限已授予");
                // 获取可用摄像头列表
                initializeCameras();
            }
        }
    }
    
    // 初始化摄像头列表
    private void initializeCameras() {
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            cameraIds = manager.getCameraIdList();
            cameraNames = new String[cameraIds.length];
            
            // 获取每个摄像头的名称
            for (int i = 0; i < cameraIds.length; i++) {
                CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraIds[i]);
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                
                if (facing != null) {
                    switch (facing) {
                        case CameraCharacteristics.LENS_FACING_FRONT:
                            cameraNames[i] = "前置摄像头 " + (i + 1);
                            break;
                        case CameraCharacteristics.LENS_FACING_BACK:
                            cameraNames[i] = "后置摄像头 " + (i + 1);
                            break;
                        case CameraCharacteristics.LENS_FACING_EXTERNAL:
                            cameraNames[i] = "外接摄像头 " + (i + 1);
                            break;
                        default:
                            cameraNames[i] = "摄像头 " + (i + 1);
                            break;
                    }
                } else {
                    cameraNames[i] = "摄像头 " + (i + 1);
                }
            }
            
            Log.d(TAG, "发现 " + cameraIds.length + " 个摄像头");
        } catch (CameraAccessException e) {
            Log.e(TAG, "获取摄像头列表失败", e);
            cameraIds = new String[0];
            cameraNames = new String[0];
        }
    }
    
    // 安全地切换摄像头
    private void switchCameraSafely() {
        // 在后台线程中执行摄像头切换
        if (backgroundHandler != null) {
            backgroundHandler.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        // 关闭当前相机
                        if (customCameraManager != null) {
                            customCameraManager.closeCamera();
                        }
                        
                        // 在主线程中更新UI
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                // 重新打开相机
                                if (textureView.isAvailable()) {
                                    openCamera(textureView.getWidth(), textureView.getHeight());
                                } else {
                                    textureView.setSurfaceTextureListener(surfaceTextureListener);
                                }
                            }
                        });
                    } catch (Exception e) {
                        Log.e(TAG, "切换摄像头时发生错误", e);
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(MainActivity.this, "切换摄像头失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                }
            });
        }
    }
    
    // 显示摄像头选择对话框
    private void showCameraSelection() {
        if (cameraIds == null || cameraIds.length == 0) {
            Toast.makeText(this, "未发现可用摄像头", Toast.LENGTH_SHORT).show();
            return;
        }
        
        if (cameraIds.length == 1) {
            Toast.makeText(this, "设备只有一个摄像头", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // 创建选择对话框
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("选择摄像头");
        builder.setItems(cameraNames, (dialog, which) -> {
            if (which != currentCameraIndex) {
                currentCameraIndex = which;
                btnSelectCamera.setText(cameraNames[currentCameraIndex]);
                
                // 如果预览正在显示，重新打开相机
                if (isPreviewShowing) {
                    switchCameraSafely();
                }
                
                Toast.makeText(this, "已选择: " + cameraNames[currentCameraIndex], Toast.LENGTH_SHORT).show();
            }
        });
        builder.show();
    }
    
    // 切换闪光灯设置（用于拍照）
    private void toggleFlashSetting() {
        // OFF -> ON -> AUTO -> OFF 循环
        switch (flashModeForCapture) {
            case OFF:
                flashModeForCapture = FlashMode.ON;
                break;
            case ON:
                flashModeForCapture = FlashMode.AUTO;
                break;
            default:
                flashModeForCapture = FlashMode.OFF;
                break;
        }
        Log.d(TAG, "切换闪光灯设置 - 当前模式: " + flashModeForCapture);
        String modeText = (flashModeForCapture == FlashMode.ON) ? "闪光灯: 开" : (flashModeForCapture == FlashMode.AUTO ? "闪光灯: 自动" : "闪光灯: 关");
        btnFlashToggle.setText(modeText);

        // 更新预览时的闪光灯指示器
        updateFlashIndicator();

        String toastText = (flashModeForCapture == FlashMode.ON) ? "开启" : (flashModeForCapture == FlashMode.AUTO ? "自动" : "关闭");
        Toast.makeText(this, "拍照时闪光灯设置: " + toastText, Toast.LENGTH_SHORT).show();
    }
    
    // 更新预览时的闪光灯指示器
    private void updateFlashIndicator() {
        if (btnFlashIndicator != null) {
            btnFlashIndicator.setImageResource(flashModeForCapture == FlashMode.ON ? R.drawable.ic_flash_on : R.drawable.ic_flash_off);
        }
    }
    
    private void startCapture() {
        Log.d(TAG, "开始拍照");
        
        // 更新运行状态
        isRunning = true;
        
        // 获取拍照间隔设置
        SettingsManager settingsManager = new SettingsManager(this);
        interval = settingsManager.getCaptureInterval() * 1000L; // 转换为毫秒
        
        // 获取电源锁
        if (!wakeLock.isHeld()) {
            wakeLock.acquire(60*60*1000L /*60分钟*/);
        }

        // 更新UI状态与进度条
        updateUI(true);
        
        // 启动进度条更新
        startProgressUpdate();

        // 启动Activity驱动的拍照循环
        startCaptureLoop();
    }
    
    private void stopCapture() {
        // 释放电源锁
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
        
        // 停止Activity驱动的拍照循环
        stopCaptureLoop();

        // 关闭相机资源，但保留已显示的图片
        closeCameraResourcesOnly();
        
        // 更新UI状态
        updateUI(false);
        
        // 停止进度条更新
        stopProgressUpdate();
        
        // 重置会话计数
        captureCounter.resetSessionCount();
        updateCaptureCountDisplay();
    }

    // 启动Activity内的拍照循环
    private void startCaptureLoop() {
        // 立即执行一次拍照周期
        if (captureCycleRunnable != null) {
            captureHandler.removeCallbacks(captureCycleRunnable);
        }
        captureCycleRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isRunning) return;
                triggerOneCaptureCycle();
            }
        };
        captureHandler.post(captureCycleRunnable);
    }

    // 停止Activity内的拍照循环
    private void stopCaptureLoop() {
        if (captureCycleRunnable != null) {
            captureHandler.removeCallbacks(captureCycleRunnable);
            captureCycleRunnable = null;
        }
    }

    // 单次拍照周期：短暂预览->拍照
    private void triggerOneCaptureCycle() {
        // 显示短暂预览（仅在没有预览显示时才显示）
        if (!isPreviewShowing) {
            showCameraPreview();
        }

        // 预览暖机后执行拍照
        captureHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    if (customCameraManager != null) {
                        // 更新闪光模式到拍照设置
                        customCameraManager.setFlashMode(flashModeForCapture);
                        customCameraManager.takePicture();
                    } else {
                        Log.w(TAG, "拍照管理器未就绪，跳过本次拍照");
                    }
                } catch (Exception e) {
                    Log.e(TAG, "执行拍照异常", e);
                }
            }
        }, previewWarmupMs);
    }

    // 仅关闭相机资源，不影响已显示的图片
    private void closeCameraResourcesOnly() {
        try {
            if (customCameraManager != null) {
                customCameraManager.closeCamera();
                customCameraManager.stopBackgroundThread();
                customCameraManager = null;
            }
        } catch (Exception e) {
            Log.e(TAG, "关闭相机资源失败", e);
        }
        if (textureView != null) {
            textureView.setVisibility(View.GONE);
        }
    }

    // 调度下一次拍照
    private void scheduleNextCycle() {
        if (!isRunning) return;
        // 重置进度参考时间
        startTime = System.currentTimeMillis();
        // 在设定间隔后执行下一次周期
        captureHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!isRunning) return;
                triggerOneCaptureCycle();
            }
        }, interval);
    }
    
    // 启动进度条更新
    private void startProgressUpdate() {
        // 停止之前的进度更新（如果有的话）
        stopProgressUpdate();
        
        // 设置进度按钮为运行状态
        btnStartStop.setProgressState(true);
        
        // 记录开始时间
        startTime = System.currentTimeMillis();
        
        // 创建进度更新任务
        progressRunnable = new Runnable() {
            @Override
            public void run() {
                if (isRunning) {
                    long elapsed = System.currentTimeMillis() - startTime;
                    float progress = (float) (elapsed % interval) / interval;
                    btnStartStop.setProgress(progress);
                    
                    // 每100毫秒更新一次进度
                    progressHandler.postDelayed(this, 100);
                }
            }
        };
        
        // 启动进度更新
        progressHandler.post(progressRunnable);
    }
    
    // 停止进度条更新
    private void stopProgressUpdate() {
        if (progressRunnable != null) {
            progressHandler.removeCallbacks(progressRunnable);
            progressRunnable = null;
        }
        // 重置进度条
        btnStartStop.setProgress(0f);
        btnStartStop.setProgressState(false);
    }
    
    // 更新计数器显示
    private void updateCaptureCountDisplay() {
        if (tvCaptureCount != null) {
            // 重新加载以获取服务侧的最新计数
            if (captureCounter != null) {
                captureCounter.reloadCounts();
            }
            int sessionCount = captureCounter.getSessionCount();
            tvCaptureCount.setText("已拍张数: " + sessionCount);
        }
    }
    
    private void updateUI(boolean running) {
        isRunning = running;
        btnStartStop.setText(running ? R.string.stop_capture : R.string.start_capture);
        tvStatus.setText(running ? R.string.status_running : R.string.status_stopped);
        
        // 更新计数器显示
        updateCaptureCountDisplay();
        
        // 根据运行状态控制图标显示
        if (running) {
            // 运行时隐藏图标容器
            appIconContainer.setVisibility(View.GONE);
            // 显示摄像头选择按钮
            btnSelectCamera.setVisibility(View.VISIBLE);
            // 显示闪光灯设置按钮
            btnFlashToggle.setVisibility(View.VISIBLE);
            // 显示计数器
            tvCaptureCount.setVisibility(View.VISIBLE);
        } else {
            // 停止时显示图标容器
            appIconContainer.setVisibility(View.VISIBLE);
            // 隐藏摄像头选择按钮
            btnSelectCamera.setVisibility(View.GONE);
            // 隐藏闪光灯设置按钮
            btnFlashToggle.setVisibility(View.GONE);
            // 隐藏计数器
            tvCaptureCount.setVisibility(View.GONE);
        }
    }
    
    // 相机预览相关方法
    private void showCameraPreview() {
        cameraPreviewContainer.setVisibility(View.VISIBLE);
        appIconContainer.setVisibility(View.GONE); // 隐藏图标容器
        btnSelectCamera.setVisibility(View.VISIBLE); // 显示摄像头选择按钮
        btnFlashToggle.setVisibility(View.VISIBLE); // 显示闪光灯设置按钮
        tvCaptureCount.setVisibility(View.VISIBLE); // 显示计数器

        // 确保ImageView隐藏，TextureView显示
        if (ivCapturedImage != null) {
            ivCapturedImage.setVisibility(View.GONE);
        }
        if (tvCaptureTime != null) {
            tvCaptureTime.setVisibility(View.GONE);
        }
        if (textureView != null) {
            textureView.setVisibility(View.VISIBLE);
            // 注意：TextureView不支持背景绘制，所以我们移除设置背景颜色的代码
        }
    
        // 更新摄像头选择按钮文本
        if (cameraNames != null && cameraNames.length > currentCameraIndex) {
            btnSelectCamera.setText(cameraNames[currentCameraIndex]);
        }
    
        isPreviewShowing = true;
        startBackgroundThread();
    
        // 如果纹理视图已经准备好了，就打开相机
        if (textureView != null && textureView.isAvailable()) {
            openCamera(textureView.getWidth(), textureView.getHeight());
        } else if (textureView != null) {
            textureView.setSurfaceTextureListener(surfaceTextureListener);
        }
    }

    private void hideCameraPreview() {
        cameraPreviewContainer.setVisibility(View.GONE);
        appIconContainer.setVisibility(View.VISIBLE); // 显示图标容器
        btnSelectCamera.setVisibility(View.GONE); // 隐藏摄像头选择按钮
        btnFlashToggle.setVisibility(View.GONE); // 隐藏闪光灯设置按钮
        tvCaptureCount.setVisibility(View.GONE); // 隐藏计数器
        isPreviewShowing = false;
        
        // 关闭自定义相机管理器
        if (customCameraManager != null) {
            customCameraManager.closeCamera();
            customCameraManager.stopBackgroundThread();
            customCameraManager = null;
        }
        
        stopBackgroundThread();
        
        // 确保ImageView和TextureView都隐藏
        if (ivCapturedImage != null) {
            ivCapturedImage.setVisibility(View.GONE);
        }
        if (tvCaptureTime != null) {
            tvCaptureTime.setVisibility(View.GONE);
        }
        if (textureView != null) {
            textureView.setVisibility(View.GONE);
        }
    }
    
    private void startBackgroundThread() {
        // 如果后台线程已经存在，先停止它
        if (backgroundThread != null) {
            stopBackgroundThread();
        }
        
        backgroundThread = new HandlerThread("CameraBackground");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
    }
    
    private void stopBackgroundThread() {
        if (backgroundThread != null) {
            backgroundThread.quitSafely();
            try {
                backgroundThread.join();
                backgroundThread = null;
                backgroundHandler = null;
            } catch (InterruptedException e) {
                Log.e(TAG, "中断后台线程", e);
            }
        }
    }
    
    private final TextureView.SurfaceTextureListener surfaceTextureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int width, int height) {
            if (isPreviewShowing) {
                openCamera(width, height);
            }
        }
        
        @Override
        public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surface, int width, int height) {
            // 重新配置相机预览
            if (isPreviewShowing) {
                // 关闭当前相机
                if (customCameraManager != null) {
                    customCameraManager.closeCamera();
                }
                openCamera(width, height);
            }
        }
        
        @Override
        public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surface) {
            // 当纹理销毁时，确保关闭相机以释放会话，避免悬挂导致黑屏
            if (customCameraManager != null) {
                customCameraManager.closeCamera();
            }
            return true;
        }
        
        @Override
        public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surface) {
            // 忽略
        }
    };
    
    private void openCamera(int width, int height) {
        // 确保在后台线程中执行
        if (backgroundHandler == null) {
            Log.e(TAG, "后台线程未启动");
            return;
        }
        
        backgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                // 使用自定义相机管理器
                if (customCameraManager == null) {
                    Log.d(TAG, "创建新的CustomCameraManager实例");
                    customCameraManager = new CustomCameraManager(MainActivity.this);
                } 
                
                // 更新配置
                customCameraManager.setTextureView(textureView);
                customCameraManager.setSelectedCameraIndex(currentCameraIndex);
                customCameraManager.setFlashMode(flashModeForCapture);
                
                // 设置预览显示回调
                customCameraManager.setPreviewDisplayCallback(new CustomCameraManager.PreviewDisplayCallback() {
                    @Override
                    public void onPreviewDisplay(Bitmap bitmap) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                // 显示拍摄的照片
                                showCapturedImage(bitmap);
                            }
                        });
                    }
                });
                
                // 设置拍照回调
                customCameraManager.setCaptureCallback(new CustomCameraManager.CaptureCallback() {
                    @Override
                    public void onCaptureSuccess(String imagePath) {
                        // 发送广播通知拍照完成
                        Intent captureCompletedIntent = new Intent("com.camera.app.action.CAPTURE_COMPLETED");
                        captureCompletedIntent.putExtra("photoPath", imagePath);
                        sendBroadcast(captureCompletedIntent);
                        
                        // 发送显示最后图片的广播
                        Intent showLastImageIntent = new Intent("com.camera.app.action.SHOW_LAST_IMAGE");
                        showLastImageIntent.putExtra("photoPath", imagePath);
                        sendBroadcast(showLastImageIntent);
                    }
                    
                    @Override
                    public void onCaptureError(Exception e) {
                        Log.e(TAG, "拍照失败", e);
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(MainActivity.this, "拍照失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                });
                
                customCameraManager.startBackgroundThread();
                customCameraManager.openCamera();
            }
        });
    }
    
    private static class CompareSizesByArea implements Comparator<Size> {
        @Override
        public int compare(Size lhs, Size rhs) {
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
                    (long) rhs.getWidth() * rhs.getHeight());
        }
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        // 运行时不自动显示预览，以节约电量

        // 更新计数器显示
        updateCaptureCountDisplay();
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        // 隐藏预览并关闭相机，确保资源正确释放
        if (isPreviewShowing) {
            hideCameraPreview();
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 使用CustomCameraManager关闭相机
        if (customCameraManager != null) {
            customCameraManager.closeCamera();
            customCameraManager.stopBackgroundThread();
        }
        stopBackgroundThread();
        stopProgressUpdate(); // 确保停止进度更新
        
        // 释放电源锁
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
        
        unregisterReceiver(serviceStatusReceiver);
        unregisterReceiver(captureCompletedReceiver); // 取消注册新的广播接收器
    }
    
    // 显示拍摄的照片
    private void showCapturedImage(Bitmap bitmap) {
        if (bitmap != null && ivCapturedImage != null) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    // 显示拍摄的照片，并隐藏预览以节电
                    cameraPreviewContainer.setVisibility(View.VISIBLE);
                    ivCapturedImage.setVisibility(View.VISIBLE);
                    ivCapturedImage.setImageBitmap(bitmap);
                    ivCapturedImage.bringToFront();
                    if (textureView != null) textureView.setVisibility(View.GONE);
                    Log.d(TAG, "显示拍摄的照片并隐藏预览");

                    // 显示当前时间
                    if (tvCaptureTime != null) {
                        tvCaptureTime.setVisibility(View.VISIBLE);
                        tvCaptureTime.setText("拍摄时间: " + android.text.format.DateFormat.format("yyyy-MM-dd HH:mm:ss", System.currentTimeMillis()));
                    }
                    
                    // 延迟一段时间后重新显示预览（如果需要）
                    if (isRunning && textureView != null) {
                        textureView.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                // 只有在拍照循环运行时才重新显示预览
                                if (isRunning && !isPreviewShowing) {
                                    showCameraPreview();
                                }
                            }
                        }, 1000); // 1秒后重新显示预览
                    }
                }
            });
        }
    }

    // 显示上次拍摄的照片
    private void showLastCapturedImage(String photoPath) {
        Log.d(TAG, "显示上次拍摄的照片: " + photoPath);
        // 显示最后拍摄的照片
        if (photoPath != null && !photoPath.isEmpty()) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (ivCapturedImage != null) {
                        cameraPreviewContainer.setVisibility(View.VISIBLE);
                        ivCapturedImage.setVisibility(View.VISIBLE);
                        ivCapturedImage.bringToFront();
                        if (textureView != null) textureView.setVisibility(View.GONE);

                        // 加载并显示图片
                        Bitmap bitmap = BitmapFactory.decodeFile(photoPath);
                        if (bitmap != null) {
                            ivCapturedImage.setImageBitmap(bitmap);
                            Log.d(TAG, "成功加载并显示照片");
                        } else {
                            Log.e(TAG, "无法加载照片: " + photoPath);
                        }

                        // 显示拍摄时间（从文件时间或当前时间）
                        if (tvCaptureTime != null) {
                            long ts = System.currentTimeMillis();
                            try {
                                java.io.File f = new java.io.File(photoPath);
                                if (f.exists()) ts = f.lastModified();
                            } catch (Exception ignore) {}
                            tvCaptureTime.setVisibility(View.VISIBLE);
                            tvCaptureTime.setText("拍摄时间: " + android.text.format.DateFormat.format("yyyy-MM-dd HH:mm:ss", ts));
                        }
                    }
                }
            });
        } else {
            Log.w(TAG, "照片路径为空，无法显示");
        }
    }
    
    // 检查服务状态
    private void checkServiceStatus() {
        // 这里可以添加检查服务是否正在运行的逻辑
        // 简单起见，我们假设服务未运行
        updateUI(false);
    }
}