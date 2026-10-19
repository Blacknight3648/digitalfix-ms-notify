package com.digitalfix.notify.listener;

import com.digitalfix.notify.dto.event.EventEnvelopeDTO;
import com.digitalfix.notify.dto.event.WorkOrderNotificationEvent;
import com.digitalfix.notify.service.EmailService;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.io.IOException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailListenerTest {

    @Mock
    private EmailService emailService;

    @Mock
    private Channel channel;

    @Mock
    private Message message;

    private EmailListener emailListener;

    private MessageProperties messageProperties;

    @BeforeEach
    void setUp() {
        emailListener = new EmailListener(emailService);
        messageProperties = new MessageProperties();
        messageProperties.setDeliveryTag(100L);
        when(message.getMessageProperties()).thenReturn(messageProperties);
    }

    @Test
    @DisplayName("Debe confirmar con basicAck cuando el envío de correo es exitoso")
    void testOnEmailRequest_Success() throws IOException {
        WorkOrderNotificationEvent event = WorkOrderNotificationEvent.builder()
                .workOrderId(555L)
                .emailTo("cliente@ejemplo.com")
                .clientName("Juan Perez")
                .subject("Orden Asignada")
                .build();

        EventEnvelopeDTO<WorkOrderNotificationEvent> envelope = EventEnvelopeDTO.<WorkOrderNotificationEvent>builder()
                .eventId("evt-001")
                .payload(event)
                .build();

        emailListener.onEmailRequest(envelope, message, channel);

        verify(emailService, times(1)).sendWorkOrderEmail(event);
        verify(channel, times(1)).basicAck(100L, false);
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
    }

    @Test
    @DisplayName("Debe rechazar con basicNack(requeue=false) cuando ocurre un error en el envío")
    void testOnEmailRequest_Failure() throws IOException {
        WorkOrderNotificationEvent event = WorkOrderNotificationEvent.builder()
                .workOrderId(555L)
                .build();

        EventEnvelopeDTO<WorkOrderNotificationEvent> envelope = EventEnvelopeDTO.<WorkOrderNotificationEvent>builder()
                .eventId("evt-002")
                .payload(event)
                .build();

        doThrow(new RuntimeException("Fallo en servicio SMTP")).when(emailService).sendWorkOrderEmail(any());

        emailListener.onEmailRequest(envelope, message, channel);

        verify(emailService, times(1)).sendWorkOrderEmail(event);
        verify(channel, times(1)).basicNack(100L, false, false);
        verify(channel, never()).basicAck(anyLong(), anyBoolean());
    }
}
