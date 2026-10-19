package df.digitalfix.ms.notify.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class RabbitMQConfigTest {

    private RabbitMQConfig rabbitMQConfig;

    @BeforeEach
    void setUp() {
        rabbitMQConfig = new RabbitMQConfig();
        ReflectionTestUtils.setField(rabbitMQConfig, "directExchangeName", "cmd.direct");
        ReflectionTestUtils.setField(rabbitMQConfig, "dlxExchangeName", "cmd.dead.dlx");
        ReflectionTestUtils.setField(rabbitMQConfig, "emailQueueName", "q.cmd.email");
        ReflectionTestUtils.setField(rabbitMQConfig, "emailDlqName", "q.cmd.email.dlq");
        ReflectionTestUtils.setField(rabbitMQConfig, "dispatchQueueName", "q.cmd.dispatch");
        ReflectionTestUtils.setField(rabbitMQConfig, "dispatchDlqName", "q.cmd.dispatch.dlq");
        ReflectionTestUtils.setField(rabbitMQConfig, "emailRoutingKey", "email.send");
        ReflectionTestUtils.setField(rabbitMQConfig, "emailDlqRoutingKey", "email.send.dlq");
        ReflectionTestUtils.setField(rabbitMQConfig, "dispatchRoutingKey", "dispatch.ticket");
        ReflectionTestUtils.setField(rabbitMQConfig, "dispatchDlqRoutingKey", "dispatch.ticket.dlq");
    }

    @Test
    @DisplayName("Debe configurar jsonMessageConverter con Jackson2JsonMessageConverter")
    void testJsonMessageConverter() {
        MessageConverter converter = rabbitMQConfig.jsonMessageConverter();
        assertThat(converter).isNotNull();
        assertThat(converter).isInstanceOf(Jackson2JsonMessageConverter.class);
    }

    @Test
    @DisplayName("Debe configurar directExchange durable")
    void testDirectExchange() {
        DirectExchange exchange = rabbitMQConfig.directExchange();
        assertThat(exchange).isNotNull();
        assertThat(exchange.getName()).isEqualTo("cmd.direct");
        assertThat(exchange.isDurable()).isTrue();
        assertThat(exchange.isAutoDelete()).isFalse();
    }

    @Test
    @DisplayName("Debe configurar deadLetterExchange durable")
    void testDeadLetterExchange() {
        DirectExchange dlx = rabbitMQConfig.deadLetterExchange();
        assertThat(dlx).isNotNull();
        assertThat(dlx.getName()).isEqualTo("cmd.dead.dlx");
        assertThat(dlx.isDurable()).isTrue();
        assertThat(dlx.isAutoDelete()).isFalse();
    }

    @Test
    @DisplayName("Debe configurar emailQueue con argumentos de Dead Letter Exchange y DLQ")
    void testEmailQueue() {
        Queue queue = rabbitMQConfig.emailQueue();
        assertThat(queue).isNotNull();
        assertThat(queue.getName()).isEqualTo("q.cmd.email");
        assertThat(queue.isDurable()).isTrue();
        assertThat(queue.getArguments())
                .containsEntry("x-dead-letter-exchange", "cmd.dead.dlx")
                .containsEntry("x-dead-letter-routing-key", "email.send.dlq");
    }

    @Test
    @DisplayName("Debe configurar emailDlq como cola durable")
    void testEmailDlq() {
        Queue queue = rabbitMQConfig.emailDlq();
        assertThat(queue).isNotNull();
        assertThat(queue.getName()).isEqualTo("q.cmd.email.dlq");
        assertThat(queue.isDurable()).isTrue();
    }

    @Test
    @DisplayName("Debe configurar dispatchQueue con argumentos de Dead Letter Exchange y DLQ")
    void testDispatchQueue() {
        Queue queue = rabbitMQConfig.dispatchQueue();
        assertThat(queue).isNotNull();
        assertThat(queue.getName()).isEqualTo("q.cmd.dispatch");
        assertThat(queue.isDurable()).isTrue();
        assertThat(queue.getArguments())
                .containsEntry("x-dead-letter-exchange", "cmd.dead.dlx")
                .containsEntry("x-dead-letter-routing-key", "dispatch.ticket.dlq");
    }

    @Test
    @DisplayName("Debe configurar dispatchDlq como cola durable")
    void testDispatchDlq() {
        Queue queue = rabbitMQConfig.dispatchDlq();
        assertThat(queue).isNotNull();
        assertThat(queue.getName()).isEqualTo("q.cmd.dispatch.dlq");
        assertThat(queue.isDurable()).isTrue();
    }

    @Test
    @DisplayName("Debe configurar bindings de emailQueue y emailDlq")
    void testEmailBindings() {
        Binding emailBinding = rabbitMQConfig.emailBinding();
        assertThat(emailBinding).isNotNull();
        assertThat(emailBinding.getDestination()).isEqualTo("q.cmd.email");
        assertThat(emailBinding.getExchange()).isEqualTo("cmd.direct");
        assertThat(emailBinding.getRoutingKey()).isEqualTo("email.send");

        Binding dlqBinding = rabbitMQConfig.emailDlqBinding();
        assertThat(dlqBinding).isNotNull();
        assertThat(dlqBinding.getDestination()).isEqualTo("q.cmd.email.dlq");
        assertThat(dlqBinding.getExchange()).isEqualTo("cmd.dead.dlx");
        assertThat(dlqBinding.getRoutingKey()).isEqualTo("email.send.dlq");
    }

    @Test
    @DisplayName("Debe configurar bindings de dispatchQueue y dispatchDlq")
    void testDispatchBindings() {
        Binding dispatchBinding = rabbitMQConfig.dispatchBinding();
        assertThat(dispatchBinding).isNotNull();
        assertThat(dispatchBinding.getDestination()).isEqualTo("q.cmd.dispatch");
        assertThat(dispatchBinding.getExchange()).isEqualTo("cmd.direct");
        assertThat(dispatchBinding.getRoutingKey()).isEqualTo("dispatch.ticket");

        Binding dlqBinding = rabbitMQConfig.dispatchDlqBinding();
        assertThat(dlqBinding).isNotNull();
        assertThat(dlqBinding.getDestination()).isEqualTo("q.cmd.dispatch.dlq");
        assertThat(dlqBinding.getExchange()).isEqualTo("cmd.dead.dlx");
        assertThat(dlqBinding.getRoutingKey()).isEqualTo("dispatch.ticket.dlq");
    }
}
