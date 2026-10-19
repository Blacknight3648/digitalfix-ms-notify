package com.digitalfix.notify.service;

import com.digitalfix.notify.dto.event.DispatchTicketEvent;

public interface DispatchService {
    void processDispatchTicket(DispatchTicketEvent event);
}
