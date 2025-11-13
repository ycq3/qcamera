package com.pipiqiang.qcamera.storage;

import android.content.Context;
import android.util.Base64;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Credentials;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class WebDavStorage implements CloudStorage {
    private OkHttpClient client;
    private String baseUrl; // e.g. https://webdav.example.com/remote.php/dav/files/USERNAME
    private String bucketPath; // optional path component, reused from config.bucket
    private String username;
    private String password;

    @Override
    public void init(Context context, Config config) {
        this.baseUrl = sanitizeBase(config.endpoint);
        this.bucketPath = sanitizeSegment(config.bucket);
        this.username = config.accessKey; // reuse accessKey as username
        this.password = config.secretKey; // reuse secretKey as password

        client = new OkHttpClient.Builder()
                .retryOnConnectionFailure(true)
                .build();
    }

    @Override
    public String upload(String key, File file) throws Exception {
        String url = buildUrl(key);
        MediaType octet = MediaType.parse("application/octet-stream");
        RequestBody body = RequestBody.create(file, octet);
        Request request = new Request.Builder()
                .url(url)
                .put(body)
                .header("Authorization", Credentials.basic(username == null ? "" : username, password == null ? "" : password))
                .build();
        try (Response resp = client.newCall(request).execute()) {
            if (!resp.isSuccessful()) {
                throw new RuntimeException("WebDAV upload failed: " + resp.code() + " " + resp.message());
            }
        }
        return key;
    }

    @Override
    public File download(String key, File targetFile) throws Exception {
        String url = buildUrl(key);
        Request request = new Request.Builder()
                .url(url)
                .get()
                .header("Authorization", Credentials.basic(username == null ? "" : username, password == null ? "" : password))
                .build();
        try (Response resp = client.newCall(request).execute()) {
            if (!resp.isSuccessful()) {
                throw new RuntimeException("WebDAV download failed: " + resp.code() + " " + resp.message());
            }
            try (InputStream in = resp.body().byteStream(); FileOutputStream out = new FileOutputStream(targetFile)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) >= 0) {
                    out.write(buf, 0, n);
                }
            }
        }
        return targetFile;
    }

    @Override
    public boolean delete(String key) throws Exception {
        String url = buildUrl(key);
        Request request = new Request.Builder()
                .url(url)
                .delete()
                .header("Authorization", Credentials.basic(username == null ? "" : username, password == null ? "" : password))
                .build();
        try (Response resp = client.newCall(request).execute()) {
            return resp.isSuccessful() || resp.code() == 404; // treat 404 as deleted
        }
    }

    @Override
    public List<String> list(String prefix, int maxKeys) throws Exception {
        // Simple placeholder: implement PROPFIND if listing is needed
        return new ArrayList<>();
    }

    public boolean ping() throws Exception {
        String url = baseUrl;
        if (bucketPath != null && !bucketPath.isEmpty()) {
            url = join(baseUrl, bucketPath);
        }
        if (url == null || url.trim().isEmpty()) {
            throw new IllegalStateException("缺少Endpoint");
        }
        Request head = new Request.Builder()
                .url(url)
                .head()
                .header("Authorization", Credentials.basic(username == null ? "" : username, password == null ? "" : password))
                .build();
        try (Response resp = client.newCall(head).execute()) {
            if (resp.isSuccessful()) return true;
            int code = resp.code();
            String message = resp.message();
            if (code == 405) {
                Request get = new Request.Builder()
                        .url(url)
                        .get()
                        .header("Range", "bytes=0-0")
                        .header("Authorization", Credentials.basic(username == null ? "" : username, password == null ? "" : password))
                        .build();
                try (Response r2 = client.newCall(get).execute()) {
                    if (r2.isSuccessful()) return true;
                    throw new RuntimeException("WebDAV连接失败: " + r2.code() + " " + r2.message());
                }
            }
            if (code == 401 || code == 403) {
                throw new RuntimeException("认证失败: " + code + " " + message);
            }
            if (code == 404) {
                throw new RuntimeException("路径不存在: " + url);
            }
            throw new RuntimeException("连接失败: " + code + " " + message);
        }
    }

    private String buildUrl(String key) {
        String k = sanitizeSegment(key);
        String path = baseUrl;
        if (bucketPath != null && !bucketPath.isEmpty()) {
            path = join(path, bucketPath);
        }
        return join(path, k);
    }

    private static String sanitizeBase(String s) {
        if (s == null) return "";
        s = s.trim();
        // remove trailing slash
        return s.replaceAll("/+$", "");
    }

    private static String sanitizeSegment(String s) {
        if (s == null) return "";
        s = s.trim();
        s = s.replaceAll("^/+", "").replaceAll("/+$", "");
        return s;
    }

    private static String join(String a, String b) {
        if (a == null || a.isEmpty()) return b;
        if (b == null || b.isEmpty()) return a;
        if (a.endsWith("/")) a = a.replaceAll("/+$", "");
        if (b.startsWith("/")) b = b.replaceAll("^/+", "");
        return a + "/" + b;
    }
}
