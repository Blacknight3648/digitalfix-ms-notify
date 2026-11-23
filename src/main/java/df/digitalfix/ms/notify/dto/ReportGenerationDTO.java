package df.digitalfix.ms.notify.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// Lector tolerante: el productor puede enviar campos extra (crewId, orderStatus...) sin que el
// mensaje se rechace y termine en la DLQ.
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportGenerationDTO {

    @NotNull(message = "El ID de la orden de trabajo es obligatorio")
    private Long workOrderId;

    @NotBlank(message = "El tipo de reporte es obligatorio (ej. CERTIFICADO_TECNICO, INFORME_CIERRE)")
    private String reportType;

    @NotBlank(message = "El correo de destino para adjuntar el PDF es obligatorio")
    private String recipientEmail;
}
