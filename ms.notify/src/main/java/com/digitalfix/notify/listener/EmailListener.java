package com.digitalfix.notify.listener;

import com.digitalfix.notify.dto.event.EventEnvelopeDTO;
import com.digitalfix.notify.dto.event.WorkOrderNotificationEvent;
import com.digitalfix.notify.service.EmailService;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class EmailListener {

    private final EmailService emailService;

    @RabbitListener(queues = "${digitalfix.rabbitmq.queues.email:q.cmd.email}")
    public void onEmailRequest(EventEnvelopeDTO<WorkOrderNotificationEvent> envelope, Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        log.info("Mensaje recibido en q.cmd.email. eventId: {}, deliveryTag: {}", envelope.getEventId(), deliveryTag);

        try {
            WorkOrderNotificationEvent payload = envelope.getPayload();
            
            // Invocación al servicio de negocio para procesar y enviar el correo
            emailService.sendWorkOrderEmail(payload);

            // Confirmación exitosa (ACK)
            channel.basicAck(deliveryTag, false);
            log.info("Mensaje [eventId: {}] procesado y confirmado con ACK", envelope.getEventId());

        } catch (Exception e) {
            log.error("Error al procesar el correo para la orden #{}. Causa: {}", 
                    envelope.getPayload() != null ? envelope.getPayload().getWorkOrderId() : "N/A", e.getMessage());

            // Rechazo explícito sin reencolamiento (requeue = false) -> Deriva el mensaje a la DLQ
            channel.basicNack(deliveryTag, false, false);
            log.warn("Mensaje [eventId: {}] derivado a la DLQ mediante NACK (requeue=false)", envelope.getEventId());
        }
    }
}
