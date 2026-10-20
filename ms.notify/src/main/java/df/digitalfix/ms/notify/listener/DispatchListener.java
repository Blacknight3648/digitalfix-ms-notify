package df.digitalfix.ms.notify.listener;

import com.rabbitmq.client.Channel;
import df.digitalfix.ms.notify.dto.DispatchTicketDTO;
import df.digitalfix.ms.notify.dto.EventEnvelopeDTO;
import df.digitalfix.ms.notify.service.DispatchService;
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
public class DispatchListener {

    private final DispatchService dispatchService;
    private final ManejadorFallosMensajeria manejadorFallos;

    @RabbitListener(id = ListenerIds.DISPATCH, queues = "${app.rabbitmq.queues.dispatch:q.cmd.dispatch}")
    public void consumeDispatchTicket(
            @Payload EventEnvelopeDTO<DispatchTicketDTO> envelope,
            Message message,
            Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {

        log.info("[COLA DESPACHO - q.cmd.dispatch] Solicitud de ticket técnico en terreno recibida | Evento: {} | EventID: {} | Orden #: {}", 
                envelope.getEventType(), 
                envelope.getEventId(),
                envelope.getData() != null ? envelope.getData().getWorkOrderId() : "N/A");

        try {
            if (envelope.getData() == null) {
                throw new InvalidPayloadException("El evento " + envelope.getEventId() + " llegó sin 'data'");
            }
            // Procesa la generación del ticket técnico de terreno
            dispatchService.processDispatchTicket(envelope.getData());

            // ACK Manual
            channel.basicAck(deliveryTag, false);
            log.info("[COLA DESPACHO - q.cmd.dispatch] ACK confirmado. Ticket técnico generado exitosamente para EventID: {}", envelope.getEventId());

        } catch (Exception e) {
            log.error("[COLA DISPATCH] Error al procesar el evento (EventID: {}): {}", envelope.getEventId(), e.getMessage());
            // Error definitivo → DLQ; error transitorio → reintento con límite (ver ManejadorFallosMensajeria)
            manejadorFallos.resolver(message, channel, deliveryTag, e);
        }
    }
}
