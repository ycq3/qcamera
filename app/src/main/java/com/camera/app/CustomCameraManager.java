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
    private Size previewSize;
    
    private CaptureCallback captureCallback;
    
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
        CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        try {
            cameraId = manager.getCameraIdList()[0]; // 使用后置摄像头
            
            // 选择合适的预览尺寸
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            
            if (map != null) {
                Size[] sizes = map.getOutputSizes(ImageFormat.JPEG);
                previewSize = chooseOptimalSize(sizes, 1920, 1080); // 默认使用1080p
                
                // 初始化ImageReader
                imageReader = ImageReader.newInstance(
                        previewSize.getWidth(), 
                        previewSize.getHeight(),
                        ImageFormat.JPEG, 
                        2);
                imageReader.setOnImageAvailableListener(onImageAvailableListener, backgroundHandler);
            }
            
            manager.openCamera(cameraId, stateCallback, backgroundHandler);
        } catch (CameraAccessException e) {
            Log.e(TAG, "无法访问相机", e);
        }
    }
    
    public void closeCamera() {
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
        if (cameraDevice == null) return;
        
        try {
            final CaptureRequest.Builder captureBuilder =
                    cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(imageReader.getSurface());
            
            // 设置自动对焦
            captureBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
            
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
                    Log.e(TAG, "拍照失败");
                    if (captureCallback != null) {
                        captureCallback.onCaptureError(new Exception("拍照失败"));
                    }
                }
            };
            
            captureSession.stopRepeating();
            captureSession.abortCaptures();
            captureSession.capture(captureBuilder.build(), captureListener, backgroundHandler);
        } catch (CameraAccessException e) {
            Log.e(TAG, "拍照时发生错误", e);
            if (captureCallback != null) {
                captureCallback.onCaptureError(e);
            }
        }
    }
    
    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(CameraDevice camera) {
            cameraDevice = camera;
            createCameraPreview();
        }
        
        @Override
        public void onDisconnected(CameraDevice camera) {
            cameraDevice.close();
            cameraDevice = null;
        }
        
        @Override
        public void onError(CameraDevice camera, int error) {
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
}