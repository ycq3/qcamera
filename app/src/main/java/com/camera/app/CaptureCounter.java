package com.camera.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

/**
 * 拍照计数器管理类
 * 用于跟踪和管理应用会话中的拍照数量
 */
public class CaptureCounter {
    private static final String TAG = "CaptureCounter";
    private static final String PREF_NAME = "capture_counter";
    private static final String KEY_SESSION_COUNT = "session_count";
    private static final String KEY_TOTAL_COUNT = "total_count";
    
    private SharedPreferences sharedPreferences;
    private int sessionCount = 0; // 当前会话计数
    private int totalCount = 0;   // 总计数
    
    public CaptureCounter(Context context) {
        sharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        loadCounts();
    }
    
    /**
     * 加载计数器值
     */
    private void loadCounts() {
        sessionCount = sharedPreferences.getInt(KEY_SESSION_COUNT, 0);
        totalCount = sharedPreferences.getInt(KEY_TOTAL_COUNT, 0);
        Log.d(TAG, "加载计数器 - 会话计数: " + sessionCount + ", 总计数: " + totalCount);
    }

    /**
     * 重新从SharedPreferences加载计数（跨组件更新时调用）
     */
    public void reloadCounts() {
        loadCounts();
    }
    
    /**
     * 递增拍照计数
     */
    public void incrementCount() {
        sessionCount++;
        totalCount++;
        saveCounts();
        Log.d(TAG, "计数器递增 - 会话计数: " + sessionCount + ", 总计数: " + totalCount);
    }
    
    /**
     * 保存计数器值到SharedPreferences
     */
    private void saveCounts() {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putInt(KEY_SESSION_COUNT, sessionCount);
        editor.putInt(KEY_TOTAL_COUNT, totalCount);
        editor.apply();
    }
    
    /**
     * 重置会话计数（总停止后调用）
     */
    public void resetSessionCount() {
        sessionCount = 0;
        saveCounts();
        Log.d(TAG, "重置会话计数 - 总计数: " + totalCount);
    }
    
    /**
     * 获取当前会话计数
     * @return 当前会话拍照数量
     */
    public int getSessionCount() {
        return sessionCount;
    }
    
    /**
     * 获取总计数
     * @return 总拍照数量
     */
    public int getTotalCount() {
        return totalCount;
    }
    
    /**
     * 清除所有计数
     */
    public void clearAllCounts() {
        sessionCount = 0;
        totalCount = 0;
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putInt(KEY_SESSION_COUNT, 0);
        editor.putInt(KEY_TOTAL_COUNT, 0);
        editor.apply();
        Log.d(TAG, "清除所有计数");
    }
}