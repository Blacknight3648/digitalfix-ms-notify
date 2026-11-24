package df.digitalfix.ms.notify.service.impl;

import df.digitalfix.ms.notify.dto.EmailNotificationDTO;
import df.digitalfix.ms.notify.exceptions.EmailDeliveryException;
import df.digitalfix.ms.notify.service.EmailService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.exceptions.TemplateInputException;

import java.nio.charset.StandardCharsets;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    @Value("${app.mail.from:${spring.mail.username}}")
    private String fromEmail;

    @Override
    public void sendHtmlEmail(EmailNotificationDTO emailDTO) {
        log.info("Enviando correo desde fromEmail='{}' hacia emailTo='{}'", fromEmail, emailDTO.getEmailTo());
        log.info("Iniciando procesamiento de email para orden ID: {} destinado a: {}",
                emailDTO.getWorkOrderId(), emailDTO.getEmailTo());

        try {
            Context context = new Context();
            context.setVariable("clientName", emailDTO.getClientName());
            context.setVariable("workOrderId", emailDTO.getWorkOrderId());

            if (emailDTO.getTemplateAttributes() != null) {
                emailDTO.getTemplateAttributes().forEach(context::setVariable);
            }

            String cleanTemplateName = emailDTO.getTemplateName().replace(".html", "");
            String htmlContent = templateEngine.process("email/" + cleanTemplateName, context);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    message,
                    MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED,
                    StandardCharsets.UTF_8.name());

            helper.setFrom(fromEmail.trim(), "DigitalFix");
            helper.setTo(emailDTO.getEmailTo().trim());
            helper.setSubject(emailDTO.getSubject());
            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("Email enviado exitosamente a {} para la orden #{}", emailDTO.getEmailTo(),
                    emailDTO.getWorkOrderId());

        } catch (TemplateInputException e) {
            log.error("Plantilla de correo no encontrada ({}): {}", emailDTO.getTemplateName(), e.getMessage());
            throw new IllegalArgumentException("Plantilla de correo no encontrada: " + emailDTO.getTemplateName(), e);
        } catch (MessagingException | MailException | java.io.UnsupportedEncodingException e) {
            log.error("Error SMTP al enviar correo a {}: {}", emailDTO.getEmailTo(), e.getMessage(), e);
            throw new EmailDeliveryException("Error en el despacho de correo SMTP vía Brevo", e);
        }
    }
}