package df.digitalfix.ms.notify.listener;

import com.rabbitmq.client.Channel;
import df.digitalfix.ms.notify.messaging.ListenerIds;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
public class DlqListener {

    @RabbitListener(id = ListenerIds.DLQ, messageConverter = "rawMessageConverter", queues = {
            "#{emailDlq.name}",
            "#{dispatchDlq.name}",
            "#{reportDlq.name}"
    })
    public void processQuarantineMessage(
            Message message,
            Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag,
            @Header(AmqpHeaders.CONSUMER_QUEUE) String queueName) throws IOException {

        String payload = new String(message.getBody());

        log.error("[ALERTA DLQ CUARENTENA] Mensaje fallido detectado en la cola muerta '{}'", queueName);
        log.error("   -> Payload afectado: {}", payload);
        log.error("   -> Encabezados de error (Death Headers / x-death): {}", message.getMessageProperties().getHeaders().get("x-death"));

        // Se confirma el consumo en la DLQ para registrar la auditoría
        channel.basicAck(deliveryTag, false);
        log.info("[ALERTA DLQ CUARENTENA] ACK manual emitido para auditoría y desalojo en '{}'", queueName);
    }
}
