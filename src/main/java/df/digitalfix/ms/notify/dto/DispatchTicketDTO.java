package df.digitalfix.ms.notify.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DispatchTicketDTO {

    @NotNull(message = "El ID de la orden de trabajo es obligatorio")
    private Long workOrderId;

    @NotBlank(message = "El ID del técnico asignado es obligatorio")
    private String technicianId;

    @NotBlank(message = "La dirección de atención es obligatoria")
    private String serviceAddress;

    @NotBlank(message = "El teléfono del cliente es obligatorio")
    private String clientPhone;

    @NotNull(message = "La fecha programada de visita es obligatoria")
    private LocalDateTime scheduledDate;

    @NotBlank(message = "La descripción del problema/servicio es obligatoria")
    private String serviceDescription;
}
