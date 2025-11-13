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

        Properties props = new Properties();
        props.put("mail.smtp.host", settings.getSmtpHost());
        props.put("mail.smtp.port", Integer.toString(settings.getSmtpPort()));
        props.put("mail.smtp.auth", "true");

        String enc = settings.getSmtpEncryption();
        if ("ssl".equalsIgnoreCase(enc)) {
            props.put("mail.smtp.ssl.enable", "true");
        } else if ("tls".equalsIgnoreCase(enc)) {
            props.put("mail.smtp.starttls.enable", "true");
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
        Transport.send(message);
    }
}