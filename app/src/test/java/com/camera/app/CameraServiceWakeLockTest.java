package com.pipiqiang.qcamera.app;

import static org.junit.Assert.*;

import android.content.Intent;
import android.os.Build;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.annotation.Config;

@RunWith(org.robolectric.RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.Q)
public class CameraServiceWakeLockTest {

    @Test
    public void startCapture_serviceStarts() {
        Intent intent = new Intent(ApplicationProvider.getApplicationContext(), CameraService.class);
        intent.setAction(CameraService.ACTION_START_CAPTURE);
        CameraService service = Robolectric.buildService(CameraService.class, intent).create().get();
        assertNotNull(service);
    }
}