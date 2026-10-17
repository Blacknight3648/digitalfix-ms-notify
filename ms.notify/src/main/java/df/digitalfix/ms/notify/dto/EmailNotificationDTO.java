package df.digitalfix.ms.notify.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailNotificationDTO {

    @NotBlank(message = "El correo del destinatario es obligatorio")
    @Email(message = "El formato del correo electrónico no es válido")
    private String emailTo;

    @NotBlank(message = "El asunto es obligatorio")
    private String subject;

    @NotBlank(message = "El nombre de la plantilla (templateName) es obligatorio")
    private String templateName;

    @NotNull(message = "El ID de la orden de trabajo es obligatorio")
    private Long workOrderId;

    @NotBlank(message = "El nombre del cliente es obligatorio")
    private String clientName;

    // Atributos dinámicos para inyectar en la plantilla HTML (Thymeleaf)
    private Map<String, Object> templateAttributes;

    public Map<String, Object> getTemplateParams() {
        return this.templateAttributes;
    }

    public void setTemplateParams(Map<String, Object> templateParams) {
        this.templateAttributes = templateParams;
    }
}
