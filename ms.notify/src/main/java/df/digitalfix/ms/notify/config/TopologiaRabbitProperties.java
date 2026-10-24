package df.digitalfix.ms.notify.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Única fuente de los nombres de la topología RabbitMQ ({@code app.rabbitmq} en application.yaml).
 * Ninguna clase escribe un nombre de cola, exchange o routing key: todas lo leen de aquí.
 * <p>
 * Se valida al arrancar: si falta un nombre, ms-notify no levanta (mejor que declarar una cola
 * vacía o con un nombre por defecto distinto al que usan los productores).
 *
 * @param exchanges los tres exchanges del caso: {@code cmd.direct}, {@code cmd.topic} y {@code cmd.dead.dlx}
 * @param flujos    un {@link Flujo} por caso de uso: email al cliente, despacho al técnico e informe PDF
 * @param dlqTtl    cuánto se conserva un mensaje en una DLQ antes de descartarse
 * @param retry     reintentos de errores transitorios antes de derivar a la DLQ
 */
@Validated
@ConfigurationProperties(prefix = "app.rabbitmq")
public record TopologiaRabbitProperties(
        @Valid @NotNull Exchanges exchanges,
        @Valid @NotNull Flujos flujos,
        @NotNull Duration dlqTtl,
        @Valid @NotNull Reintentos retry
) {

    public record Exchanges(
            /* Comandos punto a punto: routing key exacta (email.send, dispatch.ticket, report.gen). */
            @NotBlank String direct,
            /* Mismos comandos por patrón (email.*, dispatch.#, report.*). */
            @NotBlank String topic,
            /* Dead Letter Exchange: recibe los NACK sin requeue y los enruta a cada DLQ. */
            @NotBlank String dlx
    ) {
    }

    public record Flujos(
            @Valid @NotNull Flujo email,
            @Valid @NotNull Flujo dispatch,
            @Valid @NotNull Flujo report
    ) {
    }

    /**
     * Ruta completa de un caso de uso: el productor publica en {@code cmd.direct} con
     * {@code routingKey} (o en {@code cmd.topic} con una clave que calce con {@code patronTopic}),
     * el mensaje llega a {@code cola} y, si se rechaza, el DLX lo lleva a {@code dlq} con
     * {@code routingKeyDlq}.
     */
    public record Flujo(
            @NotBlank String cola,
            @NotBlank String dlq,
            @NotBlank String routingKey,
            @NotBlank String patronTopic,
            @NotBlank String routingKeyDlq
    ) {
    }

    public record Reintentos(@Min(0) int maxAttempts) {
    }
}
