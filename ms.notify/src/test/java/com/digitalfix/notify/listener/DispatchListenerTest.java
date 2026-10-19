package com.digitalfix.notify.listener;

import com.digitalfix.notify.dto.event.DispatchTicketEvent;
import com.digitalfix.notify.dto.event.EventEnvelopeDTO;
import com.digitalfix.notify.service.DispatchService;
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
class DispatchListenerTest {

    @Mock
    private DispatchService dispatchService;

    @Mock
    private Channel channel;

    @Mock
    private Message message;

    private DispatchListener dispatchListener;

    private MessageProperties messageProperties;

    @BeforeEach
    void setUp() {
        dispatchListener = new DispatchListener(dispatchService);
        messageProperties = new MessageProperties();
        messageProperties.setDeliveryTag(200L);
        when(message.getMessageProperties()).thenReturn(messageProperties);
    }

    @Test
    @DisplayName("Debe confirmar con basicAck cuando la generación del ticket es exitosa")
    void testOnDispatchTicketRequest_Success() throws IOException {
        DispatchTicketEvent event = DispatchTicketEvent.builder()
                .workOrderId(888L)
                .technicianId("TEC-01")
                .serviceAddress("Av. Siempre Viva 123")
                .build();

        EventEnvelopeDTO<DispatchTicketEvent> envelope = EventEnvelopeDTO.<DispatchTicketEvent>builder()
                .eventId("evt-003")
                .payload(event)
                .build();

        dispatchListener.onDispatchTicketRequest(envelope, message, channel);

        verify(dispatchService, times(1)).processDispatchTicket(event);
        verify(channel, times(1)).basicAck(200L, false);
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
    }

    @Test
    @DisplayName("Debe rechazar con basicNack(requeue=false) cuando ocurre un error en el ticket")
    void testOnDispatchTicketRequest_Failure() throws IOException {
        DispatchTicketEvent event = DispatchTicketEvent.builder()
                .workOrderId(888L)
                .build();

        EventEnvelopeDTO<DispatchTicketEvent> envelope = EventEnvelopeDTO.<DispatchTicketEvent>builder()
                .eventId("evt-004")
                .payload(event)
                .build();

        doThrow(new RuntimeException("Error en base de datos al generar ticket")).when(dispatchService).processDispatchTicket(any());

        dispatchListener.onDispatchTicketRequest(envelope, message, channel);

        verify(dispatchService, times(1)).processDispatchTicket(event);
        verify(channel, times(1)).basicNack(200L, false, false);
        verify(channel, never()).basicAck(anyLong(), anyBoolean());
    }
}
