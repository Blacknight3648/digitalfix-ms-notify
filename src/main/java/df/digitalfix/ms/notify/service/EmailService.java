package df.digitalfix.ms.notify.service;

import df.digitalfix.ms.notify.dto.EmailNotificationDTO;

public interface EmailService {
    void sendHtmlEmail(EmailNotificationDTO emailDTO);
}
