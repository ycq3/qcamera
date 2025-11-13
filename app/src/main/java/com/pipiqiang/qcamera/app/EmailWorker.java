package com.pipiqiang.qcamera.app;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class EmailWorker extends Worker {

    public static final String KEY_TO = "to";
    public static final String KEY_SUBJECT = "subject";
    public static final String KEY_BODY_HTML = "body_html";
    public static final String KEY_ATTACHMENTS = "attachments"; // comma-separated paths

    public EmailWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        SettingsManager settings = new SettingsManager(getApplicationContext());
        EmailSender sender = new EmailSender(settings);

        String to = getInputData().getString(KEY_TO);
        String subject = getInputData().getString(KEY_SUBJECT);
        String bodyHtml = getInputData().getString(KEY_BODY_HTML);
        String attachmentsStr = getInputData().getString(KEY_ATTACHMENTS);

        List<File> attachments = new ArrayList<>();
        if (attachmentsStr != null && !attachmentsStr.isEmpty()) {
            for (String p : attachmentsStr.split(",")) {
                File f = new File(p.trim());
                if (f.exists()) attachments.add(f);
            }
        }

        try {
            sender.send(to, subject, bodyHtml, attachments);
            return Result.success(new Data.Builder()
                    .putString("status", "sent")
                    .build());
        } catch (Exception e) {
            int retryCount = settings.getSmtpRetryCount();
            if (getRunAttemptCount() < retryCount) {
                return Result.retry();
            }
            return Result.failure(new Data.Builder()
                    .putString("error", e.getMessage())
                    .build());
        }
    }
}