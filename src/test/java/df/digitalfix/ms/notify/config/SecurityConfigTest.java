package df.digitalfix.ms.notify.config;

import df.digitalfix.ms.notify.service.EmailService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// ms-admin-queues entrega los mensajes sin token por la red interna. Si la seguridad bloqueara
// /internal/** respondería 401/403, que ms-admin-queues trata como error definitivo: todos los
// mensajes terminarían en la DLQ. Este test usa la cadena de filtros real.
@SpringBootTest
@AutoConfigureMockMvc
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EmailService emailService;

    @Test
    void laEntregaInternaNoExigeToken() throws Exception {
        mockMvc.perform(post("/internal/v1/notificaciones/email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"eventId":"evt-1","eventType":"X","timestamp":"2026-11-05T20:00:00",
                                 "data":{"workOrderId":1,"emailTo":"a@b.cl","subject":"s","templateName":"order-notification","clientName":"c"}}
                                """))
                .andExpect(status().isOk());
    }

    // Sin mecanismo de login configurado, Spring Security rechaza lo no autenticado con 403.
    @Test
    void elRestoDeLaApiSigueProtegido() throws Exception {
        mockMvc.perform(get("/api/v1/cualquier-otra-ruta")).andExpect(status().isForbidden());
    }
}
