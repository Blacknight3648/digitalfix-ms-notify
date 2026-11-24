package df.digitalfix.ms.notify.messaging;

/**
 * Identificadores de los contenedores de {@code @RabbitListener}. Con ellos ms-admin-queues
 * consulta, detiene o reanuda cada consumidor (GET/POST /api/v1/listeners/{id}).
 */
public final class ListenerIds {

    public static final String EMAIL = "listener-email";
    public static final String DISPATCH = "listener-dispatch";
    public static final String REPORT = "listener-report";
    public static final String DLQ = "listener-dlq";

    private ListenerIds() {
    }
}
