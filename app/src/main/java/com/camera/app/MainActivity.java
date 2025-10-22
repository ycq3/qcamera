package com.camera.app;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {
    
    private static final String TAG = "MainActivity";
    private static final int PERMISSION_REQUEST_CODE = 100;
    
    private Button btnStartStop;
    private Button btnSettings;
    private Button btnGallery;
    private Button btnTestStorage;
    private TextView tvStatus;
    private boolean isRunning = false;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        initViews();
        setupListeners();
        
        // 检查必要权限
        checkPermissions();
    }
    
    private void initViews() {
        btnStartStop = findViewById(R.id.btn_start_stop);
        btnSettings = findViewById(R.id.btn_settings);
        btnGallery = findViewById(R.id.btn_gallery);
        btnTestStorage = findViewById(R.id.btn_test_storage);
        tvStatus = findViewById(R.id.tv_status);
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
        
        btnTestStorage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                TestPhotoStorage.testPhotoStorage(MainActivity.this);
                Toast.makeText(MainActivity.this, "存储测试已完成，请查看日志", Toast.LENGTH_SHORT).show();
            }
        });
        
        // 临时显示测试按钮用于调试
        btnTestStorage.setVisibility(View.VISIBLE);
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
            }
        }
    }
    
    private void startCapture() {
        // 启动拍照服务
        Intent serviceIntent = new Intent(this, CameraService.class);
        serviceIntent.setAction(CameraService.ACTION_START_CAPTURE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
        
        // 更新UI状态
        isRunning = true;
        btnStartStop.setText(R.string.stop_capture);
        tvStatus.setText(R.string.status_running);
    }
    
    private void stopCapture() {
        // 停止拍照服务
        Intent serviceIntent = new Intent(this, CameraService.class);
        serviceIntent.setAction(CameraService.ACTION_STOP_CAPTURE);
        startService(serviceIntent);
        
        // 更新UI状态
        isRunning = false;
        btnStartStop.setText(R.string.start_capture);
        tvStatus.setText(R.string.status_stopped);
    }
}