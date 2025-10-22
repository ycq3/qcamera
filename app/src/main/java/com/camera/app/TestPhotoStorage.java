package com.camera.app;

import android.content.Context;
import android.util.Log;

import java.io.File;

public class TestPhotoStorage {
    private static final String TAG = "TestPhotoStorage";
    
    public static void testPhotoStorage(Context context) {
        try {
            // 测试存储路径
            File photoDir = context.getExternalFilesDir(null);
            Log.d(TAG, "照片存储目录: " + photoDir.getAbsolutePath());
            Log.d(TAG, "目录是否存在: " + photoDir.exists());
            
            if (photoDir.exists()) {
                // 列出目录中的所有文件
                File[] files = photoDir.listFiles();
                if (files != null) {
                    Log.d(TAG, "目录中文件数量: " + files.length);
                    for (File file : files) {
                        Log.d(TAG, "文件: " + file.getName() + ", 大小: " + file.length() + ", 修改时间: " + file.lastModified());
                    }
                } else {
                    Log.d(TAG, "无法列出目录中的文件");
                }
            } else {
                Log.d(TAG, "照片存储目录不存在");
            }
        } catch (Exception e) {
            Log.e(TAG, "测试照片存储时出错", e);
        }
    }
}