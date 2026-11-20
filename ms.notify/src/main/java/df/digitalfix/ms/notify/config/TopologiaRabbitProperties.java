package df.digitalfix.ms.notify.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Propiedades de consumo RabbitMQ ({@code app.rabbitmq} en application.yaml).
 * <p>
 * La topología completa (exchanges, colas, DLQ, TTL y bindings) es declarada centralizadamente
 * por {@code ms-admin-queues}; este microservicio opera en modo pasivo y únicamente referencia
 * las colas que consume/verifica y su política de reintentos.
 *
 * @param flujos un {@link Flujo} por caso de uso con la cola principal y su DLQ asociada
 * @param retry  reintentos de errores transitorios antes de derivar a la DLQ
 */
@Validated
@ConfigurationProperties(prefix = "app.rabbitmq")
public record TopologiaRabbitProperties(
        @Valid @NotNull Flujos flujos,
        @Valid @NotNull Reintentos retry
) {

    public record Flujos(
            @Valid @NotNull Flujo email,
            @Valid @NotNull Flujo dispatch,
            @Valid @NotNull Flujo report
    ) {
    }

    /**
     * Colas preexistentes de un flujo de consumo y su DLQ asociada.
     */
    public record Flujo(
            @NotBlank String cola,
            @NotBlank String dlq
    ) {
    }

    public record Reintentos(@Min(0) int maxAttempts) {
    }
}
