package com.pipiqiang.qcamera.storage;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import okhttp3.MediaType;
import okhttp3.RequestBody;
import okio.BufferedSink;

public class ProgressRequestBody extends RequestBody {
    private final File file;
    private final MediaType mediaType;
    private final ProgressListener listener;

    public interface ProgressListener {
        void onProgress(long bytesWritten, long contentLength);
    }

    public ProgressRequestBody(File file, MediaType mediaType, ProgressListener listener) {
        this.file = file;
        this.mediaType = mediaType;
        this.listener = listener;
    }

    @Override
    public MediaType contentType() {
        return mediaType;
    }

    @Override
    public long contentLength() {
        return file.length();
    }

    @Override
    public void writeTo(BufferedSink sink) throws IOException {
        long total = contentLength();
        long written = 0L;
        byte[] buffer = new byte[8192];
        FileInputStream in = new FileInputStream(file);
        try {
            int read;
            while ((read = in.read(buffer)) != -1) {
                sink.write(buffer, 0, read);
                written += read;
                if (listener != null) listener.onProgress(written, total);
            }
        } finally {
            try { in.close(); } catch (Exception ignored) {}
        }
    }
}