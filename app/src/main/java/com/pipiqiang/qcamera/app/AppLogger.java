package com.pipiqiang.qcamera.app;

import android.content.Context;
import android.text.format.DateFormat;
import android.util.Log;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AppLogger {

    private static volatile AppLogger instance;
    private final Context appContext;
    private final ExecutorService executor;
    private File currentLogFile;
    private BufferedWriter writer;
    private String currentDay;

    private AppLogger(Context ctx) {
        this.appContext = ctx.getApplicationContext();
        this.executor = Executors.newSingleThreadExecutor();
        rotateIfNeeded();
    }

    public static void init(Context ctx) {
        if (instance == null) {
            synchronized (AppLogger.class) {
                if (instance == null) {
                    instance = new AppLogger(ctx);
                }
            }
        }
    }

    private static AppLogger get() {
        return instance;
    }

    public static void d(String tag, String msg) {
        Log.d(tag, msg);
        write("D", tag, msg, null);
    }

    public static void i(String tag, String msg) {
        Log.i(tag, msg);
        write("I", tag, msg, null);
    }

    public static void w(String tag, String msg) {
        Log.w(tag, msg);
        write("W", tag, msg, null);
    }

    public static void w(String tag, String msg, Throwable t) {
        Log.w(tag, msg, t);
        String stack = t == null ? null : android.util.Log.getStackTraceString(t);
        write("W", tag, msg, stack);
    }

    public static void e(String tag, String msg) {
        Log.e(tag, msg);
        write("E", tag, msg, null);
    }

    public static void e(String tag, String msg, Throwable t) {
        Log.e(tag, msg, t);
        String stack = t == null ? null : android.util.Log.getStackTraceString(t);
        write("E", tag, msg, stack);
    }

    private static void write(String level, String tag, String msg, String stack) {
        AppLogger inst = get();
        if (inst == null) return;
        inst.executor.execute(() -> {
            try {
                inst.rotateIfNeeded();
                String ts = DateFormat.format("yyyy-MM-dd HH:mm:ss.SSS", new Date()).toString();
                String line = ts + " " + level + "/" + tag + ": " + msg;
                inst.ensureWriter();
                inst.writer.write(line);
                inst.writer.newLine();
                if (stack != null && !stack.isEmpty()) {
                    inst.writer.write(stack);
                    inst.writer.newLine();
                }
                inst.writer.flush();
            } catch (Exception ignored) {}
        });
    }

    private void ensureWriter() throws IOException {
        if (writer == null) {
            writer = new BufferedWriter(new FileWriter(currentLogFile, true));
        }
    }

    private void rotateIfNeeded() {
        String day = DateFormat.format("yyyyMMdd", new Date()).toString();
        if (currentDay != null && currentDay.equals(day) && currentLogFile != null && currentLogFile.exists()) return;
        currentDay = day;
        File dir = new File(appContext.getFilesDir(), "logs");
        if (!dir.exists()) dir.mkdirs();
        currentLogFile = new File(dir, "app-" + day + ".log");
        if (writer != null) {
            try { writer.close(); } catch (Exception ignored) {}
            writer = null;
        }
    }

    public static File[] listLogFiles(Context ctx) {
        File dir = new File(ctx.getFilesDir(), "logs");
        File[] files = dir.listFiles();
        if (files == null) return new File[0];
        return files;
    }

    public static void enqueueUpload(Context ctx) {
        SettingsManager sm = new SettingsManager(ctx);
        if (!sm.isCloudEnabled()) return;
        File[] files = listLogFiles(ctx);
        if (files.length == 0) return;
        String today = android.text.format.DateFormat.format("yyyyMMdd", new Date()).toString();
        for (File f : files) {
            String name = f.getName();
            if (name.contains(today)) continue;
            androidx.work.Data data = new androidx.work.Data.Builder()
                    .putString(com.pipiqiang.qcamera.app.CloudUploadWorker.KEY_PHOTO_PATH, f.getAbsolutePath())
                    .putString("key_prefix", "logs")
                    .build();
            androidx.work.OneTimeWorkRequest req = new androidx.work.OneTimeWorkRequest.Builder(com.pipiqiang.qcamera.app.CloudUploadWorker.class)
                    .addTag(com.pipiqiang.qcamera.app.CloudUploadWorker.TAG_UPLOAD_WORK)
                    .setConstraints(com.pipiqiang.qcamera.app.CloudUploadWorker.connectedNetworkConstraints())
                    .setInputData(data)
                    .build();
            androidx.work.WorkManager.getInstance(ctx).enqueue(req);
        }
    }
}
