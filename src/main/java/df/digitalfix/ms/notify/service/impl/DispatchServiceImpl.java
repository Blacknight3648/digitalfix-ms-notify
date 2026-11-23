package df.digitalfix.ms.notify.service.impl;

import df.digitalfix.ms.notify.dto.DispatchTicketDTO;
import df.digitalfix.ms.notify.service.DispatchService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class DispatchServiceImpl implements DispatchService {

    @Override
    public void processDispatchTicket(DispatchTicketDTO dispatchDTO) {
        log.info("Generando ticket de despacho a terreno para Orden #{} - Técnico ID: {}", 
                dispatchDTO.getWorkOrderId(), dispatchDTO.getTechnicianId());

        // Simulación de armado y log del ticket impreso/digital de despacho
        log.info("================ TICKET DE DESPACHO TECNICO ================");
        log.info("Orden ID: {}", dispatchDTO.getWorkOrderId());
        log.info("Técnico Asignado: {}", dispatchDTO.getTechnicianId());
        log.info("Dirección Trabajo: {}", dispatchDTO.getServiceAddress());
        log.info("Contacto Cliente: {}", dispatchDTO.getClientPhone());
        log.info("Fecha Programada: {}", dispatchDTO.getScheduledDate());
        log.info("Detalle Servicio: {}", dispatchDTO.getServiceDescription());
        log.info("==========================================================");

        log.info("Ticket de despacho procesado correctamente para orden #{}", dispatchDTO.getWorkOrderId());
    }
}
