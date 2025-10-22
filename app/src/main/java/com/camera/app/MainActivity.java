package com.camera.app;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
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
    
    private Button btnStartStop;
    private Button btnSettings;
    private Button btnGallery;
    private Button btnFlashToggle; // 闪光灯切换按钮（文本按钮）
    private Button btnSelectCamera; // 选择摄像头按钮
    private TextView tvStatus;
    private ImageView appIcon; // 应用图标
    private LinearLayout appIconContainer; // 图标容器
    private boolean isRunning = false;
    
    // 相机预览相关
    private FrameLayout cameraPreviewContainer;
    private TextureView textureView;
    private ImageButton btnFlashIndicator; // 闪光灯指示器（图标）
    private CameraDevice cameraDevice;
    private CameraCaptureSession captureSession;
    private Handler backgroundHandler;
    private HandlerThread backgroundThread;
    private String cameraId;
    private Size previewSize;
    private CaptureRequest.Builder previewRequestBuilder;
    private boolean isFlashOn = false; // 仅用于预览时的闪光灯指示
    private boolean isFlashEnabledForCapture = false; // 用于拍照时的闪光灯设置
    private boolean isPreviewShowing = false;
    
    // 多摄像头支持
    private String[] cameraIds;
    private int currentCameraIndex = 0;
    private String[] cameraNames; // 摄像头名称
    
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
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
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
        
        // 检查必要权限
        checkPermissions();
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
        
        // 初始化相机预览相关视图
        cameraPreviewContainer = findViewById(R.id.camera_preview_container);
        textureView = findViewById(R.id.texture_view);
        btnFlashIndicator = findViewById(R.id.btn_flash_indicator);
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
        // 检查相机权限
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) 
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions();
            return false;
        }
        
        // 检查存储权限
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) 
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions();
            return false;
        }
        
        // 检查读取存储权限
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) 
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions();
            return false;
        }
        
        return true;
    }
    
    private void requestPermissions() {
        String[] permissions;
        
        // 注意：TIRAMISU是在Android 13中引入的，我们的compileSdk是32，所以不使用这个常量
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
        
        ActivityCompat.requestPermissions(this, permissions, PERMISSION_REQUEST_CODE);
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
    
    // 安全地切换摄像头
    private void switchCameraSafely() {
        // 在后台线程中执行摄像头切换
        if (backgroundHandler != null) {
            backgroundHandler.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        // 关闭当前相机
                        closeCamera();
                        
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
    
    // 切换闪光灯设置（用于拍照）
    private void toggleFlashSetting() {
        isFlashEnabledForCapture = !isFlashEnabledForCapture;
        btnFlashToggle.setText(isFlashEnabledForCapture ? "闪光灯: 开" : "闪光灯: 关");
        
        // 更新预览时的闪光灯指示器
        updateFlashIndicator();
        
        Toast.makeText(this, "拍照时闪光灯设置: " + (isFlashEnabledForCapture ? "开启" : "关闭"), Toast.LENGTH_SHORT).show();
    }
    
    // 更新预览时的闪光灯指示器
    private void updateFlashIndicator() {
        if (btnFlashIndicator != null) {
            btnFlashIndicator.setImageResource(isFlashEnabledForCapture ? R.drawable.ic_flash_on : R.drawable.ic_flash_off);
        }
    }
    
    private void startCapture() {
        // 显示相机预览
        showCameraPreview();
        
        // 启动拍照服务
        Intent serviceIntent = new Intent(this, CameraService.class);
        // 传递摄像头索引和闪光灯设置给服务
        serviceIntent.putExtra("cameraIndex", currentCameraIndex);
        serviceIntent.putExtra("flashEnabled", isFlashEnabledForCapture);
        serviceIntent.setAction(CameraService.ACTION_START_CAPTURE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
        
        // 更新UI状态
        updateUI(true);
    }
    
    private void stopCapture() {
        // 隐藏相机预览
        hideCameraPreview();
        
        // 停止拍照服务
        Intent serviceIntent = new Intent(this, CameraService.class);
        serviceIntent.setAction(CameraService.ACTION_STOP_CAPTURE);
        startService(serviceIntent);
        
        // 更新UI状态
        updateUI(false);
    }
    
    private void updateUI(boolean running) {
        isRunning = running;
        btnStartStop.setText(running ? R.string.stop_capture : R.string.start_capture);
        tvStatus.setText(running ? R.string.status_running : R.string.status_stopped);
        
        // 根据运行状态控制图标显示
        if (running) {
            // 运行时隐藏图标容器
            appIconContainer.setVisibility(View.GONE);
            // 显示摄像头选择按钮
            btnSelectCamera.setVisibility(View.VISIBLE);
            // 显示闪光灯设置按钮
            btnFlashToggle.setVisibility(View.VISIBLE);
        } else {
            // 停止时显示图标容器
            appIconContainer.setVisibility(View.VISIBLE);
            // 隐藏摄像头选择按钮
            btnSelectCamera.setVisibility(View.GONE);
            // 隐藏闪光灯设置按钮
            btnFlashToggle.setVisibility(View.GONE);
        }
    }
    
    // 相机预览相关方法
    private void showCameraPreview() {
        cameraPreviewContainer.setVisibility(View.VISIBLE);
        appIconContainer.setVisibility(View.GONE); // 隐藏图标容器
        btnSelectCamera.setVisibility(View.VISIBLE); // 显示摄像头选择按钮
        btnFlashToggle.setVisibility(View.VISIBLE); // 显示闪光灯设置按钮
        
        // 更新摄像头选择按钮文本
        if (cameraNames != null && cameraNames.length > currentCameraIndex) {
            btnSelectCamera.setText(cameraNames[currentCameraIndex]);
        }
        
        isPreviewShowing = true;
        startBackgroundThread();
        
        // 如果纹理视图已经准备好了，就打开相机
        if (textureView.isAvailable()) {
            openCamera(textureView.getWidth(), textureView.getHeight());
        } else {
            textureView.setSurfaceTextureListener(surfaceTextureListener);
        }
    }
    
    private void hideCameraPreview() {
        cameraPreviewContainer.setVisibility(View.GONE);
        appIconContainer.setVisibility(View.VISIBLE); // 显示图标容器
        btnSelectCamera.setVisibility(View.GONE); // 隐藏摄像头选择按钮
        btnFlashToggle.setVisibility(View.GONE); // 隐藏闪光灯设置按钮
        isPreviewShowing = false;
        closeCamera();
        stopBackgroundThread();
    }
    
    private void startBackgroundThread() {
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
            // 忽略
        }
        
        @Override
        public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surface) {
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
                CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
                try {
                    // 获取所有摄像头ID
                    if (cameraIds == null || cameraIds.length == 0) {
                        initializeCameras();
                    }
                    
                    // 检查选中的摄像头索引是否有效
                    if (currentCameraIndex >= cameraIds.length) {
                        currentCameraIndex = 0; // 回退到默认摄像头
                    }
                    
                    cameraId = cameraIds[currentCameraIndex]; // 使用选中的摄像头
                    Log.d(TAG, "尝试打开相机 ID: " + cameraId);
                    
                    // 选择合适的预览尺寸
                    CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
                    StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                    if (map != null) {
                        Size[] sizes = map.getOutputSizes(SurfaceTexture.class);
                        // 获取最大分辨率作为预览尺寸
                        previewSize = getMaxResolution(cameraId);
                    }
                    
                    // 打开相机
                    if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                        Log.e(TAG, "缺少相机权限");
                        return;
                    }
                    
                    // 确保在主线程中更新UI
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                manager.openCamera(cameraId, stateCallback, backgroundHandler);
                            } catch (CameraAccessException e) {
                                Log.e(TAG, "无法访问相机", e);
                                Toast.makeText(MainActivity.this, "无法访问相机: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                            } catch (Exception e) {
                                Log.e(TAG, "打开相机时发生未知错误", e);
                                Toast.makeText(MainActivity.this, "打开相机失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
                } catch (Exception e) {
                    Log.e(TAG, "初始化相机时发生错误", e);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(MainActivity.this, "初始化相机失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        });
    }
    
    // 获取摄像头支持的最大分辨率
    private Size getMaxResolution(String cameraId) throws CameraAccessException {
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
        StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        
        if (map != null) {
            Size[] sizes = map.getOutputSizes(SurfaceTexture.class);
            // 按面积排序，获取最大分辨率
            return Collections.max(Arrays.asList(sizes), new CompareSizesByArea());
        }
        return new Size(1920, 1080); // 默认分辨率
    }
    
    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            cameraDevice = camera;
            createCameraPreview();
        }
        
        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            cameraDevice.close();
            cameraDevice = null;
        }
        
        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            cameraDevice.close();
            cameraDevice = null;
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(MainActivity.this, "打开相机失败: 错误代码 " + error, Toast.LENGTH_SHORT).show();
                }
            });
        }
    };
    
    private void createCameraPreview() {
        // 确保在主线程中执行UI操作
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    SurfaceTexture texture = textureView.getSurfaceTexture();
                    if (texture == null) {
                        Log.e(TAG, "SurfaceTexture为空");
                        return;
                    }
                    
                    assert texture != null;
                    texture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
                    Surface surface = new Surface(texture);
                    
                    if (cameraDevice == null) {
                        Log.e(TAG, "相机设备为空");
                        return;
                    }
                    
                    previewRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                    previewRequestBuilder.addTarget(surface);
                    
                    // 设置自动对焦
                    previewRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
                    previewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                    
                    // 设置预览时的闪光灯状态
                    if (isFlashEnabledForCapture) {
                        previewRequestBuilder.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_TORCH);
                    }
                    
                    cameraDevice.createCaptureSession(Arrays.asList(surface),
                            new CameraCaptureSession.StateCallback() {
                                @Override
                                public void onConfigured(@NonNull CameraCaptureSession session) {
                                    if (cameraDevice == null) return;
                                    
                                    captureSession = session;
                                    try {
                                        captureSession.setRepeatingRequest(previewRequestBuilder.build(), null, backgroundHandler);
                                    } catch (CameraAccessException e) {
                                        Log.e(TAG, "配置预览失败", e);
                                    }
                                }
                                
                                @Override
                                public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                                    runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            Toast.makeText(MainActivity.this, "配置相机预览失败", Toast.LENGTH_SHORT).show();
                                        }
                                    });
                                }
                            }, backgroundHandler);
                } catch (Exception e) {
                    Log.e(TAG, "创建预览失败", e);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(MainActivity.this, "创建预览失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        });
    }
    
    private Size chooseOptimalSize(Size[] choices, int width, int height) {
        List<Size> bigEnough = new ArrayList<>();
        for (Size option : choices) {
            if (option.getHeight() == option.getWidth() * height / width &&
                    option.getWidth() >= width && option.getHeight() >= height) {
                bigEnough.add(option);
            }
        }
        
        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSizesByArea());
        } else {
            return choices[0];
        }
    }
    
    private static class CompareSizesByArea implements Comparator<Size> {
        @Override
        public int compare(Size lhs, Size rhs) {
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
                    (long) rhs.getWidth() * rhs.getHeight());
        }
    }
    
    private void closeCamera() {
        try {
            if (captureSession != null) {
                captureSession.close();
                captureSession = null;
            }
            if (cameraDevice != null) {
                cameraDevice.close();
                cameraDevice = null;
            }
        } catch (Exception e) {
            Log.e(TAG, "关闭相机时发生错误", e);
        }
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        // 如果服务正在运行，显示预览
        if (isRunning) {
            showCameraPreview();
        }
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        // 隐藏预览但不关闭相机，因为服务可能仍在运行
        if (isPreviewShowing) {
            cameraPreviewContainer.setVisibility(View.GONE);
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        closeCamera();
        stopBackgroundThread();
        unregisterReceiver(serviceStatusReceiver);
    }
}