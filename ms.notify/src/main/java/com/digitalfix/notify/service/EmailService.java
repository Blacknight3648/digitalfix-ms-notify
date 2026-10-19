package com.digitalfix.notify.service;

import com.digitalfix.notify.dto.event.WorkOrderNotificationEvent;

public interface EmailService {
    void sendWorkOrderEmail(WorkOrderNotificationEvent event);
}
