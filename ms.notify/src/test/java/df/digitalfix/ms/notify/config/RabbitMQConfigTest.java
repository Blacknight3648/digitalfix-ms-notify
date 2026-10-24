package df.digitalfix.ms.notify.config;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.core.Queue;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

// La topología se arma con el application.yaml real: si alguien cambia un nombre en el yaml o en
// el código y quedan distintos al caso (sección 8), este test lo detecta sin levantar RabbitMQ.
class RabbitMQConfigTest {

    private static TopologiaRabbitProperties topologia;
    private static RabbitMQConfig config;

    @BeforeAll
    static void cargarYaml() throws IOException {
        StandardEnvironment env = new StandardEnvironment();
        new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yaml"))
                .forEach(env.getPropertySources()::addLast);
        topologia = Binder.get(env).bind("app.rabbitmq", TopologiaRabbitProperties.class).get();
        config = new RabbitMQConfig(topologia);
    }

    @Test
    void declaraLosTresExchangesDelCaso() {
        assertExchange(config.cmdDirectExchange(), "cmd.direct", ExchangeTypes.DIRECT);
        assertExchange(config.cmdTopicExchange(), "cmd.topic", ExchangeTypes.TOPIC);
        assertExchange(config.cmdDeadLetterExchange(), "cmd.dead.dlx", ExchangeTypes.DIRECT);
    }

    @Test
    void cadaColaDeTrabajoDerivaASuDlqPorElDlx() {
        assertColaConDlq(config.emailQueue(), "q.cmd.email", "email.dead");
        assertColaConDlq(config.dispatchQueue(), "q.cmd.dispatch", "dispatch.dead");
        assertColaConDlq(config.reportQueue(), "q.cmd.report", "report.dead");
    }

    @Test
    void lasDlqSonQuorumYConservanLosMensajes24Horas() {
        for (Queue dlq : new Queue[]{config.emailDlq(), config.dispatchDlq(), config.reportDlq()}) {
            assertThat(dlq.isDurable()).isTrue();
            assertThat(dlq.getArguments()).containsEntry("x-queue-type", "quorum")
                    .containsEntry("x-message-ttl", 86_400_000);
        }
        assertThat(config.emailDlq().getName()).isEqualTo("q.cmd.email.dlq");
        assertThat(config.dispatchDlq().getName()).isEqualTo("q.cmd.dispatch.dlq");
        assertThat(config.reportDlq().getName()).isEqualTo("q.cmd.report.dlq");
    }

    @Test
    void bindingsDirectYTopicSegunLaTablaDelCaso() {
        assertBinding(config.emailDirectBinding(), "cmd.direct", "email.send", "q.cmd.email");
        assertBinding(config.emailTopicBinding(), "cmd.topic", "email.*", "q.cmd.email");
        assertBinding(config.dispatchDirectBinding(), "cmd.direct", "dispatch.ticket", "q.cmd.dispatch");
        assertBinding(config.dispatchTopicBinding(), "cmd.topic", "dispatch.#", "q.cmd.dispatch");
        assertBinding(config.reportDirectBinding(), "cmd.direct", "report.gen", "q.cmd.report");
        assertBinding(config.reportTopicBinding(), "cmd.topic", "report.*", "q.cmd.report");
    }

    @Test
    void bindingsDelDlxHaciaCadaDlq() {
        assertBinding(config.emailDlqBinding(), "cmd.dead.dlx", "email.dead", "q.cmd.email.dlq");
        assertBinding(config.dispatchDlqBinding(), "cmd.dead.dlx", "dispatch.dead", "q.cmd.dispatch.dlq");
        assertBinding(config.reportDlqBinding(), "cmd.dead.dlx", "report.dead", "q.cmd.report.dlq");
    }

    @Test
    void reintentosTransitoriosConfigurados() {
        assertThat(topologia.retry().maxAttempts()).isEqualTo(3);
    }

    private static void assertExchange(Exchange exchange, String nombre, String tipo) {
        assertThat(exchange.getName()).isEqualTo(nombre);
        assertThat(exchange.getType()).isEqualTo(tipo);
        assertThat(exchange.isDurable()).isTrue();
    }

    private static void assertColaConDlq(Queue cola, String nombre, String routingKeyDlq) {
        assertThat(cola.getName()).isEqualTo(nombre);
        assertThat(cola.isDurable()).isTrue();
        assertThat(cola.getArguments())
                .containsEntry("x-queue-type", "quorum")
                .containsEntry("x-dead-letter-exchange", "cmd.dead.dlx")
                .containsEntry("x-dead-letter-routing-key", routingKeyDlq);
    }

    private static void assertBinding(Binding binding, String exchange, String routingKey, String cola) {
        assertThat(binding.getExchange()).isEqualTo(exchange);
        assertThat(binding.getRoutingKey()).isEqualTo(routingKey);
        assertThat(binding.getDestination()).isEqualTo(cola);
        assertThat(binding.getDestinationType()).isEqualTo(Binding.DestinationType.QUEUE);
    }
}
