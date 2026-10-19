package com.digitalfix.notify.dto.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DispatchTicketEvent implements Serializable {

    private Long workOrderId;
    private String technicianId;
    private String serviceAddress;
    private String clientPhone;
    private LocalDateTime scheduledDate;
    private String serviceDescription;
}
