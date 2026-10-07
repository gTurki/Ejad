package com.example.ejadwebapplication.Client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

// Component مو Service: ما يمثّل entity، مجرد غلاف لـ Gmail SMTP
// كل ميثود عامة عليها @Async: الإيميل يطلع في thread ثاني، فما يأخّر الرد ولا يفشّل العملية
// نستقبل String مو entity، لأن الـ thread الثاني ما عنده session ويطيح لو لمس علاقة lazy
@Slf4j
@Component
@RequiredArgsConstructor
public class EmailSender {

    private final JavaMailSender mailSender;

    // Gmail يرفض أي from غير الإيميل اللي مسجّلين فيه
    @Value("${spring.mail.username}")
    private String from;

    @Async
    public void sendWelcome(String to, String fullName) {
        send(to, "Welcome to Ejad",
                "Hi " + fullName + ",\n\n"
                        + "Your account has been created successfully. "
                        + "You can now report lost items and we will notify you when a possible match is found.");
    }

    // عند التسجيل وعند النقل لمكان جديد
    @Async
    public void sendStaffPending(String to, String fullName, String locationName) {
        send(to, "Your staff account is waiting for verification",
                "Hi " + fullName + ",\n\n"
                        + "Your staff account at " + locationName + " is waiting for admin verification. "
                        + "We will email you once it is verified.");
    }

    @Async
    public void sendStaffVerified(String to, String fullName, String locationName) {
        send(to, "Your staff account has been verified",
                "Hi " + fullName + ",\n\n"
                        + "Your staff account at " + locationName + " has been verified. "
                        + "You can now create FOUND reports and receive new report notifications.");
    }

    @Async
    public void sendStaffUnverified(String to, String fullName) {
        send(to, "Your staff verification has been removed",
                "Hi " + fullName + ",\n\n"
                        + "Your staff verification has been removed by the admin. "
                        + "Please contact the admin if you think this is a mistake.");
    }

    // لإشعارات البلاغات (MATCH_FOUND و MATCH_CONFIRMED)، الرسالة نفسها اللي تنحفظ في Notification
    @Async
    public void sendReportUpdate(String to, String fullName, String subject, String message) {
        send(to, subject, "Hi " + fullName + ",\n\n" + message);
    }

    // تأكيد إنشاء البلاغ لصاحبه
    @Async
    public void sendReportCreated(String to, String fullName, String type, String title) {
        send(to, type + " report created",
                "Hi " + fullName + ",\n\n"
                        + "Your " + type + " report \"" + title + "\" has been created successfully. "
                        + "We will notify you when a possible match is found.");
    }

    // private وبدون @Async: تنادى من داخل الكلاس، فالـ @Async ما بيشتغل عليها أصلاً (self-invocation)
    private void send(String to, String subject, String body) {
        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setFrom(from);
        mail.setTo(to);
        mail.setSubject(subject);
        mail.setText(body + "\n\nEjad - Lost & Found");
        try {
            mailSender.send(mail);
        } catch (MailException e) {
            // فشل الإيميل ما يفشّل العملية، نفس فلسفة الـ AI
            log.warn("Email to {} failed: {}", to, e.getClass().getSimpleName());
        }
    }
}
