package com.camera.app;

import android.content.Context;
import android.os.Environment;
import android.os.StatFs;
import android.util.Log;

import java.io.File;
import java.io.FileFilter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;

public class StorageManager {
    
    private static final String TAG = "StorageManager";
    private static final long MIN_FREE_SPACE_DEFAULT = 100 * 1024 * 1024; // 100MB
    
    private Context context;
    
    public StorageManager(Context context) {
        this.context = context;
    }
    
    /**
     * 检查是否有足够的存储空间
     * @param requiredSpace 所需空间（字节）
     * @return 是否有足够空间
     */
    public boolean hasEnoughSpace(long requiredSpace) {
        long freeSpace = getFreeSpace();
        return freeSpace > requiredSpace;
    }
    
    /**
     * 获取可用存储空间（字节）
     * @return 可用空间大小
     */
    public long getFreeSpace() {
        File path = context.getExternalFilesDir(null);
        if (path == null) {
            return 0;
        }
        
        StatFs stat = new StatFs(path.getPath());
        long blockSize = stat.getBlockSizeLong();
        long availableBlocks = stat.getAvailableBlocksLong();
        return availableBlocks * blockSize;
    }
    
    /**
     * 自动清理旧照片以释放空间
     * @param minFreeSpace 最小剩余空间（字节）
     * @return 是否成功清理
     */
    public boolean cleanOldPhotos(long minFreeSpace) {
        try {
            long currentFreeSpace = getFreeSpace();
            if (currentFreeSpace >= minFreeSpace) {
                return true; // 空间已经足够
            }
            
            long needToFree = minFreeSpace - currentFreeSpace;
            long freedSpace = 0;
            
            File photoDir = context.getExternalFilesDir(null);
            if (photoDir == null || !photoDir.exists()) {
                return false;
            }
            
            // 获取所有照片文件
            File[] photoFiles = photoDir.listFiles(new FileFilter() {
                @Override
                public boolean accept(File file) {
                    return file.isFile() && file.getName().toLowerCase(Locale.ROOT).endsWith(".jpg");
                }
            });
            
            if (photoFiles == null || photoFiles.length == 0) {
                return false;
            }
            
            // 按修改时间排序，旧的在前
            Arrays.sort(photoFiles, new Comparator<File>() {
                @Override
                public int compare(File f1, File f2) {
                    return Long.compare(f1.lastModified(), f2.lastModified());
                }
            });
            
            // 删除旧照片直到释放足够空间
            for (File photoFile : photoFiles) {
                long fileSize = photoFile.length();
                if (photoFile.delete()) {
                    freedSpace += fileSize;
                    Log.d(TAG, "删除旧照片: " + photoFile.getName() + ", 大小: " + fileSize + " 字节");
                    
                    if (freedSpace >= needToFree) {
                        break;
                    }
                }
            }
            
            Log.d(TAG, "清理完成，释放空间: " + freedSpace + " 字节");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "清理旧照片时出错", e);
            return false;
        }
    }
    
    /**
     * 获取照片目录
     * @return 照片存储目录
     */
    public File getPhotoDirectory() {
        return context.getExternalFilesDir(null);
    }
    
    /**
     * 根据设置检查并清理存储空间
     * @param autoClean 是否启用自动清理
     * @param minSpaceMB 最小剩余空间（MB）
     */
    public void checkAndCleanStorage(boolean autoClean, int minSpaceMB) {
        if (!autoClean) {
            return;
        }
        
        long minSpaceBytes = minSpaceMB * 1024L * 1024L;
        if (minSpaceBytes <= 0) {
            minSpaceBytes = MIN_FREE_SPACE_DEFAULT;
        }
        
        long freeSpace = getFreeSpace();
        Log.d(TAG, "当前剩余空间: " + freeSpace + " 字节 (" + (freeSpace / (1024*1024)) + " MB)");
        
        if (freeSpace < minSpaceBytes) {
            Log.d(TAG, "空间不足，开始清理旧照片");
            cleanOldPhotos(minSpaceBytes);
        }
    }
}