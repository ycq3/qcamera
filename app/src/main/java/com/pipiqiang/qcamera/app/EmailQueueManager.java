package com.pipiqiang.qcamera.app;

import android.content.Context;

import androidx.work.BackoffPolicy;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.WorkRequest;

import java.util.concurrent.TimeUnit;

public class EmailQueueManager {

    private final Context context;
    private final SettingsManager settings;

    public EmailQueueManager(Context context) {
        this.context = context.getApplicationContext();
        this.settings = new SettingsManager(context);
    }

    public WorkRequest enqueue(String to, String subject, String bodyHtml, String attachmentsCsv) {
        int backoffSec = settings.getSmtpRetryBackoffSec();
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(EmailWorker.class)
                .setInputData(new Data.Builder()
                        .putString(EmailWorker.KEY_TO, to)
                        .putString(EmailWorker.KEY_SUBJECT, subject)
                        .putString(EmailWorker.KEY_BODY_HTML, bodyHtml)
                        .putString(EmailWorker.KEY_ATTACHMENTS, attachmentsCsv)
                        .build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, backoffSec, TimeUnit.SECONDS)
                .build();

        WorkManager.getInstance(context).enqueue(request);
        return request;
    }
}