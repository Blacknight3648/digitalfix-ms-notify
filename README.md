# Microservicio de Notificaciones (`ms-digitalfix-notify`)

## 📋 Descripción general

`ms-digitalfix-notify` es el **consumidor de eventos** de DigitalFix. Escucha las colas de RabbitMQ y reacciona a los cambios de las órdenes de trabajo que publica ms-workorders:
- envía correos HTML al cliente;
- genera tickets de despacho para la cuadrilla en terreno;
- procesa solicitudes de informes PDF.

No tiene API pública de negocio. Su único endpoint de control (`/api/v1/listeners`) lo usa ms-admin-queues por la red interna.

```
ms-workorders ──(cmd.direct)──► q.cmd.email    ──► EmailListener    ──► SMTP (Thymeleaf)
                               q.cmd.dispatch ──► DispatchListener ──► Ticket de despacho
                               q.cmd.report   ──► ReportListener   ──► Informe PDF
                                      │ NACK / reintentos agotados
                                      ▼
                              cmd.dead.dlx ──► q.cmd.*.dlq ──► DlqListener (cuarentena y registro)
```

---

## 🛠️ Arquitectura y tecnologías

* **Lenguaje y framework:** Java 21 / Spring Boot 4.0.8
* **Mensajería asíncrona:** Spring AMQP / RabbitMQ (ACK manual, `prefetch: 1`)
* **Correo:** Spring Boot Starter Mail (`JavaMailSender`) + plantillas Thymeleaf
* **Sin base de datos** (caso, sección 5): el estado de cada mensaje vive en sus colas y DLQ
* **Documentación y diagnóstico:** SpringDoc OpenAPI / Spring Boot Actuator
* **Contenerización:** Dockerfile multi-stage (usuario no-root)

### Organización por responsabilidad

| Paquete | Contenido |
| :--- | :--- |
| `config/` | `TopologiaRabbitProperties` (nombres de la topología, validados al arrancar), `RabbitMQConfig` (beans de Queue, Exchange y Binding por caso de uso), `JacksonConfig` (convertidores), `SecurityConfig`. |
| `listener/` | Un consumidor por dominio: `EmailListener`, `DispatchListener`, `ReportListener` y `DlqListener`. |
| `messaging/` | `ManejadorFallosMensajeria` (política de errores), `ValidadorMensajes` (Bean Validation del payload) y `ListenerIds`. |
| `service/` | Lógica de negocio: `EmailService`, `DispatchService`, `ReportService` y `ListenerAdminService`. |
| `dto/` | `EventEnvelopeDTO<T>` y los payloads de cada caso de uso. |
| `controller/` | `ListenerAdminController` (control de consumidores). |

La configuración de mensajería está aislada en `config/`. Los listeners solo reciben, validan y delegan en un servicio.

---

## 📬 Topología de RabbitMQ

Todos los nombres están centralizados en `application.yaml` (bloque `app.rabbitmq.flujos`, uno por caso de uso) y se leen con `TopologiaRabbitProperties`, sin valores por defecto en el código. `RabbitMQConfig` declara cada flujo como `@Bean` (cola, DLQ y sus tres bindings) y los listeners referencian la cola por su bean (`#{emailQueue.name}`). `RabbitMQConfigTest` verifica la topología contra la tabla del caso usando el yaml real.

| Cola principal | Propósito | Exchange / binding | DLQ (vía `cmd.dead.dlx`) |
| :--- | :--- | :--- | :--- |
| `q.cmd.email` | Correo al cliente: orden creada, cambio de estado, cuadrilla asignada. | `cmd.direct` (`email.send`) / `cmd.topic` (`email.*`) | `q.cmd.email.dlq` (`email.dead`) |
| `q.cmd.dispatch` | Ticket de despacho para la cuadrilla. | `cmd.direct` (`dispatch.ticket`) / `cmd.topic` (`dispatch.#`) | `q.cmd.dispatch.dlq` (`dispatch.dead`) |
| `q.cmd.report` | Informe de cierre o certificado técnico en PDF. | `cmd.direct` (`report.gen`) / `cmd.topic` (`report.*`) | `q.cmd.report.dlq` (`report.dead`) |

Las DLQ tienen un TTL de 24 h.

ms-workorders publica las notificaciones al cliente en `cmd.topic` con `email.<estado>` (`email.creada`, `email.asignada`, `email.cerrada`…), que calzan con `email.*`; el despacho y el informe van por `cmd.direct`. `EmailListener` registra en el log el exchange y la routing key con que llegó cada mensaje.

---

## 📨 Contrato de los mensajes

Todos los eventos viajan en un sobre común:

```json
{
  "eventId": "5f0c…",
  "eventType": "WORK_ORDER_STATUS_CHANGED",
  "timestamp": "2026-10-18T23:15:00",
  "traceId": "TRACE-1a2b3c4d",
  "data": { }
}
```

| Cola | `data` (campos obligatorios en **negrita**) |
| :--- | :--- |
| `q.cmd.email` | **`emailTo`**, **`subject`**, **`templateName`** (`order-notification`), **`workOrderId`**, **`clientName`**, `templateAttributes` (`status`, `serviceAddress`, `technicianName`). |
| `q.cmd.dispatch` | **`workOrderId`**, **`technicianId`**, **`serviceAddress`**, **`clientPhone`**, **`scheduledDate`**, **`serviceDescription`**, `crewId`. |
| `q.cmd.report` | **`workOrderId`**, **`reportType`** (`INFORME_CIERRE`, `CERTIFICADO_TECNICO`), **`recipientEmail`**. |

