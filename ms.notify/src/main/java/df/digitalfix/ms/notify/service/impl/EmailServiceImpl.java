package df.digitalfix.ms.notify.service.impl;

import df.digitalfix.ms.notify.dto.EmailNotificationDTO;
import df.digitalfix.ms.notify.exceptions.EmailDeliveryException;
import df.digitalfix.ms.notify.service.EmailService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.nio.charset.StandardCharsets;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    @Value("${spring.mail.username:no-reply@digitalfix.cl}")
    private String fromEmail;

    @Override
    public void sendHtmlEmail(EmailNotificationDTO emailDTO) {
        log.info("Iniciando procesamiento de email para orden ID: {} destinado a: {}", 
                emailDTO.getWorkOrderId(), emailDTO.getEmailTo());

        try {
            // 1. Preparar el contexto de variables para la plantilla Thymeleaf
            Context context = new Context();
            context.setVariable("clientName", emailDTO.getClientName());
            context.setVariable("workOrderId", emailDTO.getWorkOrderId());
            
            if (emailDTO.getTemplateAttributes() != null) {
                emailDTO.getTemplateAttributes().forEach(context::setVariable);
            }

            // 2. Renderizar la plantilla HTML
            String htmlContent = templateEngine.process("email/" + emailDTO.getTemplateName(), context);

            // 3. Crear y configurar el mensaje MimeMessage
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    message, 
                    MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED, 
                    StandardCharsets.UTF_8.name()
            );

            helper.setFrom(fromEmail);
            helper.setTo(emailDTO.getEmailTo());
            helper.setSubject(emailDTO.getSubject());
            helper.setText(htmlContent, true); // true = HTML

            // 4. Enviar correo vía SMTP
            mailSender.send(message);
            log.info("Email enviado exitosamente a {} para la orden #{}", emailDTO.getEmailTo(), emailDTO.getWorkOrderId());

        } catch (MessagingException e) {
            log.error("Error al construir o enviar el correo a {}: {}", emailDTO.getEmailTo(), e.getMessage(), e);
            throw new EmailDeliveryException("Error en el despacho de correo SMTP", e);
        }
    }
}
