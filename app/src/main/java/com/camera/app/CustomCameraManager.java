package com.camera.app;

import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Size;
import android.view.Surface;

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

public class CustomCameraManager {
    
    private static final String TAG = "CustomCameraManager";
    
    private Context context;
    private CameraDevice cameraDevice;
    private CameraCaptureSession captureSession;
    private ImageReader imageReader;
    private Handler backgroundHandler;
    private HandlerThread backgroundThread;
    private String cameraId;
    private Size previewSize; // 实际使用的预览尺寸
    private Size captureSize; // 实际使用的拍照尺寸
    private String[] cameraIds; // 存储所有摄像头ID
    
    private CaptureCallback captureCallback;
    private boolean isCameraOpened = false;
    private int selectedCameraIndex = 0; // 默认选择第一个摄像头
    private boolean isFlashEnabled = false; // 闪光灯设置
    
    public interface CaptureCallback {
        void onCaptureSuccess(String imagePath);
        void onCaptureError(Exception e);
    }
    
    public CustomCameraManager(Context context) {
        this.context = context;
    }
    
    public void setCaptureCallback(CaptureCallback callback) {
        this.captureCallback = callback;
    }
    
    // 设置选择的摄像头索引
    public void setSelectedCameraIndex(int index) {
        this.selectedCameraIndex = index;
    }
    
    // 设置闪光灯状态
    public void setFlashEnabled(boolean enabled) {
        this.isFlashEnabled = enabled;
    }
    
    // 获取闪光灯状态
    public boolean isFlashEnabled() {
        return this.isFlashEnabled;
    }
    
    // 获取所有可用的摄像头ID
    public String[] getAvailableCameraIds() throws CameraAccessException {
        CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        return manager.getCameraIdList();
    }
    
    // 获取摄像头方向信息
    public int getCameraOrientation(String cameraId) throws CameraAccessException {
        CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
        return characteristics.get(CameraCharacteristics.LENS_FACING);
    }
    
    // 获取摄像头支持的最大分辨率
    public Size getMaxResolution(String cameraId) throws CameraAccessException {
        CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
        StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        
        if (map != null) {
            Size[] sizes = map.getOutputSizes(ImageFormat.JPEG);
            // 按面积排序，获取最大分辨率
            return Collections.max(Arrays.asList(sizes), new CompareSizesByArea());
        }
        return new Size(1920, 1080); // 默认分辨率
    }
    
    public boolean isCameraOpened() {
        return isCameraOpened;
    }
    
    public void startBackgroundThread() {
        backgroundThread = new HandlerThread("CameraBackground");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
    }
    
    public void stopBackgroundThread() {
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
    
    public void openCamera() {
        isCameraOpened = false;
        CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        try {
            // 获取所有摄像头ID
            cameraIds = manager.getCameraIdList();
            
            // 检查选中的摄像头索引是否有效
            if (selectedCameraIndex >= cameraIds.length) {
                selectedCameraIndex = 0; // 回退到默认摄像头
            }
            
            cameraId = cameraIds[selectedCameraIndex]; // 使用选中的摄像头
            Log.d(TAG, "尝试打开相机 ID: " + cameraId);
            
            // 获取摄像头支持的最大分辨率
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            
            if (map != null) {
                // 获取预览支持的尺寸
                Size[] previewSizes = map.getOutputSizes(SurfaceTexture.class);
                previewSize = chooseOptimalPreviewSize(previewSizes, 1920, 1080);
                Log.d(TAG, "选择预览尺寸: " + previewSize.getWidth() + "x" + previewSize.getHeight());
                
                // 获取拍照支持的最大尺寸
                Size[] captureSizes = map.getOutputSizes(ImageFormat.JPEG);
                captureSize = Collections.max(Arrays.asList(captureSizes), new CompareSizesByArea());
                Log.d(TAG, "选择拍照尺寸: " + captureSize.getWidth() + "x" + captureSize.getHeight());
                
                // 初始化ImageReader，使用拍照的最大分辨率
                imageReader = ImageReader.newInstance(
                        captureSize.getWidth(), 
                        captureSize.getHeight(),
                        ImageFormat.JPEG, 
                        2);
                imageReader.setOnImageAvailableListener(onImageAvailableListener, backgroundHandler);
            }
            
            manager.openCamera(cameraId, stateCallback, backgroundHandler);
            Log.d(TAG, "已调用openCamera方法");
        } catch (CameraAccessException e) {
            Log.e(TAG, "无法访问相机", e);
        } catch (Exception e) {
            Log.e(TAG, "打开相机时发生未知错误", e);
        }
    }
    
    public void closeCamera() {
        isCameraOpened = false;
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
    
    public void takePicture() {
        if (cameraDevice == null) {
            Log.e(TAG, "相机未打开");
            if (captureCallback != null) {
                captureCallback.onCaptureError(new Exception("相机未打开"));
            }
            return;
        }
        
        Log.d(TAG, "开始拍照");
        try {
            final CaptureRequest.Builder captureBuilder =
                    cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(imageReader.getSurface());
            
            // 设置自动对焦
            captureBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
            
            // 设置闪光灯状态
            if (isFlashEnabled) {
                captureBuilder.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_SINGLE);
            } else {
                captureBuilder.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_OFF);
            }
            
            // 方向
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, 90);
            
            CameraCaptureSession.CaptureCallback captureListener = new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
                    super.onCaptureCompleted(session, request, result);
                    Log.d(TAG, "拍照完成");
                }
                
