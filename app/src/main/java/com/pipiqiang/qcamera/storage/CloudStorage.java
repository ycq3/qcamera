package com.pipiqiang.qcamera.storage;

import android.content.Context;

import java.io.File;
import java.util.List;

public interface CloudStorage {
    enum Provider {
        NONE,
        S3,
        GCS,
        AZURE,
        OSS,
        COS,
        WEBDAV
    }

    class Config {
        public final Provider provider;
        public final String accessKey;
        public final String secretKey;
        public final String region;
        public final String bucket;
        public final String endpoint; // optional for OSS/COS/custom
        public final boolean encryptionEnabled;
        public final boolean resumableEnabled;

        public Config(Provider provider, String accessKey, String secretKey,
                      String region, String bucket, String endpoint,
                      boolean encryptionEnabled, boolean resumableEnabled) {
            this.provider = provider;
            this.accessKey = accessKey;
            this.secretKey = secretKey;
            this.region = region;
            this.bucket = bucket;
            this.endpoint = endpoint;
            this.encryptionEnabled = encryptionEnabled;
            this.resumableEnabled = resumableEnabled;
        }
    }

    void init(Context context, Config config) throws Exception;

    String upload(String key, File file) throws Exception;

    File download(String key, File targetFile) throws Exception;

    boolean delete(String key) throws Exception;

    List<String> list(String prefix, int maxKeys) throws Exception;
}