package df.digitalfix.ms.notify.config;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.rabbit.connection.Connection;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;

import java.io.IOException;
import java.net.ConnectException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RabbitTopologyHealthIndicatorTest {

    private ConnectionFactory connectionFactory;
    private Connection connection;
    private RabbitTopologyHealthIndicator indicator;

    @BeforeEach
    void setUp() {
        connectionFactory = mock(ConnectionFactory.class);
        connection = mock(Connection.class);

        TopologiaRabbitProperties topologia = new TopologiaRabbitProperties(
                new TopologiaRabbitProperties.Flujos(
                        new TopologiaRabbitProperties.Flujo("q.cmd.email", "q.cmd.email.dlq"),
                        new TopologiaRabbitProperties.Flujo("q.cmd.dispatch", "q.cmd.dispatch.dlq"),
                        new TopologiaRabbitProperties.Flujo("q.cmd.report", "q.cmd.report.dlq")
                ),
                new TopologiaRabbitProperties.Reintentos(3)
        );

        indicator = new RabbitTopologyHealthIndicator(connectionFactory, topologia);
    }

    @Test
    void reportaUpCuandoTodasLasColasRequeridasExisten() throws IOException {
        Channel channel = mock(Channel.class);
        when(connectionFactory.createConnection()).thenReturn(connection);
        when(connection.createChannel(false)).thenReturn(channel);
        when(channel.queueDeclarePassive("q.cmd.email")).thenReturn(mock(AMQP.Queue.DeclareOk.class));
        when(channel.queueDeclarePassive("q.cmd.email.dlq")).thenReturn(mock(AMQP.Queue.DeclareOk.class));
        when(channel.queueDeclarePassive("q.cmd.dispatch")).thenReturn(mock(AMQP.Queue.DeclareOk.class));
        when(channel.queueDeclarePassive("q.cmd.dispatch.dlq")).thenReturn(mock(AMQP.Queue.DeclareOk.class));
        when(channel.queueDeclarePassive("q.cmd.report")).thenReturn(mock(AMQP.Queue.DeclareOk.class));
        when(channel.queueDeclarePassive("q.cmd.report.dlq")).thenReturn(mock(AMQP.Queue.DeclareOk.class));

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails().get("checkedQueues")).isEqualTo(List.of(
                "q.cmd.email", "q.cmd.email.dlq",
                "q.cmd.dispatch", "q.cmd.dispatch.dlq",
                "q.cmd.report", "q.cmd.report.dlq"
        ));
    }

    @Test
    void reportaDownConDetalleCuandoFaltanColasProvisionadas() throws IOException {
        Channel channelOk = mock(Channel.class);
        Channel channelMissing = mock(Channel.class);
        when(connectionFactory.createConnection()).thenReturn(connection);
        when(connection.createChannel(false)).thenReturn(channelOk, channelMissing, channelOk, channelMissing, channelOk, channelOk);

        when(channelOk.queueDeclarePassive("q.cmd.email")).thenReturn(mock(AMQP.Queue.DeclareOk.class));
        when(channelMissing.queueDeclarePassive("q.cmd.email.dlq")).thenThrow(new IOException("404 NOT_FOUND"));
        when(channelOk.queueDeclarePassive("q.cmd.dispatch")).thenReturn(mock(AMQP.Queue.DeclareOk.class));
        when(channelMissing.queueDeclarePassive("q.cmd.dispatch.dlq")).thenThrow(new IOException("404 NOT_FOUND"));
        when(channelOk.queueDeclarePassive("q.cmd.report")).thenReturn(mock(AMQP.Queue.DeclareOk.class));
        when(channelOk.queueDeclarePassive("q.cmd.report.dlq")).thenReturn(mock(AMQP.Queue.DeclareOk.class));

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails().get("missingQueues")).isEqualTo(List.of("q.cmd.email.dlq", "q.cmd.dispatch.dlq"));
        assertThat(health.getDetails().get("error")).isEqualTo("Recursos AMQP requeridos no provisionados en RabbitMQ");
    }

    @Test
    void reportaDownCuandoFallaLaConexionAlBroker() {
        when(connectionFactory.createConnection()).thenThrow(new AmqpConnectException(new ConnectException("Connection refused")));

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails().get("error")).isEqualTo("Fallo de conexión al verificar los recursos AMQP requeridos en RabbitMQ");
    }
}
