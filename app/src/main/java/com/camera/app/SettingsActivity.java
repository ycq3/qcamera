package com.pipiqiang.qcamera.app;
import com.pipiqiang.qcamera.R;

import android.os.Bundle;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.settings_activity);
        
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
        
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.settings, new SettingsFragment())
                .commit();
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    public static class SettingsFragment extends PreferenceFragmentCompat {
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey);
            ListPreference provider = findPreference("cloud_provider");
            Preference accessKey = findPreference("cloud_access_key");
            Preference secretKey = findPreference("cloud_secret_key");
            Preference region = findPreference("cloud_region");
            Preference bucket = findPreference("cloud_bucket");
            Preference endpoint = findPreference("cloud_endpoint");
            Preference pathPrefix = findPreference("cloud_path_prefix");
            Preference testConn = findPreference("cloud_test_connection");
            Preference smtpTest = findPreference("smtp_test_email");
            if (provider != null) {
                String value = provider.getValue();
                updateCloudLabels(value, accessKey, secretKey, region, bucket, endpoint, pathPrefix);
                provider.setOnPreferenceChangeListener((pref, newValue) -> {
                    updateCloudLabels(String.valueOf(newValue), accessKey, secretKey, region, bucket, endpoint, pathPrefix);
                    return true;
                });
            }

            if (testConn != null) {
                testConn.setOnPreferenceClickListener(pref -> {
                    if (getContext() == null) return true;
                    android.widget.Toast.makeText(getContext(), R.string.cloud_test_connecting, android.widget.Toast.LENGTH_SHORT).show();
                    new Thread(() -> {
                        Exception error = null;
                        boolean ok = false;
                        try {
                            SettingsManager sm = new SettingsManager(getContext());
                            String raw = sm.getCloudProviderRaw();
                            com.pipiqiang.qcamera.storage.CloudStorage.Provider p;
                            if ("s3".equalsIgnoreCase(raw)) p = com.pipiqiang.qcamera.storage.CloudStorage.Provider.S3;
                            else if ("webdav".equalsIgnoreCase(raw)) p = com.pipiqiang.qcamera.storage.CloudStorage.Provider.WEBDAV;
                            else if ("gcs".equalsIgnoreCase(raw)) p = com.pipiqiang.qcamera.storage.CloudStorage.Provider.GCS;
                            else if ("azure".equalsIgnoreCase(raw)) p = com.pipiqiang.qcamera.storage.CloudStorage.Provider.AZURE;
                            else if ("oss".equalsIgnoreCase(raw)) p = com.pipiqiang.qcamera.storage.CloudStorage.Provider.OSS;
                            else if ("cos".equalsIgnoreCase(raw)) p = com.pipiqiang.qcamera.storage.CloudStorage.Provider.COS;
                            else p = com.pipiqiang.qcamera.storage.CloudStorage.Provider.NONE;
                            com.pipiqiang.qcamera.storage.CloudStorage.Config cfg = new com.pipiqiang.qcamera.storage.CloudStorage.Config(
                                    p,
                                    sm.getCloudAccessKey(),
                                    sm.getCloudSecretKey(),
                                    sm.getCloudRegion(),
                                    sm.getCloudBucket(),
                                    sm.getCloudEndpoint(),
                                    sm.isCloudEncryptionEnabled(),
                                    sm.isCloudResumableEnabled()
                            );
                            com.pipiqiang.qcamera.storage.CloudStorage storage = com.pipiqiang.qcamera.storage.CloudStorageFactory.create(getContext().getApplicationContext(), cfg);
                            if (p == com.pipiqiang.qcamera.storage.CloudStorage.Provider.WEBDAV && storage instanceof com.pipiqiang.qcamera.storage.WebDavStorage) {
                                ok = ((com.pipiqiang.qcamera.storage.WebDavStorage) storage).ping();
                            } else if (p == com.pipiqiang.qcamera.storage.CloudStorage.Provider.S3) {
                                try {
                                    storage.list("", 1);
                                    ok = true;
                                } catch (Exception e) {
                                    error = e;
                                }
                            } else if (p == com.pipiqiang.qcamera.storage.CloudStorage.Provider.GCS ||
                                       p == com.pipiqiang.qcamera.storage.CloudStorage.Provider.COS) {
                                try {
                                    storage.list("", 1);
                                    ok = true;
                                } catch (Exception e) {
                                    error = e;
                                }
                            } else if (p == com.pipiqiang.qcamera.storage.CloudStorage.Provider.AZURE ||
                                       p == com.pipiqiang.qcamera.storage.CloudStorage.Provider.OSS) {
                                String url = buildProbeUrl(raw, sm.getCloudEndpoint(), sm.getCloudBucket(), sm.getCloudRegion());
                                if (url == null || url.trim().isEmpty()) {
                                    throw new IllegalStateException("缺少Endpoint或Bucket");
                                }
                                OkHttpClient c = new OkHttpClient.Builder().retryOnConnectionFailure(true).build();
                                Request head = new Request.Builder().url(url).head().build();
                                try (Response r = c.newCall(head).execute()) {
                                    if (r.isSuccessful()) ok = true;
                                    else if (r.code() == 405) {
                                        Request get = new Request.Builder().url(url).header("Range", "bytes=0-0").get().build();
                                        try (Response r2 = c.newCall(get).execute()) { ok = r2.isSuccessful(); }
                                    } else {
                                        ok = false;
                                    }
                                }
                            } else {
                                error = new IllegalStateException("当前云存储未启用");
                            }
                        } catch (Exception e) {
                            error = e;
                        }
                        final Exception errFinal = error;
                        final boolean okFinal = ok;
                        if (getActivity() == null) return;
                        getActivity().runOnUiThread(() -> {
                            if (okFinal && errFinal == null) {
                                android.widget.Toast.makeText(getContext(), R.string.cloud_test_success, android.widget.Toast.LENGTH_SHORT).show();
                            } else {
                                String msg = errFinal == null ? "未知错误" : errFinal.getMessage();
                                android.widget.Toast.makeText(getContext(), getString(R.string.cloud_test_failed, msg), android.widget.Toast.LENGTH_LONG).show();
                            }
                        });
                    }).start();
                    return true;
                });
            }

            if (smtpTest != null) {
                smtpTest.setOnPreferenceClickListener(pref -> {
                    if (getContext() == null) return true;
                    android.widget.Toast.makeText(getContext(), R.string.smtp_test_sending, android.widget.Toast.LENGTH_SHORT).show();
                    new Thread(() -> {
                        Exception error = null;
                        boolean ok = false;
                        try {
                            SettingsManager sm = new SettingsManager(getContext());
                            String to = sm.getSmtpFrom();
                            if (to == null || to.trim().isEmpty()) {
                                to = sm.getEmailAddress();
                            }
                            if (to == null || to.trim().isEmpty()) {
                                throw new IllegalStateException(getString(R.string.smtp_test_missing_recipient));
                            }
                            EmailSender sender = new EmailSender(sm);
                            sender.send(to, "SMTP测试", "这是一封测试邮件", null);
                            ok = true;
                        } catch (Exception e) {
                            error = e;
                        }
                        final Exception errFinal = error;
                        final boolean okFinal = ok;
                        if (getActivity() == null) return;
                        getActivity().runOnUiThread(() -> {
                            if (okFinal && errFinal == null) {
                                android.widget.Toast.makeText(getContext(), R.string.smtp_test_success, android.widget.Toast.LENGTH_SHORT).show();
                            } else {
                                String msg = errFinal == null ? "未知错误" : errFinal.getMessage();
                                android.widget.Toast.makeText(getContext(), getString(R.string.smtp_test_failed, msg), android.widget.Toast.LENGTH_LONG).show();
                            }
                        });
                    }).start();
                    return true;
                });
            }
        }

        private void updateCloudLabels(String provider,
                                       Preference accessKey,
                                       Preference secretKey,
                                       Preference region,
                                       Preference bucket,
                                       Preference endpoint,
                                       Preference pathPrefix) {
            if (provider == null) provider = "none";
            if ("webdav".equalsIgnoreCase(provider)) {
                if (accessKey != null) accessKey.setTitle(R.string.cloud_label_username);
                if (secretKey != null) secretKey.setTitle(R.string.cloud_label_password);
                if (region != null) region.setTitle(R.string.cloud_label_region);
                if (bucket != null) bucket.setTitle(R.string.cloud_label_path_prefix);
                if (endpoint != null) endpoint.setTitle(R.string.cloud_label_endpoint);
                if (pathPrefix != null) pathPrefix.setTitle(R.string.cloud_label_path_prefix);
            } else if ("azure".equalsIgnoreCase(provider)) {
                if (accessKey != null) accessKey.setTitle(R.string.pref_title_cloud_access_key);
                if (secretKey != null) secretKey.setTitle(R.string.pref_title_cloud_secret_key);
                if (region != null) region.setTitle(R.string.pref_title_cloud_region);
                if (bucket != null) bucket.setTitle(R.string.cloud_label_container);
                if (endpoint != null) endpoint.setTitle(R.string.cloud_label_endpoint);
                if (pathPrefix != null) pathPrefix.setTitle(R.string.pref_title_cloud_path_prefix);
            } else if ("cos".equalsIgnoreCase(provider)) {
                if (accessKey != null) accessKey.setTitle(R.string.cloud_label_secret_id);
                if (secretKey != null) secretKey.setTitle(R.string.pref_title_cloud_secret_key);
                if (region != null) region.setTitle(R.string.pref_title_cloud_region);
                if (bucket != null) bucket.setTitle(R.string.cloud_label_bucket);
                if (endpoint != null) endpoint.setTitle(R.string.pref_title_cloud_endpoint);
                if (pathPrefix != null) pathPrefix.setTitle(R.string.pref_title_cloud_path_prefix);
            } else {
                if (accessKey != null) accessKey.setTitle(R.string.pref_title_cloud_access_key);
                if (secretKey != null) secretKey.setTitle(R.string.pref_title_cloud_secret_key);
                if (region != null) region.setTitle(R.string.pref_title_cloud_region);
                if (bucket != null) bucket.setTitle(R.string.pref_title_cloud_bucket);
                if (endpoint != null) endpoint.setTitle(R.string.pref_title_cloud_endpoint);
                if (pathPrefix != null) pathPrefix.setTitle(R.string.pref_title_cloud_path_prefix);
            }
        }

        private String buildProbeUrl(String provider, String endpoint, String bucket, String region) {
            String ep = endpoint == null ? "" : endpoint.trim();
            String b = bucket == null ? "" : bucket.trim();
            String rg = region == null ? "" : region.trim();
            if ("gcs".equalsIgnoreCase(provider)) {
                if (ep.isEmpty()) ep = "https://storage.googleapis.com";
                return joinUrl(ep, b);
            } else if ("azure".equalsIgnoreCase(provider)) {
                if (ep.isEmpty()) return null;
                String base = joinUrl(ep, b);
                return base.contains("?") ? base + "&comp=list" : base + "?comp=list";
            } else if ("oss".equalsIgnoreCase(provider)) {
                if (ep.isEmpty()) {
                    if (b.isEmpty() || rg.isEmpty()) return null;
                    return "https://" + b + "." + rg + ".aliyuncs.com";
                }
                return joinUrl(ep, b);
            } else if ("cos".equalsIgnoreCase(provider)) {
                if (ep.isEmpty()) {
                    if (b.isEmpty() || rg.isEmpty()) return null;
                    return "https://" + b + ".cos." + rg + ".myqcloud.com";
                }
                return joinUrl(ep, b);
            }
            return ep;
        }

        private String joinUrl(String a, String b) {
            if (a == null || a.isEmpty()) return b;
            if (b == null || b.isEmpty()) return a;
            String aa = a;
            while (aa.endsWith("/")) aa = aa.substring(0, aa.length() - 1);
            String bb = b;
            while (bb.startsWith("/")) bb = bb.substring(1);
            return aa + "/" + bb;
        }
    }
}
