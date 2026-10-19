package com.digitalfix.notify.dto.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkOrderNotificationEvent implements Serializable {

    private Long workOrderId;
    private String orderNumber;
    private String clientName;
    private String emailTo;
    private String subject;
    private String templateName;
    private String status;
    private String technicianId;
    private String technicianName;
    private LocalDateTime scheduledDate;
    private Map<String, Object> metadata;
}
