package df.digitalfix.ms.notify.config;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.Queue;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class RabbitMQConfigTest {

    @Test
    void noDeclaraInfraestructuraNiInicializadoresDeColas() {
        Method[] metodos = RabbitMQConfig.class.getDeclaredMethods();
        assertThat(Arrays.stream(metodos)
                .filter(m -> Queue.class.isAssignableFrom(m.getReturnType())
                        || Exchange.class.isAssignableFrom(m.getReturnType())
                        || Binding.class.isAssignableFrom(m.getReturnType())
                        || Declarables.class.isAssignableFrom(m.getReturnType())))
                .isEmpty();
    }

    @Test
    void configuracionManualYTopologiaSeCarganDesdeApplicationYaml() throws IOException {
        StandardEnvironment env = new StandardEnvironment();
        new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yaml"))
                .forEach(env.getPropertySources()::addLast);

        assertThat(env.getProperty("spring.rabbitmq.listener.simple.acknowledge-mode")).isEqualTo("manual");

        TopologiaRabbitProperties topologia = Binder.get(env)
                .bind("app.rabbitmq", TopologiaRabbitProperties.class)
                .get();

        assertThat(topologia.flujos().email().cola()).isEqualTo("q.cmd.email");
        assertThat(topologia.flujos().dispatch().cola()).isEqualTo("q.cmd.dispatch");
    }
}
