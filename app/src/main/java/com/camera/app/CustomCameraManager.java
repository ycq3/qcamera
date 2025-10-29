package com.camera.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.media.MediaScannerConnection; // 添加媒体扫描连接导入

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
    private boolean isFlashEnabled = false; // 兼容旧逻辑的布尔标记
    private FlashMode flashMode = FlashMode.OFF; // 三态闪光灯模式
    
    // 添加TextureView引用
    private TextureView textureView;
    
    // 添加拍照状态标志
    private boolean isCapturing = false;
    
    // 添加预览显示回调接口
    private PreviewDisplayCallback previewDisplayCallback;
    
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
    
    // 设置TextureView
    public void setTextureView(TextureView textureView) {
        this.textureView = textureView;
    }
    
    // 设置选择的摄像头索引
    public void setSelectedCameraIndex(int index) {
        this.selectedCameraIndex = index;
    }
    
    // 设置闪光灯状态
    public void setFlashEnabled(boolean enabled) {
        this.isFlashEnabled = enabled;
        this.flashMode = enabled ? FlashMode.ON : FlashMode.OFF;
    }
    
    // 获取闪光灯状态
    public boolean isFlashEnabled() {
        return this.isFlashEnabled;
    }

    // 设置三态闪光灯模式
    public void setFlashMode(FlashMode mode) {
        this.flashMode = mode == null ? FlashMode.OFF : mode;
        // 同步旧布尔标记，仅用于日志/兼容：ON 为 true，其它为 false
        this.isFlashEnabled = (this.flashMode == FlashMode.ON);
    }

    public FlashMode getFlashMode() {
        return this.flashMode;
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
        isCapturing = false; // 重置拍照状态
        CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        try {
            Log.d(TAG, "开始打开相机");
            
            // 获取所有摄像头ID
            cameraIds = manager.getCameraIdList();
            Log.d(TAG, "找到 " + cameraIds.length + " 个摄像头");
            
            // 检查选中的摄像头索引是否有效
            if (selectedCameraIndex >= cameraIds.length) {
                selectedCameraIndex = 0; // 回退到默认摄像头
                Log.w(TAG, "摄像头索引超出范围，回退到默认摄像头");
            }
            
            cameraId = cameraIds[selectedCameraIndex]; // 使用选中的摄像头
            Log.d(TAG, "尝试打开相机 ID: " + cameraId + ", 闪光模式: " + flashMode);
            
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
                if (imageReader != null) {
                    imageReader.close();
                }
                imageReader = ImageReader.newInstance(
                        captureSize.getWidth(), 
                        captureSize.getHeight(),
                        ImageFormat.JPEG, 
                        2);
                imageReader.setOnImageAvailableListener(onImageAvailableListener, backgroundHandler);
                Log.d(TAG, "ImageReader初始化完成");
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
        isCapturing = false; // 重置拍照状态
        if (captureSession != null) {
            try {
                captureSession.close();
            } catch (Exception e) {
                Log.e(TAG, "关闭captureSession时出错", e);
            }
            captureSession = null;
        }
        if (cameraDevice != null) {
            try {
                cameraDevice.close();
            } catch (Exception e) {
                Log.e(TAG, "关闭cameraDevice时出错", e);
            }
            cameraDevice = null;
        }
        if (imageReader != null) {
            try {
                imageReader.close();
            } catch (Exception e) {
                Log.e(TAG, "关闭imageReader时出错", e);
            }
            imageReader = null;
        }
    }
    
    public void takePicture() {
        if (isCapturing) {
            Log.w(TAG, "拍照已在进行中");
            return;
        }

        if (cameraDevice == null) {
            Log.e(TAG, "相机未打开");
            if (captureCallback != null) {
                captureCallback.onCaptureError(new Exception("相机未打开"));
            }
            return;
        }

        // 检查captureSession是否已创建
        if (captureSession == null) {
            Log.e(TAG, "相机预览会话未创建");
            if (captureCallback != null) {
                captureCallback.onCaptureError(new Exception("相机预览会话未创建"));
            }
            return;
        }

        isCapturing = true;
        Log.d(TAG, "开始拍照 - 闪光模式: " + flashMode);
        try {
            final CaptureRequest.Builder captureBuilder =
                    cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(imageReader.getSurface());

            // 设置自动对焦
            captureBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
            captureBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);

            // 设置闪光灯状态（三态）
            if (flashMode == FlashMode.ON) {
                // 强制闪光：始终在拍照时触发
                captureBuilder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON_ALWAYS_FLASH);
                Log.d(TAG, "拍照闪光模式: ALWAYS_FLASH");
            } else if (flashMode == FlashMode.AUTO) {
                // 自动闪光：由AE判断是否需要
                captureBuilder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON_AUTO_FLASH);
                // 保留单闪以提高部分HAL兼容性
                captureBuilder.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_SINGLE);
                Log.d(TAG, "拍照闪光模式: AUTO_FLASH + SINGLE");
            } else {
                // 关闭闪光
                captureBuilder.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_OFF);
                captureBuilder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON);
                Log.d(TAG, "拍照闪光模式: OFF");
            }

            // 方向
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, 90);

            CameraCaptureSession.CaptureCallback captureListener = new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
                    super.onCaptureCompleted(session, request, result);
                    Log.d(TAG, "拍照完成");
                    isCapturing = false;
                    // 拍照完成后重新启动预览（如果在预览模式下）
                    if (textureView != null) {
                        restartPreview();
                    }
                }

                @Override
                public void onCaptureFailed(CameraCaptureSession session, CaptureRequest request, CaptureFailure failure) {
                    super.onCaptureFailed(session, request, failure);
                    Log.e(TAG, "拍照失败: " + failure.getReason());
                    isCapturing = false;
                    if (textureView != null) {
                        restartPreview();
                    }
                    if (captureCallback != null) {
                        captureCallback.onCaptureError(new Exception("拍照失败: " + failure.getReason()));
                    }
                }

                @Override
                public void onCaptureSequenceCompleted(CameraCaptureSession session, int sequenceId, long frameNumber) {
                    super.onCaptureSequenceCompleted(session, sequenceId, frameNumber);
                    Log.d(TAG, "拍照序列完成");
                }

                @Override
                public void onCaptureSequenceAborted(CameraCaptureSession session, int sequenceId) {
                    super.onCaptureSequenceAborted(session, sequenceId);
                    Log.e(TAG, "拍照序列被中止");
                    isCapturing = false;
                    if (textureView != null) {
                        restartPreview();
                    }
                    if (captureCallback != null) {
                        captureCallback.onCaptureError(new Exception("拍照序列被中止"));
                    }
                }
            };

            // 先停止预览（仅在有预览时）
            if (textureView != null) {
                try {
                    captureSession.stopRepeating();
                    captureSession.abortCaptures();
                } catch (CameraAccessException e) {
                    Log.e(TAG, "停止预览失败", e);
                }
            }

            captureSession.capture(captureBuilder.build(), captureListener, backgroundHandler);
            Log.d(TAG, "已发送拍照请求");
        } catch (CameraAccessException e) {
            Log.e(TAG, "拍照时发生错误", e);
            isCapturing = false;
            if (captureCallback != null) {
                captureCallback.onCaptureError(e);
            }
        } catch (Exception e) {
            Log.e(TAG, "拍照时发生未知错误", e);
            isCapturing = false;
            if (captureCallback != null) {
                captureCallback.onCaptureError(e);
            }
        }
    }
    
    // 拍照完成后重新启动预览
    private void restartPreview() {
        try {
            Log.d(TAG, "尝试重新启动预览");
            if (cameraDevice != null && captureSession != null) {
                // 先停止当前的重复请求
                try {
                    captureSession.stopRepeating();
                } catch (Exception e) {
                    Log.e(TAG, "停止重复请求失败", e);
                }
                
                final CaptureRequest.Builder previewBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                
                SurfaceTexture texture = textureView != null ? textureView.getSurfaceTexture() : null;
                if (texture != null) {
                    // 确保预览尺寸已设置
                    if (previewSize == null) {
                        Log.e(TAG, "预览尺寸未设置");
                        return;
                    }
                    
                    texture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
                    Surface surface = new Surface(texture);
                    previewBuilder.addTarget(surface);
                    
                    // 预览闪光（三态）：ON 使用手电（TORCH），AUTO/OFF 关闭
                    if (flashMode == FlashMode.ON) {
                        previewBuilder.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_TORCH);
                        previewBuilder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON);
                        Log.d(TAG, "预览闪光: TORCH");
                    } else {
                        previewBuilder.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_OFF);
                        previewBuilder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON);
                        Log.d(TAG, "预览闪光: OFF");
                    }
                    
                    previewBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
                    previewBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                    
                    // 使用setRepeatingRequest重新启动预览
                    captureSession.setRepeatingRequest(previewBuilder.build(), null, backgroundHandler);
                    Log.d(TAG, "预览已重新启动");
                    
                    // 确保TextureView可见
                    if (textureView != null) {
                        textureView.post(new Runnable() {
                            @Override
                            public void run() {
                                textureView.setVisibility(android.view.View.VISIBLE);
                            }
                        });
                    }
                } else {
                    Log.w(TAG, "无法重新启动预览：Texture为空");
                    // 尝试重新创建预览
                    if (textureView != null && textureView.isAvailable()) {
                        createCameraPreview();
                    }
                }
            } else {
                Log.w(TAG, "无法重新启动预览：相机设备或会话为空");
                // 尝试重新创建预览
                if (textureView != null && textureView.isAvailable()) {
                    createCameraPreview();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "重新启动预览失败", e);
            // 尝试重新创建预览
            if (textureView != null && textureView.isAvailable()) {
                try {
                    createCameraPreview();
                } catch (Exception ignored) {
                    Log.e(TAG, "重新创建预览失败", ignored);
                }
            }
        }
    }
    
    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(CameraDevice camera) {
            cameraDevice = camera;
            isCameraOpened = true;
            isCapturing = false; // 重置拍照状态
            Log.d(TAG, "相机已打开成功");
            createCameraPreview();
            Log.d(TAG, "相机预览已创建");
        }
        
        @Override
        public void onDisconnected(CameraDevice camera) {
            isCameraOpened = false;
            isCapturing = false; // 重置拍照状态
            if (cameraDevice != null) {
                cameraDevice.close();
                cameraDevice = null;
            }
            Log.d(TAG, "相机已断开连接");
            // 若存在预览视图，尝试延时重连，缓解黑屏
            if (textureView != null && backgroundHandler != null) {
                backgroundHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            openCamera();
                        } catch (Exception e) {
                            Log.e(TAG, "断开后重连失败", e);
                        }
                    }
                }, 1000);
            }
        }
        
        @Override
        public void onError(CameraDevice camera, int error) {
            isCameraOpened = false;
            isCapturing = false; // 重置拍照状态
            if (cameraDevice != null) {
                cameraDevice.close();
                cameraDevice = null;
            }
            Log.e(TAG, "打开相机错误: " + error);
            if (textureView != null && backgroundHandler != null) {
                backgroundHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            openCamera();
                        } catch (Exception e) {
                            Log.e(TAG, "错误后重连失败", e);
                        }
                    }
                }, 1200);
            }
        }
    };
    
    private void createCameraPreview() {
        try {
            // 在服务模式下，textureView可能为null，我们只需要创建captureSession
            Surface previewSurface = null;
            SurfaceTexture texture = null;
            
            if (textureView != null) {
                texture = textureView.getSurfaceTexture();
                if (texture == null) {
                    Log.e(TAG, "SurfaceTexture为空");
                    return;
                }
                
                // 确保预览尺寸已设置
                if (previewSize == null) {
                    Log.e(TAG, "预览尺寸未设置");
                    return;
                }
                
                texture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
                previewSurface = new Surface(texture);
            }
            
            // 确保相机设备已打开
            if (cameraDevice == null) {
                Log.e(TAG, "相机设备为空");
                return;
            }
            
            // 创建预览请求构建器（仅用于预览 Surface）
            final CaptureRequest.Builder previewBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);

            // 添加预览 surface（如果可用）
            if (previewSurface != null) {
                previewBuilder.addTarget(previewSurface);
            }

            // 预览阶段不向 ImageReader 输出，避免不必要的负载与异常

            // 预览阶段闪光（三态）：ON 使用手电（需有预览Surface），AUTO/OFF 关闭
            if (flashMode == FlashMode.ON && previewSurface != null) {
                previewBuilder.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_TORCH);
                previewBuilder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON);
                Log.d(TAG, "createPreview: 预览闪光 TORCH");
            } else {
                previewBuilder.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_OFF);
                previewBuilder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON);
                Log.d(TAG, "createPreview: 预览闪光 OFF");
            }

            // 设置自动对焦
            previewBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
            previewBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            
            // 创建capture session
            List<Surface> surfaces = new ArrayList<>();
            if (previewSurface != null) {
                surfaces.add(previewSurface);
            }
            if (imageReader != null) {
                surfaces.add(imageReader.getSurface());
            }
            
            // 确保surface列表不为空
            if (surfaces.isEmpty()) {
                Log.e(TAG, "没有可用的surface");
                return;
            }
            
            Log.d(TAG, "准备创建capture session，surface数量: " + surfaces.size());
            // 在内部类中使用时需要是final或有效final
            final boolean hasPreviewSurface = (previewSurface != null);

            cameraDevice.createCaptureSession(surfaces,
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(CameraCaptureSession session) {
                            Log.d(TAG, "相机预览会话已配置");
                            if (cameraDevice == null) {
                                Log.e(TAG, "相机设备为空，无法设置预览");
                                return;
                            }

                            captureSession = session;
                            try {
                                if (hasPreviewSurface) {
                                    // 仅在有预览 Surface 时启动重复预览请求
                                    captureSession.setRepeatingRequest(previewBuilder.build(), null, backgroundHandler);
                                    Log.d(TAG, "相机预览会话配置完成（包含预览）");
                                } else {
                                    // 服务模式（无预览）下不启动重复请求，保留会话用于静态拍照
                                    Log.d(TAG, "相机会话配置完成（无预览，仅拍照）");
                                }
                            } catch (CameraAccessException e) {
                                Log.e(TAG, "配置预览失败", e);
                            } catch (IllegalStateException e) {
                                Log.e(TAG, "相机状态异常", e);
                            }
                        }

                        @Override
                        public void onConfigureFailed(CameraCaptureSession session) {
                            Log.e(TAG, "配置失败");
                        }

                        @Override
                        public void onClosed(CameraCaptureSession session) {
                            Log.d(TAG, "相机预览会话已关闭");
                            super.onClosed(session);
                        }
                    }, backgroundHandler);
        } catch (CameraAccessException e) {
            Log.e(TAG, "创建预览失败", e);
        } catch (Exception e) {
            Log.e(TAG, "创建预览时发生未知错误", e);
        }
    }
    
    private final ImageReader.OnImageAvailableListener onImageAvailableListener
            = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
            Log.d(TAG, "图像数据已准备好");
            Image image = reader.acquireNextImage();
            if (image != null) {
                if (backgroundHandler != null) {
                    backgroundHandler.post(new ImageSaver(image));
                } else {
                    // 如果后台线程不可用，直接处理
                    new ImageSaver(image).run();
                }
            } else {
                Log.w(TAG, "获取到空的图像数据");
            }
        }
    };
    
    private class ImageSaver implements Runnable {
        private final Image image;
        
        public ImageSaver(Image image) {
            this.image = image;
        }
        
        @Override
        public void run() {
            // 检查image是否为null
            if (image == null) {
                Log.e(TAG, "图像数据为空，无法保存");
                return;
            }
            
            Log.d(TAG, "开始保存图片");
            ByteBuffer buffer = image.getPlanes()[0].getBuffer();
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            
            // 获取目标文件路径
            File file = getTargetImageFile();
            Log.d(TAG, "创建图片文件: " + file.getAbsolutePath());
            
            FileOutputStream output = null;
            File savedFile = null;
            try {
                output = new FileOutputStream(file);
                output.write(bytes);
                Log.d(TAG, "图片保存成功");
                savedFile = file;
                
                // 通知媒体扫描器有新文件
                try {
                    MediaScannerConnection.scanFile(context,
                            new String[]{file.getAbsolutePath()},
                            new String[]{"image/jpeg"},
                            null);
                    Log.d(TAG, "已通知媒体扫描器扫描新文件");
                } catch (Exception e) {
                    Log.e(TAG, "通知媒体扫描器失败", e);
                }
                
                // 将图片转换为Bitmap并传递给预览显示回调
                Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                if (previewDisplayCallback != null) {
                    previewDisplayCallback.onPreviewDisplay(bitmap);
                }
                
                // 通知回调拍照成功
                if (captureCallback != null && savedFile != null) {
                    captureCallback.onCaptureSuccess(savedFile.getAbsolutePath());
                }
            } catch (IOException e) {
                Log.e(TAG, "保存图片失败", e);
                if (captureCallback != null) {
                    captureCallback.onCaptureError(e);
                }
            } finally {
                // 确保图像资源被正确关闭
                if (image != null) {
                    try {
                        image.close();
                    } catch (Exception e) {
                        Log.e(TAG, "关闭图像时出错", e);
                    }
                }
                if (output != null) {
                    try {
                        output.close();
                    } catch (IOException e) {
                        Log.e(TAG, "关闭输出流失败", e);
                    }
                }
                
                // 拍照完成后重新启动预览（如果textureView不为null）
                // 使用post方法确保在后台线程中执行
                if (textureView != null && backgroundHandler != null) {
                    backgroundHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            restartPreview();
                        }
                    });
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
    
    // 创建公共目录的照片文件（用于系统相册显示）
    private File createPublicImageFile() {
        // 创建图片文件在公共图片目录中
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String imageFileName = "IMG_" + timeStamp + ".jpg";
        File storageDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        File imageFile = new File(storageDir, imageFileName);
        
        // 确保目录存在
        if (!storageDir.exists()) {
            storageDir.mkdirs();
        }
        
        return imageFile;
    }
    
    // 选择使用哪个目录保存照片
    private File getTargetImageFile() {
        // 默认保存到应用私有目录
        File privateFile = createImageFile();
        
        // 如果需要在系统相册中显示，也保存一份到公共目录
        try {
            File publicFile = createPublicImageFile();
            return publicFile;
        } catch (Exception e) {
            Log.e(TAG, "无法创建公共目录文件，使用私有目录", e);
            return privateFile;
        }
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
    
    // 设置预览显示回调
    public void setPreviewDisplayCallback(PreviewDisplayCallback callback) {
        this.previewDisplayCallback = callback;
    }
    
    // 预览显示回调接口
    public interface PreviewDisplayCallback {
        void onPreviewDisplay(Bitmap bitmap);
    }
}