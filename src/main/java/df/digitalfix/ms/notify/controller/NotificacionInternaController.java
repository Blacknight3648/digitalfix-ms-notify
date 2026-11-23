package df.digitalfix.ms.notify.controller;

import df.digitalfix.ms.notify.dto.DispatchTicketDTO;
import df.digitalfix.ms.notify.dto.EmailNotificationDTO;
import df.digitalfix.ms.notify.dto.EventEnvelopeDTO;
import df.digitalfix.ms.notify.dto.ReportGenerationDTO;
import df.digitalfix.ms.notify.service.DispatchService;
import df.digitalfix.ms.notify.service.EmailService;
import df.digitalfix.ms.notify.service.ReportService;
import df.digitalfix.ms.notify.validation.ValidadorMensajes;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Entrada de los mensajes de la cola. ms-notify no se conecta a RabbitMQ: ms-admin-queues consume
 * cada cola y entrega aquí el sobre del evento por HTTP. Este controlador solo valida y delega en la
 * lógica de negocio (correo, ticket de despacho, informe).
 * <p>
 * El código de respuesta decide el destino del mensaje en ms-admin-queues:
 * <ul>
 *   <li>2xx → ACK (procesado).</li>
 *   <li>4xx → error definitivo (payload inválido, plantilla inexistente) → DLQ sin reintentar.</li>
 *   <li>5xx → error transitorio (SMTP caído) → reintento con límite y luego DLQ.</li>
 * </ul>
 * Va bajo {@code /internal}: el BFF no la enruta, solo es accesible dentro de la red digitalfix-net.
 */
@Slf4j
@RestController
@Tag(name = "Notificaciones (interna)", description = "Procesamiento de los mensajes que entrega ms-admin-queues")
@RequestMapping("/internal/v1/notificaciones")
@RequiredArgsConstructor
public class NotificacionInternaController {

    private final EmailService emailService;
    private final DispatchService dispatchService;
    private final ReportService reportService;
    private final ValidadorMensajes validador;

    /** Correo HTML al cliente: orden creada, asignada, en camino, cerrada. */
    @PostMapping("/email")
    public ResponseEntity<Map<String, String>> email(@RequestBody EventEnvelopeDTO<EmailNotificationDTO> envelope) {
        log.info("[EMAIL] Evento {} ({}) | Orden #: {} | Destinatario: {}", envelope.getEventId(), envelope.getEventType(),
                envelope.getData() != null ? envelope.getData().getWorkOrderId() : "N/A",
                envelope.getData() != null ? envelope.getData().getEmailTo() : "N/A");
        emailService.sendHtmlEmail(validador.validar(envelope));
        return procesado(envelope, "Correo enviado");
    }

    /** Ticket de despacho / orden de terreno para la cuadrilla asignada. */
    @PostMapping("/despacho")
    public ResponseEntity<Map<String, String>> despacho(@RequestBody EventEnvelopeDTO<DispatchTicketDTO> envelope) {
        log.info("[DESPACHO] Evento {} ({}) | Orden #: {}", envelope.getEventId(), envelope.getEventType(),
                envelope.getData() != null ? envelope.getData().getWorkOrderId() : "N/A");
        dispatchService.processDispatchTicket(validador.validar(envelope));
        return procesado(envelope, "Ticket de despacho generado");
    }

    /** Informe técnico o certificado PDF de la orden. */
    @PostMapping("/informe")
    public ResponseEntity<Map<String, String>> informe(@RequestBody EventEnvelopeDTO<ReportGenerationDTO> envelope) {
        log.info("[INFORME] Evento {} ({}) | Tipo: {}", envelope.getEventId(), envelope.getEventType(),
                envelope.getData() != null ? envelope.getData().getReportType() : "N/A");
        reportService.generateAndSendReport(validador.validar(envelope));
        return procesado(envelope, "Informe generado");
    }

    private static ResponseEntity<Map<String, String>> procesado(EventEnvelopeDTO<?> envelope, String mensaje) {
        log.info("{} para el evento {}", mensaje, envelope.getEventId());
        return ResponseEntity.ok(Map.of(
                "eventId", String.valueOf(envelope.getEventId()),
                "message", mensaje));
    }
}
