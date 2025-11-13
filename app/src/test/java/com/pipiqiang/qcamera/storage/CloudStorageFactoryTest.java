package com.pipiqiang.qcamera.storage;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;

import com.pipiqiang.qcamera.storage.CloudStorage.Config;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertNotNull;

@RunWith(RobolectricTestRunner.class)
public class CloudStorageFactoryTest {
    @Test
    public void testCreateS3Storage() throws Exception {
        Context ctx = ApplicationProvider.getApplicationContext();
        Config cfg = new Config(CloudStorage.Provider.S3,
                "AKIA...", "SECRET...", "us-east-1", "bucket", null, true, true);
        CloudStorage storage = CloudStorageFactory.create(ctx, cfg);
        assertNotNull(storage);
    }
}