El lector JSON es **tolerante**: los campos que no conoce se ignoran (`FAIL_ON_UNKNOWN_PROPERTIES = false`). Así el productor puede agregar datos sin que el mensaje falle.

---

## ✅ Confirmación de mensajes y manejo de errores

Cada listener trabaja con **ACK manual** (`acknowledge-mode: manual`):

| Resultado | Acción | Destino |
| :--- | :--- | :--- |
| Procesado correctamente | `basicAck` | Se elimina de la cola. |
| **Error definitivo**: `data` ausente (`InvalidPayloadException`), JSON que no calza, plantilla inexistente o argumento inválido | `basicNack(requeue=false)` | DLQ inmediatamente; reintentar daría el mismo resultado. |
| **Error transitorio**: SMTP caído (`EmailDeliveryException`), timeout o error de red | Se republica en la misma cola con `x-retry-count + 1` y se confirma el original. | Reintento, hasta `app.rabbitmq.retry.max-attempts` (3). |
| Error transitorio con los reintentos agotados | `basicNack(requeue=false)` | DLQ. |

La política está en una sola clase, `messaging/ManejadorFallosMensajeria`. No se usa `requeue=true` porque devolvería el mensaje al inicio de la cola en un bucle inmediato y sin límite. `DlqListener` consume las tres DLQ, registra el payload y los headers `x-death`, y confirma.

Ejemplo real de los logs (SMTP sin credenciales válidas):

```
[q.cmd.email] Error transitorio: Authentication failed. Reintento 1/3 encolado.
[q.cmd.email] Error transitorio: Authentication failed. Reintento 2/3 encolado.
[q.cmd.email] Error transitorio: Authentication failed. Reintento 3/3 encolado.
[q.cmd.email] Error transitorio tras 3 reintentos: Authentication failed. NACK sin requeue → DLQ.
[ALERTA DLQ CUARENTENA] Mensaje fallido detectado en la cola muerta 'q.cmd.email.dlq'
```

---

## 🎛️ Control de listeners

Cada `@RabbitListener` tiene un id fijo (`messaging/ListenerIds`):

| Id | Colas |
| :--- | :--- |
| `listener-email` | `q.cmd.email` |
| `listener-dispatch` | `q.cmd.dispatch` |
| `listener-report` | `q.cmd.report` |
| `listener-dlq` | `q.cmd.email.dlq`, `q.cmd.dispatch.dlq`, `q.cmd.report.dlq` |

| Método | Ruta | Descripción |
| :--- | :--- | :--- |
| `GET` | `/api/v1/listeners` | Estado de todos los consumidores. |
| `GET` | `/api/v1/listeners/{id}` | Estado de uno (404 si no existe). |
| `POST` | `/api/v1/listeners/{id}/start` · `/stop` · `/pause` · `/resume` | Controla el consumo. |

Estos endpoints solo son accesibles dentro de `digitalfix-net`, porque el puerto 8083 no se publica. Los invoca **ms-admin-queues**, que a su vez queda detrás del BFF con rol SUPERADMIN. Desde el frontend se usan en el panel **Mensajería**.

---

## 🔑 Variables de entorno (`.env`)

```env
PORT=8083

# RabbitMQ (en Docker: RABBITMQ_HOST=rabbitmq)
RABBITMQ_HOST=localhost
RABBITMQ_PORT=5672
RABBITMQ_USERNAME=<usuario>
RABBITMQ_PASSWORD=<contrasena>
RABBITMQ_VIRTUAL_HOST=/

# Servidor SMTP (Mailtrap en desarrollo)
SPRING_MAIL_HOST=smtp.mailtrap.io
SPRING_MAIL_PORT=2525
SPRING_MAIL_USERNAME=<usuario_smtp>
SPRING_MAIL_PASSWORD=<password_smtp>
```

> Sin credenciales SMTP válidas, los correos terminan en la DLQ después de 3 reintentos, que es el comportamiento esperado. Para ver los correos, crea una bandeja en Mailtrap y configura `SPRING_MAIL_USERNAME` y `SPRING_MAIL_PASSWORD`.

---

## ▶️ Ejecución

```bash
# Plataforma completa (desde digitalfix-infrastructure)
docker compose up -d --build ms-notify

# Independiente, con su propio RabbitMQ (compose.yml de este repositorio)
docker compose up -d --build
```

### Probar un evento sin el resto de la plataforma

Desde la consola de RabbitMQ (`http://localhost:15672` → Exchanges → `cmd.direct` → *Publish message*), con la routing key `dispatch.ticket`, la propiedad `content_type = application/json` y este payload:

```json
{"eventId":"demo-1","eventType":"CREW_DISPATCH_ASSIGNED","timestamp":"2026-10-18T23:15:00",
 "data":{"workOrderId":42,"technicianId":"7","serviceAddress":"Av. Providencia 1234",
         "clientPhone":"+56912345678","scheduledDate":"2026-10-19T09:00:00","serviceDescription":"Revisión tablero"}}
```

En los logs del contenedor aparece el `TICKET DE DESPACHO TECNICO` y el `ACK confirmado`.

---

## 🔌 Puerto

Puerto local `8083`, único dentro del ecosistema (ver la [tabla de puertos](https://github.com/SolgreyDuocUC/digitalfix-infrastructure#86-puertos-de-los-microservicios)). Expone Swagger (`/swagger-ui.html`), Actuator (`/actuator/health`, que incluye el estado de RabbitMQ) y el control de listeners.
