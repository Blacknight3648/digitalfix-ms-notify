package df.digitalfix.ms.notify.messaging;

import df.digitalfix.ms.notify.dto.EventEnvelopeDTO;
import df.digitalfix.ms.notify.exceptions.InvalidPayloadException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Aplica las anotaciones de Bean Validation del sobre y de su {@code data} (@Valid en cascada)
 * antes de procesar un mensaje. Un mensaje inválido no mejora reintentándolo, por eso se lanza
 * {@link InvalidPayloadException}: {@link ManejadorFallosMensajeria} lo trata como error
 * definitivo (NACK sin requeue → DLQ) y el detalle queda en el log.
 */
@Component
@RequiredArgsConstructor
public class ValidadorMensajes {

    private final Validator validator;

    public <T> T validar(EventEnvelopeDTO<T> envelope) {
        if (envelope == null) {
            throw new InvalidPayloadException("El mensaje llegó vacío");
        }
        Set<ConstraintViolation<EventEnvelopeDTO<T>>> violaciones = validator.validate(envelope);
        if (!violaciones.isEmpty()) {
            String detalle = violaciones.stream()
                    .sorted(Comparator.comparing(v -> v.getPropertyPath().toString()))
                    .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                    .collect(Collectors.joining("; "));
            throw new InvalidPayloadException("Evento " + envelope.getEventId() + " inválido → " + detalle);
        }
        return envelope.getData();
    }
}
