package com.pipiqiang.qcamera.app;

import java.util.concurrent.ConcurrentHashMap;

public class UploadProgressBus {
    public interface Listener {
        void onProgress(String key, long bytes, long total);
    }

    private static final ConcurrentHashMap<String, Listener> LISTENERS = new ConcurrentHashMap<>();

    public static void register(String key, Listener listener) {
        if (key == null || listener == null) return;
        LISTENERS.put(key, listener);
    }

    public static void unregister(String key) {
        if (key == null) return;
        LISTENERS.remove(key);
    }

    public static Listener get(String key) {
        if (key == null) return null;
        return LISTENERS.get(key);
    }
}