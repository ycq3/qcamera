package com.camera.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class BootReceiver extends BroadcastReceiver {
    
    private static final String TAG = "BootReceiver";
    
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Log.d(TAG, "设备启动完成");
            
            // 在实际应用中，可以根据保存的状态决定是否需要重启拍照服务
            // 这里我们只记录日志，不自动启动服务
            // 如果需要自动启动，可以取消下面的注释
            
            /*
            // 启动拍照服务
            Intent serviceIntent = new Intent(context, CameraService.class);
            serviceIntent.setAction(CameraService.ACTION_START_CAPTURE);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent);
            } else {
                context.startService(serviceIntent);
            }
            */
        }
    }
}