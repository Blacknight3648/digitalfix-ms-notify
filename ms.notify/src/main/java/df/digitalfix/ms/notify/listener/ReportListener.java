package df.digitalfix.ms.notify.listener;

import com.rabbitmq.client.Channel;
import df.digitalfix.ms.notify.dto.EventEnvelopeDTO;
import df.digitalfix.ms.notify.dto.ReportGenerationDTO;
import df.digitalfix.ms.notify.service.ReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReportListener {

    private final ReportService reportService;

    @RabbitListener(queues = "${app.rabbitmq.queues.report:q.cmd.report}")
    public void consumeReportGeneration(
            @Payload EventEnvelopeDTO<ReportGenerationDTO> envelope,
            Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {

        log.info("[COLA REPORTES - q.cmd.report] Solicitud de informe/certificado PDF recibida | Evento: {} | EventID: {} | Tipo: {}", 
                envelope.getEventType(), 
                envelope.getEventId(),
                envelope.getData() != null ? envelope.getData().getReportType() : "N/A");

        try {
            // Genera y despacha el informe o certificado PDF
            reportService.generateAndSendReport(envelope.getData());

            // ACK Manual
            channel.basicAck(deliveryTag, false);
            log.info("[COLA REPORTES - q.cmd.report] ACK confirmado. Reporte generado y procesado exitosamente para EventID: {}", envelope.getEventId());

        } catch (Exception e) {
            log.error("[COLA REPORTES - q.cmd.report] Error al procesar reporte técnico (EventID: {}): {}", 
                    envelope.getEventId(), e.getMessage(), e);

            // NACK Manual -> Deriva a q.cmd.report.dlq
            channel.basicNack(deliveryTag, false, false);
            log.warn("[COLA REPORTES - q.cmd.report] NACK enviado (requeue=false). Solicitud de reporte derivada a DLQ para EventID: {}", envelope.getEventId());
        }
    }
}
