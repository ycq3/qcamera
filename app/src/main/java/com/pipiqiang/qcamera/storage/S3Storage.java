package com.pipiqiang.qcamera.storage;

import android.content.Context;
import android.net.Uri;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.ListObjectsRequest;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferListener;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferObserver;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferState;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferUtility;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class S3Storage implements CloudStorage {
    private AmazonS3Client s3Client;
    private TransferUtility transferUtility;
    private String bucket;

    @Override
    public void init(Context context, Config config) throws Exception {
        this.bucket = config.bucket;
        BasicAWSCredentials creds = new BasicAWSCredentials(config.accessKey, config.secretKey);

        ClientConfiguration clientConfiguration = new ClientConfiguration();
        clientConfiguration.setMaxConnections(16);
        clientConfiguration.setConnectionTimeout(60_000);
        clientConfiguration.setSocketTimeout(60_000);

        Regions regionsEnum = Regions.fromName(config.region);
        Region regionObj = Region.getRegion(regionsEnum);
        s3Client = new AmazonS3Client(creds, regionObj, clientConfiguration);
        if (config.endpoint != null && !config.endpoint.trim().isEmpty()) {
            s3Client.setEndpoint(config.endpoint);
        }

        transferUtility = TransferUtility.builder()
                .context(context)
                .s3Client(s3Client)
                .build();
    }

    @Override
    public String upload(String key, File file) throws Exception {
        TransferObserver observer = transferUtility.upload(bucket, key, file);
        final Object lock = new Object();
        final Exception[] error = new Exception[1];
        observer.setTransferListener(new TransferListener() {
            @Override
            public void onStateChanged(int id, TransferState state) {
                if (state == TransferState.COMPLETED || state == TransferState.FAILED || state == TransferState.CANCELED) {
                    synchronized (lock) {
                        lock.notifyAll();
                    }
                }
            }

            @Override
            public void onProgressChanged(int id, long bytesCurrent, long bytesTotal) { }

            @Override
            public void onError(int id, Exception ex) {
                error[0] = ex;
            }
        });
        synchronized (lock) {
            lock.wait(5 * 60 * 1000);
        }
        if (error[0] != null) {
            throw error[0];
        }
        return key;
    }

    @Override
    public File download(String key, File targetFile) throws Exception {
        TransferObserver observer = transferUtility.download(bucket, key, targetFile);
        final Object lock = new Object();
        final Exception[] error = new Exception[1];
        observer.setTransferListener(new TransferListener() {
            @Override
            public void onStateChanged(int id, TransferState state) {
                if (state == TransferState.COMPLETED || state == TransferState.FAILED || state == TransferState.CANCELED) {
                    synchronized (lock) {
                        lock.notifyAll();
                    }
                }
            }

            @Override
            public void onProgressChanged(int id, long bytesCurrent, long bytesTotal) { }

            @Override
            public void onError(int id, Exception ex) {
                error[0] = ex;
            }
        });
        synchronized (lock) {
            lock.wait(5 * 60 * 1000);
        }
        if (error[0] != null) {
            throw error[0];
        }
        return targetFile;
    }

    @Override
    public boolean delete(String key) throws Exception {
        s3Client.deleteObject(bucket, key);
        return true;
    }

    @Override
    public List<String> list(String prefix, int maxKeys) throws Exception {
        ListObjectsRequest req = new ListObjectsRequest()
                .withBucketName(bucket)
                .withPrefix(prefix)
                .withMaxKeys(maxKeys);
        ObjectListing listing = s3Client.listObjects(req);
        List<String> keys = new ArrayList<>();
        for (S3ObjectSummary summary : listing.getObjectSummaries()) {
            keys.add(summary.getKey());
        }
        return keys;
    }
}