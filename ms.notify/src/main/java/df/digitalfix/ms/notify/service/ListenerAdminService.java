package df.digitalfix.ms.notify.service;

import df.digitalfix.ms.notify.dto.ListenerContainerDTO;

import java.util.List;

public interface ListenerAdminService {

    List<ListenerContainerDTO> getAllListeners();
    ListenerContainerDTO getListenerById(String listenerId);
    void startListener(String listenerId);
    void stopListener(String listenerId);
    void pauseListener(String listenerId);
    void resumeListener(String listenerId);
}
