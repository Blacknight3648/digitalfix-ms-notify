package df.digitalfix.ms.notify.exceptions;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.thymeleaf.exceptions.TemplateInputException;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Traduce los errores de negocio a códigos HTTP. ms-admin-queues usa el código para decidir qué
 * hacer con el mensaje, así que la clasificación es parte del contrato:
 * <ul>
 *   <li><b>4xx = definitivo</b> (reintentar daría lo mismo) → el mensaje va a la DLQ.</li>
 *   <li><b>5xx = transitorio</b> (SMTP caído, error de red) → se reintenta.</li>
 * </ul>
 */
@Slf4j
@RestControllerAdvice
public class NotificacionExceptionHandler {

    /** Campo obligatorio vacío, correo mal formado, etc. */
    @ExceptionHandler(InvalidPayloadException.class)
    public ResponseEntity<Map<String, Object>> payloadInvalido(InvalidPayloadException ex) {
        log.warn("Payload inválido: {}", ex.getMessage());
        return respuesta(HttpStatus.BAD_REQUEST, "Payload inválido", ex.getMessage());
    }

    /** JSON mal formado o que no calza con el DTO. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> jsonIlegible(HttpMessageNotReadableException ex) {
        log.warn("Mensaje ilegible: {}", ex.getMostSpecificCause().getMessage());
        return respuesta(HttpStatus.BAD_REQUEST, "Mensaje ilegible", ex.getMostSpecificCause().getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> argumentoInvalido(IllegalArgumentException ex) {
        log.warn("Argumento inválido: {}", ex.getMessage());
        return respuesta(HttpStatus.BAD_REQUEST, "Argumento inválido", ex.getMessage());
    }

    /** La plantilla pedida no existe: el mensaje nunca podrá procesarse. */
    @ExceptionHandler(TemplateInputException.class)
    public ResponseEntity<Map<String, Object>> plantillaInexistente(TemplateInputException ex) {
        log.warn("Plantilla inexistente: {}", ex.getMessage());
        return respuesta(HttpStatus.UNPROCESSABLE_CONTENT, "Plantilla de correo inexistente", ex.getMessage());
    }

    /** SMTP no disponible: transitorio, ms-admin-queues reintentará. */
    @ExceptionHandler(EmailDeliveryException.class)
    public ResponseEntity<Map<String, Object>> correoNoEnviado(EmailDeliveryException ex) {
        log.error("Error transitorio de envío de correo: {}", ex.getMessage());
        return respuesta(HttpStatus.SERVICE_UNAVAILABLE, "Servidor de correo no disponible", ex.getMessage());
    }

    private static ResponseEntity<Map<String, Object>> respuesta(HttpStatus status, String error, String mensaje) {
        return ResponseEntity.status(status).body(Map.of(
                "timestamp", LocalDateTime.now().toString(),
                "status", status.value(),
                "error", error,
                "message", mensaje != null ? mensaje : error));
    }
}
