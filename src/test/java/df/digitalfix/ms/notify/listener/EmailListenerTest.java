package df.digitalfix.ms.notify.listener;

import com.rabbitmq.client.Channel;
import df.digitalfix.ms.notify.dto.EmailNotificationDTO;
import df.digitalfix.ms.notify.dto.EventEnvelopeDTO;
import df.digitalfix.ms.notify.exceptions.EmailDeliveryException;
import df.digitalfix.ms.notify.messaging.ValidadorMensajes;
import df.digitalfix.ms.notify.service.EmailService;
import jakarta.validation.Validation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitListener;

import java.io.IOException;
import java.lang.reflect.Method;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class EmailListenerTest {

    private static final long DELIVERY_TAG = 7L;

    private EmailService emailService;
    private Channel channel;
    private EmailListener listener;

    @BeforeEach
    void setUp() {
        emailService = mock(EmailService.class);
        channel = mock(Channel.class);
        ValidadorMensajes validador = new ValidadorMensajes(Validation.buildDefaultValidatorFactory().getValidator());
        listener = new EmailListener(emailService, validador);
    }

    @Test
    void escuchaColaQCmdEmail() throws NoSuchMethodException {
        Method method = EmailListener.class.getMethod(
                "consumeEmailNotification",
                EventEnvelopeDTO.class, Message.class, Channel.class, long.class);
        RabbitListener annotation = method.getAnnotation(RabbitListener.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.queues()).containsExactly("q.cmd.email");
    }

    @Test
    void confirmaAckCuandoElCorreoSeEnviaCorrectamente() throws IOException {
        EventEnvelopeDTO<EmailNotificationDTO> envelope = envelopeValido();

        listener.consumeEmailNotification(envelope, mensaje(), channel, DELIVERY_TAG);

        verify(emailService).sendHtmlEmail(envelope.getData());
        verify(channel).basicAck(DELIVERY_TAG, false);
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
    }

    @Test
    void enviaNackSinRequeueCuandoFallaElEnvioOValidacion() throws IOException {
        EventEnvelopeDTO<EmailNotificationDTO> envelope = envelopeValido();
        doThrow(new EmailDeliveryException("SMTP no disponible"))
                .when(emailService).sendHtmlEmail(any());

        listener.consumeEmailNotification(envelope, mensaje(), channel, DELIVERY_TAG);

        verify(channel).basicNack(DELIVERY_TAG, false, false);
        verify(channel, never()).basicAck(anyLong(), anyBoolean());
    }

    private static Message mensaje() {
        MessageProperties props = new MessageProperties();
        props.setReceivedExchange("cmd.direct");
        props.setReceivedRoutingKey("email.send");
        return new Message("{}".getBytes(), props);
    }

    private static EventEnvelopeDTO<EmailNotificationDTO> envelopeValido() {
        return EventEnvelopeDTO.<EmailNotificationDTO>builder()
                .eventId("evt-1")
                .eventType("EMAIL_NOTIFICATION")
                .timestamp(LocalDateTime.now())
                .data(EmailNotificationDTO.builder()
                        .emailTo("cliente@digitalfix.cl")
                        .subject("Orden asignada")
                        .templateName("order-notification")
                        .workOrderId(100L)
                        .clientName("Juan Pérez")
                        .build())
                .build();
    }
}
