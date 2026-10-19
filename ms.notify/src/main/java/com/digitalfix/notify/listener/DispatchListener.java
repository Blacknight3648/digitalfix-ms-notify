package com.digitalfix.notify.listener;

import com.digitalfix.notify.dto.event.DispatchTicketEvent;
import com.digitalfix.notify.dto.event.EventEnvelopeDTO;
import com.digitalfix.notify.service.DispatchService;
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
public class DispatchListener {

    private final DispatchService dispatchService;

    @RabbitListener(queues = "${digitalfix.rabbitmq.queues.dispatch:q.cmd.dispatch}")
    public void onDispatchTicketRequest(EventEnvelopeDTO<DispatchTicketEvent> envelope, Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        log.info("Mensaje recibido en q.cmd.dispatch. eventId: {}, deliveryTag: {}", envelope.getEventId(), deliveryTag);

        try {
            DispatchTicketEvent payload = envelope.getPayload();

            // Invocación al servicio de negocio para generar el ticket de cuadrilla
            dispatchService.processDispatchTicket(payload);

            // Confirmación exitosa (ACK)
            channel.basicAck(deliveryTag, false);
            log.info("Ticket de despacho [eventId: {}] procesado y confirmado con ACK", envelope.getEventId());

        } catch (Exception e) {
            log.error("Error al generar ticket de despacho para la orden #{}. Causa: {}", 
                    envelope.getPayload() != null ? envelope.getPayload().getWorkOrderId() : "N/A", e.getMessage());

            // Rechazo explícito hacia la DLQ
            channel.basicNack(deliveryTag, false, false);
            log.warn("Mensaje de despacho [eventId: {}] derivado a q.cmd.dispatch.dlq mediante NACK", envelope.getEventId());
        }
    }
}
