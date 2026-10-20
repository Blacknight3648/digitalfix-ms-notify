package df.digitalfix.ms.notify.config;

import org.springframework.amqp.core.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    // Nombres de Exchanges
    @Value("${app.rabbitmq.exchanges.direct:cmd.direct}")
    private String directExchangeName;

    @Value("${app.rabbitmq.exchanges.topic:cmd.topic}")
    private String topicExchangeName;

    @Value("${app.rabbitmq.exchanges.dlx:cmd.dead.dlx}")
    private String dlxExchangeName;

    // Nombres de Colas Principales
    @Value("${app.rabbitmq.queues.email:q.cmd.email}")
    private String emailQueueName;

    @Value("${app.rabbitmq.queues.dispatch:q.cmd.dispatch}")
    private String dispatchQueueName;

    @Value("${app.rabbitmq.queues.report:q.cmd.report}")
    private String reportQueueName;

    // Nombres de DLQs
    @Value("${app.rabbitmq.queues.email-dlq:q.cmd.email.dlq}")
    private String emailDlqName;

    @Value("${app.rabbitmq.queues.dispatch-dlq:q.cmd.dispatch.dlq}")
    private String dispatchDlqName;

    @Value("${app.rabbitmq.queues.report-dlq:q.cmd.report.dlq}")
    private String reportDlqName;

    // Routing Keys
    @Value("${app.rabbitmq.routing-keys.email-send:email.send}")
    private String emailRoutingKey;

    @Value("${app.rabbitmq.routing-keys.dispatch-ticket:dispatch.ticket}")
    private String dispatchRoutingKey;

    @Value("${app.rabbitmq.routing-keys.report-gen:report.gen}")
    private String reportRoutingKey;

    // Routing keys hacia la DLQ (x-dead-letter-routing-key) y patrones del exchange topic
    @Value("${app.rabbitmq.routing-keys.email-dead:email.dead}")
    private String emailDeadRoutingKey;

    @Value("${app.rabbitmq.routing-keys.dispatch-dead:dispatch.dead}")
    private String dispatchDeadRoutingKey;

    @Value("${app.rabbitmq.routing-keys.report-dead:report.dead}")
    private String reportDeadRoutingKey;

    @Value("${app.rabbitmq.topic-patterns.email:email.*}")
    private String emailTopicPattern;

    @Value("${app.rabbitmq.topic-patterns.dispatch:dispatch.#}")
    private String dispatchTopicPattern;

    @Value("${app.rabbitmq.topic-patterns.report:report.*}")
    private String reportTopicPattern;

    //
    // 1. DECLARACIÓN DE EXCHANGES
    //

    @Bean
    public DirectExchange cmdDirectExchange() {
        return new DirectExchange(directExchangeName, true, false);
    }

    @Bean
    public TopicExchange cmdTopicExchange() {
        return new TopicExchange(topicExchangeName, true, false);
    }

    @Bean
    public DirectExchange cmdDeadLetterExchange() {
        return new DirectExchange(dlxExchangeName, true, false);
    }

    //
    // 2. DECLARACIÓN DE DEAD LETTER QUEUES (DLQ)
    //    Todas las colas son quorum: se replican en los 2 nodos del clúster,
    //    así un mensaje confirmado no se pierde si cae un nodo. Con 2 nodos la
    //    mayoría es 2/2, por lo que la cola queda detenida hasta que el nodo vuelva.
    //

    @Bean
    public Queue emailDlq() {
        return QueueBuilder.durable(emailDlqName)
                .quorum()
                .withArgument("x-message-ttl", 86400000) // TTL de 24 horas en la DLQ
                .build();
    }

    @Bean
    public Queue dispatchDlq() {
        return QueueBuilder.durable(dispatchDlqName)
                .quorum()
                .withArgument("x-message-ttl", 86400000)
                .build();
    }

    @Bean
    public Queue reportDlq() {
        return QueueBuilder.durable(reportDlqName)
                .quorum()
                .withArgument("x-message-ttl", 86400000)
                .build();
    }

    //
    // 3. DECLARACIÓN DE COLAS PRINCIPALES CON CONFIGURACIÓN DLX
    //

    @Bean
    public Queue emailQueue() {
        return QueueBuilder.durable(emailQueueName)
                .quorum()
                .withArgument("x-dead-letter-exchange", dlxExchangeName)
                .withArgument("x-dead-letter-routing-key", emailDeadRoutingKey)
                .build();
    }

    @Bean
    public Queue dispatchQueue() {
        return QueueBuilder.durable(dispatchQueueName)
                .quorum()
                .withArgument("x-dead-letter-exchange", dlxExchangeName)
                .withArgument("x-dead-letter-routing-key", dispatchDeadRoutingKey)
                .build();
    }

    @Bean
    public Queue reportQueue() {
        return QueueBuilder.durable(reportQueueName)
                .quorum()
                .withArgument("x-dead-letter-exchange", dlxExchangeName)
                .withArgument("x-dead-letter-routing-key", reportDeadRoutingKey)
                .build();
    }

    //
    // 4. BINDINGS EN COLAS PRINCIPALES (DIRECT Y TOPIC)
    //

    @Bean
    public Binding emailDirectBinding(Queue emailQueue, DirectExchange cmdDirectExchange) {
        return BindingBuilder.bind(emailQueue).to(cmdDirectExchange).with(emailRoutingKey);
    }

    @Bean
    public Binding emailTopicBinding(Queue emailQueue, TopicExchange cmdTopicExchange) {
        return BindingBuilder.bind(emailQueue).to(cmdTopicExchange).with(emailTopicPattern);
    }

    @Bean
    public Binding dispatchDirectBinding(Queue dispatchQueue, DirectExchange cmdDirectExchange) {
        return BindingBuilder.bind(dispatchQueue).to(cmdDirectExchange).with(dispatchRoutingKey);
    }

    @Bean
    public Binding dispatchTopicBinding(Queue dispatchQueue, TopicExchange cmdTopicExchange) {
        return BindingBuilder.bind(dispatchQueue).to(cmdTopicExchange).with(dispatchTopicPattern);
    }

    @Bean
    public Binding reportDirectBinding(Queue reportQueue, DirectExchange cmdDirectExchange) {
        return BindingBuilder.bind(reportQueue).to(cmdDirectExchange).with(reportRoutingKey);
    }

    @Bean
    public Binding reportTopicBinding(Queue reportQueue, TopicExchange cmdTopicExchange) {
        return BindingBuilder.bind(reportQueue).to(cmdTopicExchange).with(reportTopicPattern);
    }

    //
    // 5. BINDINGS PARA DEAD LETTER QUEUES (DLX)
    //

    @Bean
    public Binding emailDlqBinding(Queue emailDlq, DirectExchange cmdDeadLetterExchange) {
        return BindingBuilder.bind(emailDlq).to(cmdDeadLetterExchange).with(emailDeadRoutingKey);
    }

    @Bean
    public Binding dispatchDlqBinding(Queue dispatchDlq, DirectExchange cmdDeadLetterExchange) {
        return BindingBuilder.bind(dispatchDlq).to(cmdDeadLetterExchange).with(dispatchDeadRoutingKey);
    }

    @Bean
    public Binding reportDlqBinding(Queue reportDlq, DirectExchange cmdDeadLetterExchange) {
        return BindingBuilder.bind(reportDlq).to(cmdDeadLetterExchange).with(reportDeadRoutingKey);
    }
}
