package com.pipiqiang.qcamera.storage;

import android.content.Context;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class NoopStorage implements CloudStorage {
    @Override
    public void init(Context context, Config config) {
        // no-op
    }

    @Override
    public String upload(String key, File file) {
        return key;
    }

    @Override
    public File download(String key, File targetFile) {
        return targetFile;
    }

    @Override
    public boolean delete(String key) {
        return true;
    }

    @Override
    public List<String> list(String prefix, int maxKeys) {
        return new ArrayList<>();
    }
}