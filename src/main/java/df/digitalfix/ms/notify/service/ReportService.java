package df.digitalfix.ms.notify.service;

import df.digitalfix.ms.notify.dto.ReportGenerationDTO;

public interface ReportService {
    void generateAndSendReport(ReportGenerationDTO reportDTO);
}
