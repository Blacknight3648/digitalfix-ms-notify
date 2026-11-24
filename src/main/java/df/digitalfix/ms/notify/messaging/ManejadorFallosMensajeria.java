package df.digitalfix.ms.notify.messaging;

import com.rabbitmq.client.Channel;
import df.digitalfix.ms.notify.config.TopologiaRabbitProperties;
import df.digitalfix.ms.notify.exceptions.InvalidPayloadException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.MessageConversionException;
import org.springframework.stereotype.Component;
import org.thymeleaf.exceptions.TemplateInputException;

import java.io.IOException;

/**
 * Decide qué hacer con un mensaje cuyo procesamiento falló. Todos los listeners lo usan, así la
 * política de errores es una sola y está documentada aquí:
 * <ul>
 *   <li><b>Error definitivo</b> (payload inválido, JSON que no calza, plantilla inexistente):
 *       reintentar daría el mismo resultado → NACK sin requeue → el DLX lo deriva a la DLQ.</li>
 *   <li><b>Error transitorio</b> (SMTP caído, timeout, error de red): se reintenta. El mensaje se
 *       vuelve a publicar en su misma cola con el header {@code x-retry-count} incrementado y el
 *       original se confirma con ACK. Al agotar {@code app.rabbitmq.retry.max-attempts} se hace
 *       NACK sin requeue → DLQ.</li>
 * </ul>
 * No se usa {@code basicNack(requeue=true)} porque devolvería el mensaje al inicio de la cola en un
 * bucle inmediato y sin límite de intentos.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ManejadorFallosMensajeria {

    public static final String HEADER_REINTENTOS = "x-retry-count";

    private final RabbitTemplate rabbitTemplate;
    private final TopologiaRabbitProperties topologia;

    public void resolver(Message mensaje, Channel canal, long deliveryTag, Exception error) throws IOException {
        String cola = mensaje.getMessageProperties().getConsumerQueue();

        if (esDefinitivo(error)) {
            log.warn("[{}] Error definitivo ({}): {}. NACK sin requeue → DLQ.",
                    cola, error.getClass().getSimpleName(), error.getMessage());
            canal.basicNack(deliveryTag, false, false);
            return;
        }

        int intentos = intentosPrevios(mensaje);
        int maxIntentos = topologia.retry().maxAttempts();
        if (intentos >= maxIntentos) {
            log.error("[{}] Error transitorio tras {} reintentos: {}. NACK sin requeue → DLQ.",
                    cola, intentos, error.getMessage());
            canal.basicNack(deliveryTag, false, false);
            return;
        }

        // Reintento: se publica una copia en la misma cola (exchange por defecto, routing key = cola)
        // y se confirma el original para no duplicarlo.
        mensaje.getMessageProperties().setHeader(HEADER_REINTENTOS, intentos + 1);
        rabbitTemplate.send("", cola, mensaje);
        canal.basicAck(deliveryTag, false);
        log.warn("[{}] Error transitorio: {}. Reintento {}/{} encolado.",
                cola, error.getMessage(), intentos + 1, maxIntentos);
    }

    private boolean esDefinitivo(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t instanceof InvalidPayloadException
                    || t instanceof MessageConversionException
                    || t instanceof TemplateInputException
                    || t instanceof IllegalArgumentException) {
                return true;
            }
        }
        return false;
    }

    private int intentosPrevios(Message mensaje) {
        Object valor = mensaje.getMessageProperties().getHeaders().get(HEADER_REINTENTOS);
        return valor instanceof Number n ? n.intValue() : 0;
    }
}
