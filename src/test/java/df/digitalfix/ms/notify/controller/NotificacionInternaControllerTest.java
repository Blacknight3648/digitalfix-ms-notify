package df.digitalfix.ms.notify.controller;

import df.digitalfix.ms.notify.dto.DispatchTicketDTO;
import df.digitalfix.ms.notify.dto.EmailNotificationDTO;
import df.digitalfix.ms.notify.exceptions.EmailDeliveryException;
import df.digitalfix.ms.notify.service.DispatchService;
import df.digitalfix.ms.notify.service.EmailService;
import df.digitalfix.ms.notify.service.ReportService;
import df.digitalfix.ms.notify.validation.ValidadorMensajes;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.thymeleaf.exceptions.TemplateInputException;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Contrato con ms-admin-queues: recibe el sobre tal cual viaja por la cola y el código HTTP decide
// el destino del mensaje (2xx ACK, 4xx DLQ, 5xx reintento).
@WebMvcTest(controllers = NotificacionInternaController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(ValidadorMensajes.class)
class NotificacionInternaControllerTest {

    // Sobre como lo publica ms-admin-queues para ms-workorders, con campos extra que notify no usa.
    private static final String SOBRE_EMAIL = """
            {"eventId":"evt-1","eventType":"WORK_ORDER_STATUS_CHANGED","timestamp":"2026-11-05T20:00:00",
             "traceId":"TRACE-1","correlationId":"OT-15",
             "data":{"workOrderId":15,"emailTo":"%s","clientName":"Comercial Rojas Ltda.",
                     "orderStatus":"ASIGNADA","totalAmount":125000.50,
                     "subject":"DigitalFix: tu orden #15 ya tiene cuadrilla asignada",
                     "templateName":"order-notification",
                     "templateAttributes":{"status":"ASIGNADA","serviceAddress":"Av. Providencia 1234"}}}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EmailService emailService;
    @MockitoBean
    private DispatchService dispatchService;
    @MockitoBean
    private ReportService reportService;

    private ResultActions enviar(String ruta, String json) throws Exception {
        return mockMvc.perform(post("/internal/v1/notificaciones" + ruta)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @Test
    void correoValidoSeEnviaYResponde200() throws Exception {
        enviar("/email", SOBRE_EMAIL.formatted("cliente@digitalfix.cl"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value("evt-1"));

        ArgumentCaptor<EmailNotificationDTO> correo = ArgumentCaptor.forClass(EmailNotificationDTO.class);
        verify(emailService).sendHtmlEmail(correo.capture());
        assertThat(correo.getValue().getEmailTo()).isEqualTo("cliente@digitalfix.cl");
        assertThat(correo.getValue().getWorkOrderId()).isEqualTo(15L);
        assertThat(correo.getValue().getTemplateAttributes()).containsEntry("status", "ASIGNADA");
    }

    @Test
    void payloadInvalidoEs400YNoSeProcesa() throws Exception {
        enviar("/email", SOBRE_EMAIL.formatted("no-es-correo"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("data.emailTo")));
        verifyNoInteractions(emailService);
    }

    @Test
    void jsonMalFormadoEs400() throws Exception {
        enviar("/email", "{no es json").andExpect(status().isBadRequest());
        verifyNoInteractions(emailService);
    }

    @Test
    void plantillaInexistenteEs422() throws Exception {
        doThrow(new TemplateInputException("Error resolving template [email/no-existe]"))
                .when(emailService).sendHtmlEmail(any());

        enviar("/email", SOBRE_EMAIL.formatted("cliente@digitalfix.cl"))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void smtpCaidoEs503ParaQueSeReintente() throws Exception {
        doThrow(new EmailDeliveryException("Error en el despacho de correo SMTP"))
                .when(emailService).sendHtmlEmail(any());

        enviar("/email", SOBRE_EMAIL.formatted("cliente@digitalfix.cl"))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void ticketDeDespachoConFechaIso() throws Exception {
        enviar("/despacho", """
                {"eventId":"evt-2","eventType":"CREW_DISPATCH_ASSIGNED","timestamp":"2026-11-05T20:00:00",
                 "data":{"workOrderId":77,"technicianId":"15","crewId":"3","serviceAddress":"Calle Los Alerces 456",
                         "clientPhone":"+56987654321","scheduledDate":"2026-11-06T09:30:00",
                         "serviceDescription":"Revisión de líneas"}}
                """).andExpect(status().isOk());

        ArgumentCaptor<DispatchTicketDTO> ticket = ArgumentCaptor.forClass(DispatchTicketDTO.class);
        verify(dispatchService).processDispatchTicket(ticket.capture());
        assertThat(ticket.getValue().getScheduledDate()).isEqualTo(LocalDateTime.of(2026, 11, 6, 9, 30));
    }

    @Test
    void informeSinTipoEs400() throws Exception {
        enviar("/informe", """
                {"eventId":"evt-3","eventType":"WORK_ORDER_REPORT_REQUESTED","timestamp":"2026-11-05T20:00:00",
                 "data":{"workOrderId":88,"recipientEmail":"cliente@digitalfix.cl"}}
                """).andExpect(status().isBadRequest());
        verifyNoInteractions(reportService);
    }
}
