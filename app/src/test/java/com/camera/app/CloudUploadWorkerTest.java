package com.pipiqiang.qcamera.app;

import static org.junit.Assert.*;

import android.os.Build;

import androidx.work.Constraints;
import androidx.work.NetworkType;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

@RunWith(org.robolectric.RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.Q)
public class CloudUploadWorkerTest {

    @Test
    public void constraints_connected() {
        Constraints c = CloudUploadWorker.connectedNetworkConstraints();
        assertEquals(NetworkType.CONNECTED, c.getRequiredNetworkType());
    }
}
