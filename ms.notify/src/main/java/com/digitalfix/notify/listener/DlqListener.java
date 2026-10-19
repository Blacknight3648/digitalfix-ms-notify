package com.digitalfix.notify.listener;

import com.digitalfix.notify.dto.event.EventEnvelopeDTO;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
public class DlqListener {

    /**
     * Consume los mensajes en estado de fallo provenientes de las Dead Letter Queues
     * para auditoría, alerta o reintento diferido.
     */
    @RabbitListener(queues = {
            "${digitalfix.rabbitmq.queues.email-dlq:q.cmd.email.dlq}",
            "${digitalfix.rabbitmq.queues.dispatch-dlq:q.cmd.dispatch.dlq}"
    })
    @SuppressWarnings("rawtypes")
    public void onDeadLetterMessage(EventEnvelopeDTO envelope, Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        String receivedQueue = message.getMessageProperties().getConsumerQueue();

        log.error("ALERT — Mensaje capturado en Dead Letter Queue [{}] | eventId: {} | payload: {}", 
                receivedQueue, envelope.getEventId(), envelope.getPayload());

        // ACK manual para limpiar el mensaje fallido de la DLQ una vez auditado
        channel.basicAck(deliveryTag, false);
    }
}
