package df.digitalfix.ms.notify.messaging;

import df.digitalfix.ms.notify.dto.EmailNotificationDTO;
import df.digitalfix.ms.notify.dto.EventEnvelopeDTO;
import df.digitalfix.ms.notify.exceptions.InvalidPayloadException;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ValidadorMensajesTest {

    private final ValidadorMensajes validador =
            new ValidadorMensajes(Validation.buildDefaultValidatorFactory().getValidator());

    private static EmailNotificationDTO correo(String emailTo) {
        return EmailNotificationDTO.builder()
                .emailTo(emailTo)
                .subject("DigitalFix: tu orden #15 ya tiene cuadrilla asignada")
                .templateName("order-notification")
                .workOrderId(15L)
                .clientName("Comercial Rojas Ltda.")
                .build();
    }

    private static EventEnvelopeDTO<EmailNotificationDTO> sobre(EmailNotificationDTO data) {
        return EventEnvelopeDTO.<EmailNotificationDTO>builder()
                .eventId("evt-1")
                .eventType("WORK_ORDER_STATUS_CHANGED")
                .timestamp(LocalDateTime.now())
                .data(data)
                .build();
    }

    @Test
    void mensajeValidoDevuelveSuData() {
        EmailNotificationDTO data = correo("cliente@digitalfix.cl");

        assertThat(validador.validar(sobre(data))).isSameAs(data);
    }

    @Test
    void correoMalFormadoEsPayloadInvalido() {
        assertThatThrownBy(() -> validador.validar(sobre(correo("no-es-correo"))))
                .isInstanceOf(InvalidPayloadException.class)
                .hasMessageContaining("data.emailTo");
    }

    @Test
    void sobreSinDataEsPayloadInvalido() {
        assertThatThrownBy(() -> validador.validar(sobre(null)))
                .isInstanceOf(InvalidPayloadException.class)
                .hasMessageContaining("data");
    }
}
