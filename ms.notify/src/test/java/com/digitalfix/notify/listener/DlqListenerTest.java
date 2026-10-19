package com.digitalfix.notify.listener;

import com.digitalfix.notify.dto.event.EventEnvelopeDTO;
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

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DlqListenerTest {

    @Mock
    private Channel channel;

    @Mock
    private Message message;

    private DlqListener dlqListener;

    private MessageProperties messageProperties;

    @BeforeEach
    void setUp() {
        dlqListener = new DlqListener();
        messageProperties = new MessageProperties();
        messageProperties.setDeliveryTag(300L);
        messageProperties.setConsumerQueue("q.cmd.email.dlq");
        when(message.getMessageProperties()).thenReturn(messageProperties);
    }

    @Test
    @DisplayName("Debe auditar el mensaje de error y confirmar con basicAck en la cola DLQ")
    @SuppressWarnings({"rawtypes", "unchecked"})
    void testOnDeadLetterMessage_Ack() throws IOException {
        EventEnvelopeDTO envelope = EventEnvelopeDTO.builder()
                .eventId("dlq-evt-001")
                .payload("Error payload snapshot")
                .build();

        dlqListener.onDeadLetterMessage(envelope, message, channel);

        verify(channel, times(1)).basicAck(300L, false);
    }
}
