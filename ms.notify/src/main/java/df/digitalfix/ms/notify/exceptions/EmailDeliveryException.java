package df.digitalfix.ms.notify.exceptions;

public class EmailDeliveryException extends NotificationException {

    public EmailDeliveryException(String message) {
        super(message);
    }

    public EmailDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
