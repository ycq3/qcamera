package com.pipiqiang.qcamera.app;

import java.io.File;
import java.util.List;
import java.util.Properties;

import javax.mail.Authenticator;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;

public class EmailSender {

    private final SettingsManager settings;

    public EmailSender(SettingsManager settings) {
        this.settings = settings;
    }

    public void send(String to, String subject, String bodyHtml, List<File> attachments) throws Exception {
        if (!settings.isSmtpEnabled()) {
            throw new IllegalStateException("SMTP disabled");
        }

        String host = settings.getSmtpHost();
        int port = settings.getSmtpPort();
        if (host == null || host.trim().isEmpty()) {
            throw new IllegalStateException("缺少SMTP主机地址");
        }
        if (port <= 0) {
            throw new IllegalStateException("SMTP端口无效");
        }

        Properties props = new Properties();
        props.put("mail.smtp.host", host);
        props.put("mail.smtp.port", Integer.toString(port));
        props.put("mail.smtp.auth", "true");
        // 超时设置（毫秒）
        props.put("mail.smtp.connectiontimeout", "15000");
        props.put("mail.smtp.timeout", "15000");
        props.put("mail.smtp.writetimeout", "15000");

        String enc = settings.getSmtpEncryption();
        if ("ssl".equalsIgnoreCase(enc)) {
            props.put("mail.smtp.ssl.enable", "true");
            props.put("mail.smtp.ssl.checkserveridentity", "true");
            props.put("mail.smtp.ssl.trust", host);
        } else if ("tls".equalsIgnoreCase(enc)) {
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.starttls.required", "true");
        }

        // auth mechanisms
        String mech = settings.getSmtpAuthMethod();
        if (mech != null && !mech.isEmpty()) {
            String value;
            switch (mech) {
                case "plain": value = "PLAIN"; break;
                case "login": value = "LOGIN"; break;
                case "cram_md5": value = "CRAM-MD5"; break;
                default: value = "LOGIN"; break;
            }
            props.put("mail.smtp.auth.mechanisms", value);
        }

        Session session = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(settings.getSmtpUsername(), settings.getSmtpPassword());
            }
        });

        MimeMessage message = new MimeMessage(session);
        String from = settings.getSmtpFrom();
        if (from == null || from.isEmpty()) {
            from = settings.getSmtpUsername();
        }
        message.setFrom(new InternetAddress(from));
        message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
        message.setSubject(subject, "UTF-8");

        MimeMultipart multipart = new MimeMultipart("mixed");

        MimeBodyPart bodyPart = new MimeBodyPart();
        if (settings.isSmtpHtmlEnabled()) {
            bodyPart.setContent(bodyHtml, "text/html; charset=UTF-8");
        } else {
            bodyPart.setText(bodyHtml, "UTF-8");
        }
        multipart.addBodyPart(bodyPart);

        if (attachments != null) {
            for (File file : attachments) {
                if (file == null || !file.exists()) continue;
                MimeBodyPart attach = new MimeBodyPart();
                attach.attachFile(file);
                multipart.addBodyPart(attach);
            }
        }

        message.setContent(multipart);
        int retries = Math.max(1, settings.getSmtpRetryCount());
        int backoffSec = Math.max(1, settings.getSmtpRetryBackoffSec());
        Exception last = null;
        for (int i = 0; i < retries; i++) {
            try {
                Transport.send(message);
                last = null;
                break;
            } catch (MessagingException me) {
                last = me;
                if (i == retries - 1) break;
                try { Thread.sleep(backoffSec * 1000L); } catch (InterruptedException ignored) {}
            } catch (Exception e) {
                last = e;
                if (i == retries - 1) break;
                try { Thread.sleep(backoffSec * 1000L); } catch (InterruptedException ignored) {}
            }
        }
        if (last != null) {
            throw last;
        }
    }
}