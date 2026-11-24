package df.digitalfix.ms.notify.service;

import df.digitalfix.ms.notify.dto.DispatchTicketDTO;

public interface DispatchService {
    void processDispatchTicket(DispatchTicketDTO dispatchDTO);
}
