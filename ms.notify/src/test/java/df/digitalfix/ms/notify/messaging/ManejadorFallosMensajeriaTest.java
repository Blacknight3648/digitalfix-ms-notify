package df.digitalfix.ms.notify.messaging;

import com.rabbitmq.client.Channel;
import df.digitalfix.ms.notify.config.TopologiaRabbitProperties;
import df.digitalfix.ms.notify.exceptions.EmailDeliveryException;
import df.digitalfix.ms.notify.exceptions.InvalidPayloadException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.MessageConversionException;

import java.io.IOException;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

// Política de errores de los consumidores: definitivo → NACK sin requeue (DLQ); transitorio →
// reintento con límite; agotado → NACK sin requeue (DLQ). Nunca requeue=true (bucle infinito).
class ManejadorFallosMensajeriaTest {

    private static final long TAG = 42L;
    private static final String COLA = "q.cmd.email";

    private RabbitTemplate rabbitTemplate;
    private Channel canal;
    private ManejadorFallosMensajeria manejador;

    @BeforeEach
    void setUp() {
        rabbitTemplate = mock(RabbitTemplate.class);
        canal = mock(Channel.class);
        TopologiaRabbitProperties topologia = new TopologiaRabbitProperties(null, null, Duration.ofHours(24),
                new TopologiaRabbitProperties.Reintentos(3));
        manejador = new ManejadorFallosMensajeria(rabbitTemplate, topologia);
    }

    private static Message mensaje(Integer reintentosPrevios) {
        MessageProperties props = new MessageProperties();
        props.setConsumerQueue(COLA);
        if (reintentosPrevios != null) {
            props.setHeader(ManejadorFallosMensajeria.HEADER_REINTENTOS, reintentosPrevios);
        }
        return new Message("{}".getBytes(), props);
    }

    @Test
    void payloadInvalidoVaDirectoALaDlq() throws IOException {
        manejador.resolver(mensaje(null), canal, TAG, new InvalidPayloadException("data.emailTo: formato inválido"));

        verify(canal).basicNack(TAG, false, false);
        verify(canal, never()).basicAck(anyLong(), anyBoolean());
        verifyNoInteractions(rabbitTemplate);
    }

    @Test
    void jsonQueNoCalzaEsErrorDefinitivoAunqueVengaEnvuelto() throws IOException {
        Exception envuelto = new RuntimeException("listener", new MessageConversionException("JSON mal formado"));

        manejador.resolver(mensaje(null), canal, TAG, envuelto);

        verify(canal).basicNack(TAG, false, false);
        verifyNoInteractions(rabbitTemplate);
    }

    @Test
    void errorTransitorioSeReencolaConElContadorIncrementadoYAckDelOriginal() throws IOException {
        manejador.resolver(mensaje(1), canal, TAG, new EmailDeliveryException("SMTP no responde"));

        ArgumentCaptor<Message> copia = ArgumentCaptor.forClass(Message.class);
        verify(rabbitTemplate).send(eq(""), eq(COLA), copia.capture());
        assertThat((Integer) copia.getValue().getMessageProperties().getHeader(ManejadorFallosMensajeria.HEADER_REINTENTOS))
                .isEqualTo(2);
        verify(canal).basicAck(TAG, false);
        verify(canal, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
    }

    @Test
    void reintentosAgotadosTerminanEnLaDlq() throws IOException {
        manejador.resolver(mensaje(3), canal, TAG, new EmailDeliveryException("SMTP no responde"));

        verify(canal).basicNack(TAG, false, false);
        verify(rabbitTemplate, never()).send(anyString(), anyString(), any(Message.class));
    }
}
