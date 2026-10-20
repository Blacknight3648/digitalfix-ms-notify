package df.digitalfix.ms.notify.listener;

import com.rabbitmq.client.Channel;
import df.digitalfix.ms.notify.dto.EmailNotificationDTO;
import df.digitalfix.ms.notify.dto.EventEnvelopeDTO;
import df.digitalfix.ms.notify.service.EmailService;
import df.digitalfix.ms.notify.exceptions.InvalidPayloadException;
import df.digitalfix.ms.notify.messaging.ListenerIds;
import df.digitalfix.ms.notify.messaging.ManejadorFallosMensajeria;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
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
    private final ManejadorFallosMensajeria manejadorFallos;

    @RabbitListener(id = ListenerIds.EMAIL, queues = "${app.rabbitmq.queues.email:q.cmd.email}")
    public void consumeEmailNotification(
            @Payload EventEnvelopeDTO<EmailNotificationDTO> envelope,
            Message message,
            Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {

        log.info("[COLA EMAIL - q.cmd.email] Nueva notificación recibida para despacho | Evento: {} | EventID: {} | Destinatario: {}", 
                envelope.getEventType(), 
                envelope.getEventId(), 
                envelope.getData() != null ? envelope.getData().getEmailTo() : "N/A");

        try {
            if (envelope.getData() == null) {
                throw new InvalidPayloadException("El evento " + envelope.getEventId() + " llegó sin 'data'");
            }
            // Procesa el envío de la plantilla HTML mediante JavaMailSender
            emailService.sendHtmlEmail(envelope.getData());

            // ACK Manual: Confirma procesamiento exitoso a RabbitMQ
            channel.basicAck(deliveryTag, false);
            log.info("[COLA EMAIL - q.cmd.email] ACK confirmado. Correo despachado exitosamente para EventID: {}", envelope.getEventId());

        } catch (Exception e) {
            log.error("[COLA EMAIL] Error al procesar el evento (EventID: {}): {}", envelope.getEventId(), e.getMessage());
            // Error definitivo → DLQ; error transitorio → reintento con límite (ver ManejadorFallosMensajeria)
            manejadorFallos.resolver(message, channel, deliveryTag, e);
        }
    }
}
