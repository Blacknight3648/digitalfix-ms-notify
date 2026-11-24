package df.digitalfix.ms.notify.listener;

import com.rabbitmq.client.Channel;
import df.digitalfix.ms.notify.dto.DispatchTicketDTO;
import df.digitalfix.ms.notify.dto.EventEnvelopeDTO;
import df.digitalfix.ms.notify.messaging.ValidadorMensajes;
import df.digitalfix.ms.notify.service.DispatchService;
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

class DispatchListenerTest {

    private static final long DELIVERY_TAG = 15L;

    private DispatchService dispatchService;
    private Channel channel;
    private DispatchListener listener;

    @BeforeEach
    void setUp() {
        dispatchService = mock(DispatchService.class);
        channel = mock(Channel.class);
        ValidadorMensajes validador = new ValidadorMensajes(Validation.buildDefaultValidatorFactory().getValidator());
        listener = new DispatchListener(dispatchService, validador);
    }

    @Test
    void escuchaColaQCmdDispatch() throws NoSuchMethodException {
        Method method = DispatchListener.class.getMethod(
                "consumeDispatchTicket",
                EventEnvelopeDTO.class, Message.class, Channel.class, long.class);
        RabbitListener annotation = method.getAnnotation(RabbitListener.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.queues()).containsExactly("q.cmd.dispatch");
    }

    @Test
    void confirmaAckCuandoElTicketSeEmiteCorrectamente() throws IOException {
        EventEnvelopeDTO<DispatchTicketDTO> envelope = envelopeValido();

        listener.consumeDispatchTicket(envelope, mensaje(), channel, DELIVERY_TAG);

        verify(dispatchService).processDispatchTicket(envelope.getData());
        verify(channel).basicAck(DELIVERY_TAG, false);
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
    }

    @Test
    void enviaNackSinRequeueCuandoFallaLaEmisionOValidacion() throws IOException {
        EventEnvelopeDTO<DispatchTicketDTO> envelope = envelopeValido();
        doThrow(new RuntimeException("Error al emitir ticket"))
                .when(dispatchService).processDispatchTicket(any());

        listener.consumeDispatchTicket(envelope, mensaje(), channel, DELIVERY_TAG);

        verify(channel).basicNack(DELIVERY_TAG, false, false);
        verify(channel, never()).basicAck(anyLong(), anyBoolean());
    }

    private static Message mensaje() {
        MessageProperties props = new MessageProperties();
        props.setReceivedExchange("cmd.direct");
        props.setReceivedRoutingKey("dispatch.ticket");
        return new Message("{}".getBytes(), props);
    }

    private static EventEnvelopeDTO<DispatchTicketDTO> envelopeValido() {
        return EventEnvelopeDTO.<DispatchTicketDTO>builder()
                .eventId("evt-2")
                .eventType("DISPATCH_TICKET")
                .timestamp(LocalDateTime.now())
                .data(DispatchTicketDTO.builder()
                        .workOrderId(200L)
                        .technicianId("TECH-01")
                        .serviceAddress("Av. Providencia 1234")
                        .clientPhone("+56912345678")
                        .scheduledDate(LocalDateTime.now().plusDays(1))
                        .serviceDescription("Revisión fibra óptica")
                        .build())
                .build();
    }
}
