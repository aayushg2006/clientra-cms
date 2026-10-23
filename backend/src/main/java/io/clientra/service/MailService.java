package io.clientra.service;

import io.clientra.config.AppProperties;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Sends HTML email through the SMTP server configured in application-local.yml.
 * When app.mail.enabled is false the message is only written to the log, so the app works without SMTP.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MailService {

    private final JavaMailSender sender;
    private final AppProperties props;

    public boolean isEnabled() {
        return props.mail().enabled();
    }

    @Async
    public void send(String to, String subject, String heading, String bodyHtml) {
        if (!isEnabled()) {
            log.info("[mail disabled] To: {} | Subject: {} | {}", to, subject, bodyHtml.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " "));
            return;
        }
        try {
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
            helper.setFrom(props.mail().from());
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(wrap(heading, bodyHtml), true);
            sender.send(message);
            log.info("Email sent to {}: {}", to, subject);
        } catch (Exception e) {
            log.error("Failed to send email to {} ({}): {}", to, subject, e.getMessage());
        }
    }

    private String wrap(String heading, String body) {
        return """
                <div style="font-family:Inter,Segoe UI,Arial,sans-serif;background:#F7F4EE;padding:32px">
                  <div style="max-width:560px;margin:auto;background:#fff;border-radius:16px;overflow:hidden;border:1px solid #E8E1D4">
                    <div style="background:#0E4A43;color:#fff;padding:18px 24px;font-weight:700;font-size:18px">
                      <span style="display:inline-block;background:#fff;color:#14776B;border-radius:8px;width:28px;height:28px;
                        text-align:center;line-height:28px;margin-right:8px">C</span>Clientra CMS
                    </div>
                    <div style="padding:24px;color:#1F2933;font-size:14px;line-height:1.6">
                      <h2 style="margin:0 0 12px;font-size:18px">%s</h2>
                      %s
                    </div>
                    <div style="padding:14px 24px;background:#FAF8F4;color:#6B7280;font-size:12px">
                      This is an automated message from Clientra CMS.
                    </div>
                  </div>
                </div>
                """.formatted(heading, body);
    }
}
