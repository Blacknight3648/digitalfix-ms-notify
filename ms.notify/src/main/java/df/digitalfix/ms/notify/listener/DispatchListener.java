package df.digitalfix.ms.notify.listener;

import com.rabbitmq.client.Channel;
import df.digitalfix.ms.notify.dto.DispatchTicketDTO;
import df.digitalfix.ms.notify.dto.EventEnvelopeDTO;
import df.digitalfix.ms.notify.service.DispatchService;
import df.digitalfix.ms.notify.messaging.ListenerIds;
import df.digitalfix.ms.notify.messaging.ManejadorFallosMensajeria;
import df.digitalfix.ms.notify.messaging.ValidadorMensajes;
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
    private final ValidadorMensajes validador;

    @RabbitListener(id = ListenerIds.DISPATCH, queues = "#{dispatchQueue.name}")
    public void consumeDispatchTicket(
            @Payload EventEnvelopeDTO<DispatchTicketDTO> envelope,
            Message message,
            Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {

        log.info("[COLA DESPACHO] Solicitud de ticket técnico en terreno recibida | Evento: {} | EventID: {} | Orden #: {}", 
                envelope.getEventType(), 
                envelope.getEventId(),
                envelope.getData() != null ? envelope.getData().getWorkOrderId() : "N/A");

        try {
            // Payload inválido (campo obligatorio vacío, correo mal formado...) → error definitivo → DLQ
            DispatchTicketDTO data = validador.validar(envelope);
            // Procesa la generación del ticket técnico de terreno
            dispatchService.processDispatchTicket(data);

            // ACK Manual
            channel.basicAck(deliveryTag, false);
            log.info("[COLA DESPACHO] ACK confirmado. Ticket técnico generado exitosamente para EventID: {}", envelope.getEventId());

        } catch (Exception e) {
            log.error("[COLA DISPATCH] Error al procesar el evento (EventID: {}): {}", envelope.getEventId(), e.getMessage());
            // Error definitivo → DLQ; error transitorio → reintento con límite (ver ManejadorFallosMensajeria)
            manejadorFallos.resolver(message, channel, deliveryTag, e);
        }
    }
}
