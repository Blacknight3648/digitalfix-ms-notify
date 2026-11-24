package df.digitalfix.ms.notify.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.Map;

@RestControllerAdvice
public class ListenerExceptionHandler {

    @ExceptionHandler(ListenerNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleListenerNotFound(ListenerNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "timestamp", LocalDateTime.now().toString(),
                "status", HttpStatus.NOT_FOUND.value(),
                "error", "Listener no encontrado",
                "message", ex.getMessage()));
    }
}
