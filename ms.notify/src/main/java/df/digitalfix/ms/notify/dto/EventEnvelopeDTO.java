package df.digitalfix.ms.notify.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventEnvelopeDTO<T> {

    @NotBlank(message = "El eventId no puede estar vacío")
    private String eventId;

    @NotBlank(message = "El eventType no puede estar vacío")
    private String eventType;

    @NotNull(message = "El timestamp es obligatorio")
    private LocalDateTime timestamp;

    private String traceId;

    private String correlationId;

    @NotNull(message = "El payload (data) es obligatorio")
    @Valid
    private T data;
}
