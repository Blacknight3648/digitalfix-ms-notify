package df.digitalfix.ms.notify.listener;

import com.rabbitmq.client.Channel;
import df.digitalfix.ms.notify.dto.EmailNotificationDTO;
import df.digitalfix.ms.notify.dto.EventEnvelopeDTO;
import df.digitalfix.ms.notify.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class EmailListener {

    private final EmailService emailService;

    @RabbitListener(queues = "${app.rabbitmq.queues.email:q.cmd.email}")
    public void consumeEmailNotification(
            @Payload EventEnvelopeDTO<EmailNotificationDTO> envelope,
            Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {

        log.info("[COLA EMAIL - q.cmd.email] Nueva notificación recibida para despacho | Evento: {} | EventID: {} | Destinatario: {}", 
                envelope.getEventType(), 
                envelope.getEventId(), 
                envelope.getData() != null ? envelope.getData().getEmailTo() : "N/A");

        try {
            // Procesa el envío de la plantilla HTML mediante JavaMailSender
            emailService.sendHtmlEmail(envelope.getData());

            // ACK Manual: Confirma procesamiento exitoso a RabbitMQ
            channel.basicAck(deliveryTag, false);
            log.info("[COLA EMAIL - q.cmd.email] ACK confirmado. Correo despachado exitosamente para EventID: {}", envelope.getEventId());

        } catch (Exception e) {
            log.error("[COLA EMAIL - q.cmd.email] Error al procesar notificación de correo (EventID: {}): {}", 
                    envelope.getEventId(), e.getMessage(), e);

            // NACK Manual con requeue=false: Redirige automáticamente el mensaje a q.cmd.email.dlq
            channel.basicNack(deliveryTag, false, false);
            log.warn("[COLA EMAIL - q.cmd.email] NACK enviado (requeue=false). Mensaje derivado a cola de fallos (DLQ) para EventID: {}", envelope.getEventId());
        }
    }
}
