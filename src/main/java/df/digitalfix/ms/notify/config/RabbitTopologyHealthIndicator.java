package df.digitalfix.ms.notify.config;

import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.Connection;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Verifica de forma pasiva que las colas principales y DLQ requeridas por {@code ms-notify}
 * existan en RabbitMQ, sin declarar ni modificar la topología del broker.
 */
@Slf4j
@Component("rabbitTopology")
@RequiredArgsConstructor
public class RabbitTopologyHealthIndicator implements HealthIndicator {

    private final ConnectionFactory connectionFactory;
    private final TopologiaRabbitProperties topologia;

    @Override
    public Health health() {
        List<String> requiredQueues = requiredQueues();
        List<String> missingQueues = new ArrayList<>();

        try (Connection connection = connectionFactory.createConnection()) {
            for (String queueName : requiredQueues) {
                if (!queueExists(connection, queueName)) {
                    missingQueues.add(queueName);
                }
            }
        } catch (Exception ex) {
            log.warn("No se pudo verificar la topología AMQP en RabbitMQ: {}", ex.getMessage());
            return Health.down(ex)
                    .withDetail("error", "Fallo de conexión al verificar los recursos AMQP requeridos en RabbitMQ")
                    .withDetail("requiredQueues", requiredQueues)
                    .build();
        }

        if (!missingQueues.isEmpty()) {
            log.warn("Recursos AMQP requeridos no provisionados en RabbitMQ: {}", missingQueues);
            return Health.down()
                    .withDetail("error", "Recursos AMQP requeridos no provisionados en RabbitMQ")
                    .withDetail("missingQueues", missingQueues)
                    .withDetail("checkedQueues", requiredQueues)
                    .build();
        }

        return Health.up()
                .withDetail("checkedQueues", requiredQueues)
                .build();
    }

    private boolean queueExists(Connection connection, String queueName) {
        try (Channel channel = connection.createChannel(false)) {
            channel.queueDeclarePassive(queueName);
            return true;
        } catch (Exception ex) {
            log.debug("Cola '{}' no disponible en RabbitMQ: {}", queueName, ex.getMessage());
            return false;
        }
    }

    private List<String> requiredQueues() {
        TopologiaRabbitProperties.Flujos flujos = topologia.flujos();
        return List.of(
                flujos.email().cola(),
                flujos.email().dlq(),
                flujos.dispatch().cola(),
                flujos.dispatch().dlq(),
                flujos.report().cola(),
                flujos.report().dlq()
        );
    }
}
