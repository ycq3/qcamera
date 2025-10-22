package com.camera.app;

import android.Manifest;
import android.app.KeyguardManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.MeteringRectangle;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Size;
import android.view.MotionEvent;
import android.view.Surface;
import android.graphics.SurfaceTexture;
import android.view.TextureView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class CameraActivity extends AppCompatActivity {
    private static final String TAG = "CameraActivity";
    private static final int PERMISSION_REQUEST_CODE = 200;
    
    private TextureView textureView;
    private ImageButton btnFlash;
    private ImageButton btnClose;
    private Button btnCapture;
    private ImageView focusIndicator;
    
    // 相机相关
    private CameraDevice cameraDevice;
    private CameraCaptureSession captureSession;
    private ImageReader imageReader;
    private Handler backgroundHandler;
    private HandlerThread backgroundThread;
    private String cameraId;
    private Size previewSize;
    private CaptureRequest.Builder previewRequestBuilder;
    
    // 闪光灯状态
    private boolean isFlashOn = false;
    
    // 对焦相关
    private boolean isManualFocusSupported = false;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // 保持屏幕常亮
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        
        // 如果设备锁定，显示在锁屏上
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
            
            KeyguardManager keyguardManager = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
            if (keyguardManager != null) {
                keyguardManager.requestDismissKeyguard(this, null);
            }
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD |
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        }
        
        setContentView(R.layout.activity_camera);
        
        initViews();
        setupListeners();
        
        // 检查权限
        if (checkPermissions()) {
            startBackgroundThread();
        } else {
            requestPermissions();
        }
    }
    
    private void initViews() {
        textureView = findViewById(R.id.textureView);
        btnFlash = findViewById(R.id.btnFlash);
        btnClose = findViewById(R.id.btnClose);
        btnCapture = findViewById(R.id.btnCapture);
        focusIndicator = findViewById(R.id.focusIndicator);
    }
    
    private void setupListeners() {
        // 纹理视图表面纹理监听器
        textureView.setSurfaceTextureListener(surfaceTextureListener);
        
        // 拍照按钮点击事件
        btnCapture.setOnClickListener(v -> takePicture());
        
        // 闪光灯按钮点击事件
        btnFlash.setOnClickListener(v -> toggleFlash());
        
        // 关闭按钮点击事件
        btnClose.setOnClickListener(v -> finish());
        
        // 触摸对焦
        textureView.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP && isManualFocusSupported) {
                focusOnTouch(event.getX(), event.getY());
                return true;
            }
            return false;
        });
    }
    
    private final TextureView.SurfaceTextureListener surfaceTextureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(android.graphics.SurfaceTexture surface, int width, int height) {
            openCamera(width, height);
        }
        
        @Override
        public void onSurfaceTextureSizeChanged(android.graphics.SurfaceTexture surface, int width, int height) {
            // 忽略
        }
        
        @Override
        public boolean onSurfaceTextureDestroyed(android.graphics.SurfaceTexture surface) {
            return true;
        }
        
        @Override
        public void onSurfaceTextureUpdated(android.graphics.SurfaceTexture surface) {
            // 忽略
        }
    };
    
    private boolean checkPermissions() {
        return ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }
    
    private void requestPermissions() {
        ActivityCompat.requestPermissions(this,
                new String[]{
                        Manifest.permission.CAMERA,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                },
                PERMISSION_REQUEST_CODE);
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            
            if (allGranted) {
                startBackgroundThread();
            } else {
                Toast.makeText(this, "缺少必要权限，无法使用相机功能", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }
    
    private void startBackgroundThread() {
        backgroundThread = new HandlerThread("CameraBackground");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
        
        // 如果纹理视图已经准备好了，就打开相机
        if (textureView.isAvailable()) {
            openCamera(textureView.getWidth(), textureView.getHeight());
        }
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
    
    private void openCamera(int width, int height) {
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            cameraId = manager.getCameraIdList()[0]; // 使用后置摄像头
            
            // 检查是否支持手动对焦
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
            Integer supportedFocusModes = characteristics.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AF);
            isManualFocusSupported = supportedFocusModes != null && supportedFocusModes > 0;
            
            // 选择合适的预览尺寸
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            if (map != null) {
                Size[] sizes = map.getOutputSizes(Surface.class);
                previewSize = chooseOptimalSize(sizes, width, height);
                
                // 初始化ImageReader
                imageReader = ImageReader.newInstance(
                        previewSize.getWidth(),
                        previewSize.getHeight(),
                        android.graphics.ImageFormat.JPEG,
                        2);
                imageReader.setOnImageAvailableListener(onImageAvailableListener, backgroundHandler);
            }
            
            // 打开相机
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            manager.openCamera(cameraId, stateCallback, backgroundHandler);
        } catch (CameraAccessException e) {
            Log.e(TAG, "无法访问相机", e);
        } catch (Exception e) {
            Log.e(TAG, "打开相机时发生未知错误", e);
        }
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
            Toast.makeText(CameraActivity.this, "打开相机失败: " + error, Toast.LENGTH_SHORT).show();
            finish();
        }
    };
    
    private void createCameraPreview() {
        try {
            SurfaceTexture texture = textureView.getSurfaceTexture();
            assert texture != null;
            texture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
            Surface surface = new Surface(texture);
            
            previewRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            previewRequestBuilder.addTarget(surface);
            
            // 设置自动对焦
            previewRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
            
            cameraDevice.createCaptureSession(Arrays.asList(surface, imageReader.getSurface()),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession session) {
                            if (cameraDevice == null) return;
                            
                            captureSession = session;
                            try {
                                // 设置连续自动对焦
                                previewRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
                                previewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                                
                                captureSession.setRepeatingRequest(previewRequestBuilder.build(), null, backgroundHandler);
                            } catch (CameraAccessException e) {
                                Log.e(TAG, "配置预览失败", e);
                            }
                        }
                        
                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                            Toast.makeText(CameraActivity.this, "配置相机预览失败", Toast.LENGTH_SHORT).show();
                        }
                    }, backgroundHandler);
        } catch (CameraAccessException e) {
            Log.e(TAG, "创建预览失败", e);
        }
    }
    
    private void takePicture() {
        if (cameraDevice == null) return;
        
        try {
            final CaptureRequest.Builder captureBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(imageReader.getSurface());
            
            // 设置自动对焦
            captureBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
            captureBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            
            // 设置闪光灯
            if (isFlashOn) {
                captureBuilder.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_SINGLE);
            } else {
                captureBuilder.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_OFF);
            }
            
            // 方向
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, 90);
            
            CameraCaptureSession.CaptureCallback captureListener = new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull android.hardware.camera2.TotalCaptureResult result) {
                    super.onCaptureCompleted(session, request, result);
                    // 拍照完成后重新开始预览
                    startPreviewAgain();
                }
            };
            
            captureSession.stopRepeating();
            captureSession.abortCaptures();
            captureSession.capture(captureBuilder.build(), captureListener, backgroundHandler);
        } catch (CameraAccessException e) {
            Log.e(TAG, "拍照时发生错误", e);
        }
    }
    
    private void startPreviewAgain() {
        try {
            previewRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
            previewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            
            captureSession.setRepeatingRequest(previewRequestBuilder.build(), null, backgroundHandler);
        } catch (CameraAccessException e) {
            Log.e(TAG, "重新开始预览失败", e);
        }
    }
    
    private final ImageReader.OnImageAvailableListener onImageAvailableListener = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
            backgroundHandler.post(new ImageSaver(reader.acquireNextImage()));
        }
    };
    
    private class ImageSaver implements Runnable {
        private final Image image;
        
        public ImageSaver(Image image) {
            this.image = image;
        }
        
        @Override
        public void run() {
            ByteBuffer buffer = image.getPlanes()[0].getBuffer();
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            
            File file = createImageFile();
            FileOutputStream output = null;
            try {
                output = new FileOutputStream(file);
                output.write(bytes);
                
                // 在主线程中发送通知
                runOnUiThread(() -> {
                    sendNotification(file.getName());
                    Toast.makeText(CameraActivity.this, "照片已保存: " + file.getName(), Toast.LENGTH_SHORT).show();
                });
            } catch (IOException e) {
                Log.e(TAG, "保存图片失败", e);
            } finally {
                image.close();
                if (output != null) {
                    try {
                        output.close();
                    } catch (IOException e) {
                        Log.e(TAG, "关闭输出流失败", e);
                    }
                }
            }
        }
    }
    
    private File createImageFile() {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String imageFileName = "IMG_" + timeStamp + ".jpg";
        File storageDir = getExternalFilesDir(null);
        return new File(storageDir, imageFileName);
    }
    
    private void sendNotification(String fileName) {
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        
        // 创建通知渠道 (Android 8.0+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    "photo_capture_channel",
                    "照片拍摄",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            notificationManager.createNotificationChannel(channel);
        }
        
        // 创建通知
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, "photo_capture_channel")
                .setSmallIcon(R.drawable.ic_camera)
                .setContentTitle("照片已保存")
                .setContentText("照片 " + fileName + " 已成功保存")
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);
        
        // 添加点击意图
        Intent intent = new Intent(this, PhotoGalleryActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        builder.setContentIntent(pendingIntent);
        
        // 发送通知
        notificationManager.notify((int) System.currentTimeMillis(), builder.build());
    }
    
    private void toggleFlash() {
        isFlashOn = !isFlashOn;
        btnFlash.setImageResource(isFlashOn ? R.drawable.ic_flash_on : R.drawable.ic_flash_off);
        
        // 如果预览正在运行，更新预览请求
        if (previewRequestBuilder != null && captureSession != null) {
            try {
                previewRequestBuilder.set(CaptureRequest.FLASH_MODE, 
                        isFlashOn ? CameraMetadata.FLASH_MODE_TORCH : CameraMetadata.FLASH_MODE_OFF);
                captureSession.setRepeatingRequest(previewRequestBuilder.build(), null, backgroundHandler);
            } catch (CameraAccessException e) {
                Log.e(TAG, "切换闪光灯失败", e);
            }
        }
    }
    
    private void focusOnTouch(float x, float y) {
        if (cameraDevice == null || previewRequestBuilder == null) return;
        
        try {
            // 显示对焦指示器
            runOnUiThread(() -> {
                focusIndicator.setVisibility(View.VISIBLE);
                focusIndicator.setX(x - focusIndicator.getWidth() / 2);
                focusIndicator.setY(y - focusIndicator.getHeight() / 2);
                
                // 2秒后隐藏对焦指示器
                focusIndicator.postDelayed(() -> focusIndicator.setVisibility(View.GONE), 2000);
            });
            
            // 计算对焦区域
            int areaSize = 200;
            int halfAreaSize = areaSize / 2;
            
            int touchX = (int) x;
            int touchY = (int) y;
            
            // 转换为相机坐标系
            MeteringRectangle focusArea = new MeteringRectangle(
                    Math.max(touchX - halfAreaSize, 0),
                    Math.max(touchY - halfAreaSize, 0),
                    areaSize,
                    areaSize,
                    MeteringRectangle.METERING_WEIGHT_MAX - 1
            );
            
            // 设置对焦模式
            previewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO);
            previewRequestBuilder.set(CaptureRequest.CONTROL_AF_REGIONS, new MeteringRectangle[]{focusArea});
            
            // 触发对焦
            previewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START);
            
            // 应用对焦请求
            captureSession.capture(previewRequestBuilder.build(), null, backgroundHandler);
            
            // 重置对焦触发器
            previewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_IDLE);
            captureSession.setRepeatingRequest(previewRequestBuilder.build(), null, backgroundHandler);
        } catch (CameraAccessException e) {
            Log.e(TAG, "手动对焦失败", e);
        }
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
    
    @Override
    protected void onResume() {
        super.onResume();
        startBackgroundThread();
        
        if (textureView.isAvailable()) {
            openCamera(textureView.getWidth(), textureView.getHeight());
        } else {
            textureView.setSurfaceTextureListener(surfaceTextureListener);
        }
    }
    
    @Override
    protected void onPause() {
        closeCamera();
        stopBackgroundThread();
        super.onPause();
    }
    
    private void closeCamera() {
        if (captureSession != null) {
            captureSession.close();
            captureSession = null;
        }
        if (cameraDevice != null) {
            cameraDevice.close();
            cameraDevice = null;
        }
        if (imageReader != null) {
            imageReader.close();
            imageReader = null;
        }
    }
    
    @Override
    protected void onDestroy() {
        closeCamera();
        stopBackgroundThread();
        super.onDestroy();
    }
}