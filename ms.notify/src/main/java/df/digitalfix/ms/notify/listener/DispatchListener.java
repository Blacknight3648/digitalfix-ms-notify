package df.digitalfix.ms.notify.listener;

import com.rabbitmq.client.Channel;
import df.digitalfix.ms.notify.dto.DispatchTicketDTO;
import df.digitalfix.ms.notify.dto.EventEnvelopeDTO;
import df.digitalfix.ms.notify.service.DispatchService;
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
public class DispatchListener {

    private final DispatchService dispatchService;

    @RabbitListener(queues = "${app.rabbitmq.queues.dispatch:q.cmd.dispatch}")
    public void consumeDispatchTicket(
            @Payload EventEnvelopeDTO<DispatchTicketDTO> envelope,
            Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {

        log.info("[COLA DESPACHO - q.cmd.dispatch] Solicitud de ticket técnico en terreno recibida | Evento: {} | EventID: {} | Orden #: {}", 
                envelope.getEventType(), 
                envelope.getEventId(),
                envelope.getData() != null ? envelope.getData().getWorkOrderId() : "N/A");

        try {
            // Procesa la generación del ticket técnico de terreno
            dispatchService.processDispatchTicket(envelope.getData());

            // ACK Manual
            channel.basicAck(deliveryTag, false);
            log.info("[COLA DESPACHO - q.cmd.dispatch] ACK confirmado. Ticket técnico generado exitosamente para EventID: {}", envelope.getEventId());

        } catch (Exception e) {
            log.error("[COLA DESPACHO - q.cmd.dispatch] Error al procesar ticket de despacho (EventID: {}): {}", 
                    envelope.getEventId(), e.getMessage(), e);

            // NACK Manual -> Deriva a q.cmd.dispatch.dlq
            channel.basicNack(deliveryTag, false, false);
            log.warn("[COLA DESPACHO - q.cmd.dispatch] NACK enviado (requeue=false). Ticket derivado a DLQ para EventID: {}", envelope.getEventId());
        }
    }
}
