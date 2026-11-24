package df.digitalfix.ms.notify.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuración base de propiedades RabbitMQ.
 * <p>
 * Este microservicio no declara infraestructura ni inicializadores de colas, exchanges o bindings;
 * únicamente consume colas preexistentes en el broker.
 */
@Configuration
@EnableConfigurationProperties(TopologiaRabbitProperties.class)
public class RabbitMQConfig {
}
