# Microservicio de Notificaciones (`ms-digitalfix-notify`)

## 📋 Descripción general

`ms-digitalfix-notify` contiene **solo la lógica de negocio** de las comunicaciones de DigitalFix:
- envía correos HTML al cliente;
- genera tickets de despacho para la cuadrilla en terreno;
- procesa solicitudes de informes PDF.

**No se conecta a RabbitMQ.** Toda la mensajería (topología, colas, listeners, reintentos y DLQ) está centralizada en **ms-admin-queues**, que consume las colas y le entrega cada mensaje por HTTP a los endpoints internos `/internal/v1/notificaciones/*`.

```
ms-workorders ─HTTP─► ms-admin-queues ─► RabbitMQ (q.cmd.email · q.cmd.dispatch · q.cmd.report)
                            │
                            └─ listener-<flujo> ─HTTP POST─► ms-notify /internal/v1/notificaciones/email    ─► SMTP (Thymeleaf)
                                                                                                 /despacho ─► Ticket de despacho
                                                                                                 /informe  ─► Informe PDF
```

---

## 🛠️ Arquitectura y tecnologías

* **Lenguaje y framework:** Java 21 / Spring Boot 4.0.8
* **Entrada de mensajes:** HTTP interno (lo llama ms-admin-queues); sin dependencia de Spring AMQP
* **Correo:** Spring Boot Starter Mail (`JavaMailSender`) + plantillas Thymeleaf
* **Sin base de datos** (caso, sección 5): el estado de cada mensaje vive en las colas y DLQ de ms-admin-queues
* **Documentación y diagnóstico:** SpringDoc OpenAPI / Spring Boot Actuator
* **Contenerización:** Dockerfile multi-stage (usuario no-root)

### Organización por responsabilidad

| Paquete | Contenido |
| :--- | :--- |
| `controller/` | `NotificacionInternaController`: recibe el sobre del evento, lo valida y delega en el servicio. |
| `validation/` | `ValidadorMensajes`: Bean Validation del sobre y de su `data`. |
| `service/` | Lógica de negocio: `EmailService`, `DispatchService` y `ReportService`. |
| `exceptions/` | `NotificacionExceptionHandler`: traduce los errores de negocio a códigos HTTP (ver abajo). |
| `dto/` | `EventEnvelopeDTO<T>` y los payloads de cada caso de uso. |
| `config/` | `SecurityConfig`. |

---

## 📨 Endpoints internos y contrato de los mensajes

Solo los llama **ms-admin-queues** dentro de `digitalfix-net`: el puerto 8083 no se publica y el BFF no enruta `/internal/**`.

| Método | Ruta | Flujo (cola) | Servicio |
| :--- | :--- | :--- | :--- |
| `POST` | `/internal/v1/notificaciones/email` | `email` (`q.cmd.email`) | `EmailService` |
| `POST` | `/internal/v1/notificaciones/despacho` | `dispatch` (`q.cmd.dispatch`) | `DispatchService` |
| `POST` | `/internal/v1/notificaciones/informe` | `report` (`q.cmd.report`) | `ReportService` |

El cuerpo es el sobre común, tal como viajó por la cola:

```json
{
  "eventId": "5f0c…",
  "eventType": "WORK_ORDER_STATUS_CHANGED",
  "timestamp": "2026-10-18T23:15:00",
  "traceId": "TRACE-1a2b3c4d",
  "correlationId": "OT-15",
  "data": { }
}
```

| Endpoint | `data` (campos obligatorios en **negrita**) |
| :--- | :--- |
| `/email` | **`emailTo`**, **`subject`**, **`templateName`** (`order-notification`), **`workOrderId`**, **`clientName`**, `templateAttributes` (`status`, `serviceAddress`, `technicianName`). |
| `/despacho` | **`workOrderId`**, **`technicianId`**, **`serviceAddress`**, **`clientPhone`**, **`scheduledDate`**, **`serviceDescription`**, `crewId`. |
| `/informe` | **`workOrderId`**, **`reportType`** (`INFORME_CIERRE`, `CERTIFICADO_TECNICO`), **`recipientEmail`**. |

El lector JSON es **tolerante** (`@JsonIgnoreProperties(ignoreUnknown = true)`): los campos que no conoce se ignoran, así el productor puede agregar datos sin que el mensaje se rechace.

---

## ✅ Códigos de respuesta y manejo de errores

El código HTTP es parte del contrato: ms-admin-queues lo usa para decidir qué hacer con el mensaje.

| Resultado | Respuesta | Qué hace ms-admin-queues |
| :--- | :--- | :--- |
| Procesado correctamente | `200` | ACK: el mensaje sale de la cola. |
| **Error definitivo**: `data` inválido o ausente (`InvalidPayloadException`), JSON que no calza, argumento inválido | `400` | NACK sin requeue → DLQ inmediata. |
| **Error definitivo**: plantilla inexistente (`TemplateInputException`) | `422` | NACK sin requeue → DLQ inmediata. |
| **Error transitorio**: SMTP caído (`EmailDeliveryException`) | `503` | Reintento (hasta 3) y luego DLQ. |
| Cualquier otro error inesperado | `500` | Reintento (hasta 3) y luego DLQ. |

La política de reintentos, la DLQ y el control de listeners (`listener-email`, `listener-dispatch`, `listener-report`, `listener-dlq`) están documentados en el README de ms-admin-queues.

---

## 🔑 Variables de entorno (`.env`)

```env
PORT=8083

# Servidor SMTP (Mailtrap en desarrollo)
SPRING_MAIL_HOST=smtp.mailtrap.io
SPRING_MAIL_PORT=2525
SPRING_MAIL_USERNAME=<usuario_smtp>
SPRING_MAIL_PASSWORD=<password_smtp>
```

> Sin credenciales SMTP válidas, `/email` responde 503: ms-admin-queues reintenta 3 veces y deja el mensaje en `q.cmd.email.dlq`, que es el comportamiento esperado. Para ver los correos, crea una bandeja en Mailtrap y configura `SPRING_MAIL_USERNAME` y `SPRING_MAIL_PASSWORD`.

---

## ▶️ Ejecución

```bash
# Plataforma completa (desde digitalfix-infrastructure)
docker compose up -d --build ms-notify ms-admin-queues

# Independiente (compose.yml de este repositorio, sin broker)
docker compose up -d --build
```

### Probar un evento sin el resto de la plataforma

Directamente contra el endpoint interno (desarrollo local):

```bash
curl -X POST http://localhost:8083/internal/v1/notificaciones/despacho -H "Content-Type: application/json" -d '{
  "eventId":"demo-1","eventType":"CREW_DISPATCH_ASSIGNED","timestamp":"2026-10-18T23:15:00",
  "data":{"workOrderId":42,"technicianId":"7","serviceAddress":"Av. Providencia 1234",
          "clientPhone":"+56912345678","scheduledDate":"2026-10-19T09:00:00","serviceDescription":"Revisión tablero"}}'
```

En los logs aparece el `TICKET DE DESPACHO TECNICO`. Para probar el circuito completo, publica el mismo `data` en ms-admin-queues (`POST /internal/v1/eventos/dispatch`).

---

## 🔌 Puerto

Puerto local `8083`, único dentro del ecosistema (ver la [tabla de puertos](https://github.com/SolgreyDuocUC/digitalfix-infrastructure#86-puertos-de-los-microservicios)). Expone Swagger (`/swagger-ui.html`), Actuator (`/actuator/health`) y los endpoints internos de notificación.