                @Override
                public void onCaptureFailed(CameraCaptureSession session, CaptureRequest request, CaptureFailure failure) {
                    super.onCaptureFailed(session, request, failure);
                    Log.e(TAG, "拍照失败: " + failure.getReason());
                    if (captureCallback != null) {
                        captureCallback.onCaptureError(new Exception("拍照失败: " + failure.getReason()));
                    }
                }
            };
            
            captureSession.stopRepeating();
            captureSession.abortCaptures();
            captureSession.capture(captureBuilder.build(), captureListener, backgroundHandler);
            Log.d(TAG, "已发送拍照请求");
        } catch (CameraAccessException e) {
            Log.e(TAG, "拍照时发生错误", e);
            if (captureCallback != null) {
                captureCallback.onCaptureError(e);
            }
        } catch (Exception e) {
            Log.e(TAG, "拍照时发生未知错误", e);
            if (captureCallback != null) {
                captureCallback.onCaptureError(e);
            }
        }
    }
    
    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(CameraDevice camera) {
            cameraDevice = camera;
            isCameraOpened = true;
            Log.d(TAG, "相机已打开成功");
            createCameraPreview();
            Log.d(TAG, "相机预览已创建");
        }
        
        @Override
        public void onDisconnected(CameraDevice camera) {
            isCameraOpened = false;
            cameraDevice.close();
            cameraDevice = null;
            Log.d(TAG, "相机已断开连接");
        }
        
        @Override
        public void onError(CameraDevice camera, int error) {
            isCameraOpened = false;
            cameraDevice.close();
            cameraDevice = null;
            Log.e(TAG, "打开相机错误: " + error);
        }
    };
    
    private void createCameraPreview() {
        try {
            SurfaceTexture texture = new SurfaceTexture(0);
            texture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
            Surface surface = new Surface(texture);
            
            final CaptureRequest.Builder previewBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            previewBuilder.addTarget(surface);
            
            // 设置预览时的闪光灯状态（仅在需要持续闪光时使用）
            if (isFlashEnabled) {
                previewBuilder.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_TORCH);
            }
            
            cameraDevice.createCaptureSession(Arrays.asList(surface, imageReader.getSurface()),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(CameraCaptureSession session) {
                            if (cameraDevice == null) return;
                            
                            captureSession = session;
                            try {
                                previewBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
                                captureSession.setRepeatingRequest(previewBuilder.build(), null, backgroundHandler);
                            } catch (CameraAccessException e) {
                                Log.e(TAG, "配置预览失败", e);
                            }
                        }
                        
                        @Override
                        public void onConfigureFailed(CameraCaptureSession session) {
                            Log.e(TAG, "配置失败");
                        }
                    }, backgroundHandler);
        } catch (CameraAccessException e) {
            Log.e(TAG, "创建预览失败", e);
        }
    }
    
    private final ImageReader.OnImageAvailableListener onImageAvailableListener
            = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
            Log.d(TAG, "图像数据已准备好");
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
            Log.d(TAG, "开始保存图片");
            ByteBuffer buffer = image.getPlanes()[0].getBuffer();
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            
            File file = createImageFile();
            Log.d(TAG, "创建图片文件: " + file.getAbsolutePath());
            FileOutputStream output = null;
            try {
                output = new FileOutputStream(file);
                output.write(bytes);
                Log.d(TAG, "图片保存成功");
                
                // 通知回调拍照成功
                if (captureCallback != null) {
                    captureCallback.onCaptureSuccess(file.getAbsolutePath());
                }
            } catch (IOException e) {
                Log.e(TAG, "保存图片失败", e);
                if (captureCallback != null) {
                    captureCallback.onCaptureError(e);
                }
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
        // 创建图片文件
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String imageFileName = "IMG_" + timeStamp + ".jpg";
        File storageDir = context.getExternalFilesDir(null);
        return new File(storageDir, imageFileName);
    }
    
    // 选择最优预览尺寸
    private Size chooseOptimalPreviewSize(Size[] choices, int width, int height) {
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
}