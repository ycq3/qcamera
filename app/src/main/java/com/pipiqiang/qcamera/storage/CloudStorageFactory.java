package com.pipiqiang.qcamera.storage;

import android.content.Context;

public class CloudStorageFactory {

    public static CloudStorage create(Context context, CloudStorage.Config config) throws Exception {
        switch (config.provider) {
            case S3:
                S3Storage s3 = new S3Storage();
                s3.init(context, config);
                return s3;
            case WEBDAV:
                WebDavStorage webdav = new WebDavStorage();
                webdav.init(context, config);
                return webdav;
            case GCS:
                S3Storage gcsLike = new S3Storage();
                gcsLike.init(context, config);
                return gcsLike;
            case COS:
                S3Storage cosLike = new S3Storage();
                cosLike.init(context, config);
                return cosLike;
            case AZURE:
            case OSS:
                NoopStorage noop = new NoopStorage();
                noop.init(context, config);
                return noop;
            case NONE:
            default:
                return new NoopStorage();
        }
    }
}
