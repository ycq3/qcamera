package com.pipiqiang.qcamera.app;

import android.content.Context;
import androidx.preference.PreferenceManager;
import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class EmailSenderTest {
    @Test
    public void testDefaultSmtpConfigValues() {
        Context ctx = ApplicationProvider.getApplicationContext();
        // 清空默认SharedPreferences，避免非默认值影响断言
        PreferenceManager.getDefaultSharedPreferences(ctx).edit().clear().commit();

        SettingsManager sm = new SettingsManager(ctx);

        // 验证默认值（不触发真实发送）
        assertEquals("tls", sm.getSmtpEncryption());
        assertEquals(587, sm.getSmtpPort());
        assertTrue(sm.isSmtpHtmlEnabled());
    }
}