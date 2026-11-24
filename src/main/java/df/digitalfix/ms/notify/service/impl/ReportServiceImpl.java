package df.digitalfix.ms.notify.service.impl;

import df.digitalfix.ms.notify.dto.ReportGenerationDTO;
import df.digitalfix.ms.notify.service.ReportService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class ReportServiceImpl implements ReportService {

    @Override
    public void generateAndSendReport(ReportGenerationDTO reportDTO) {
        log.info("Procesando solicitud de generación de informe ({}) para la orden #{}", 
                reportDTO.getReportType(), reportDTO.getWorkOrderId());

        // Simulación de procesamiento asíncrono de certificado/informe PDF
        log.info("Generando documento PDF tipo: {}...", reportDTO.getReportType());
        log.info("Informe PDF adjuntado y enviado al correo: {}", reportDTO.getRecipientEmail());
    }
}
