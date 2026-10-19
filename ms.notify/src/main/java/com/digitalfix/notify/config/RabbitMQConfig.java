package com.digitalfix.notify.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class RabbitMQConfig {

    @Value("${digitalfix.rabbitmq.exchanges.direct:cmd.direct}")
    private String directExchangeName;

    @Value("${digitalfix.rabbitmq.exchanges.dlx:cmd.dead.dlx}")
    private String dlxExchangeName;

    @Value("${digitalfix.rabbitmq.queues.email:q.cmd.email}")
    private String emailQueueName;

    @Value("${digitalfix.rabbitmq.queues.email-dlq:q.cmd.email.dlq}")
    private String emailDlqName;

    @Value("${digitalfix.rabbitmq.queues.dispatch:q.cmd.dispatch}")
    private String dispatchQueueName;

    @Value("${digitalfix.rabbitmq.queues.dispatch-dlq:q.cmd.dispatch.dlq}")
    private String dispatchDlqName;

    @Value("${digitalfix.rabbitmq.routing-keys.email:email.send}")
    private String emailRoutingKey;

    @Value("${digitalfix.rabbitmq.routing-keys.email-dlq:email.send.dlq}")
    private String emailDlqRoutingKey;

    @Value("${digitalfix.rabbitmq.routing-keys.dispatch:dispatch.ticket}")
    private String dispatchRoutingKey;

    @Value("${digitalfix.rabbitmq.routing-keys.dispatch-dlq:dispatch.ticket.dlq}")
    private String dispatchDlqRoutingKey;

    // -------------------------------------------------------------------
    // Converter de Mensajes JSON con Soporte para LocalDateTime
    // -------------------------------------------------------------------
    @Bean
    public MessageConverter jsonMessageConverter() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    // -------------------------------------------------------------------
    // Declaración de Exchanges (Direct & Dead Letter Exchange)
    // -------------------------------------------------------------------
    @Bean
    public DirectExchange directExchange() {
        return new DirectExchange(directExchangeName, true, false);
    }

    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(dlxExchangeName, true, false);
    }

    // -------------------------------------------------------------------
    // Declaración de Colas con Configuración de DLX
    // -------------------------------------------------------------------
    @Bean
    public Queue emailQueue() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-dead-letter-exchange", dlxExchangeName);
        args.put("x-dead-letter-routing-key", emailDlqRoutingKey);
        return new Queue(emailQueueName, true, false, false, args);
    }

    @Bean
    public Queue emailDlq() {
        return new Queue(emailDlqName, true, false, false);
    }

    @Bean
    public Queue dispatchQueue() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-dead-letter-exchange", dlxExchangeName);
        args.put("x-dead-letter-routing-key", dispatchDlqRoutingKey);
        return new Queue(dispatchQueueName, true, false, false, args);
    }

    @Bean
    public Queue dispatchDlq() {
        return new Queue(dispatchDlqName, true, false, false);
    }

    // -------------------------------------------------------------------
    // Bindings entre Colas y Exchanges
    // -------------------------------------------------------------------
    @Bean
    public Binding emailBinding() {
        return BindingBuilder.bind(emailQueue()).to(directExchange()).with(emailRoutingKey);
    }

    @Bean
    public Binding emailDlqBinding() {
        return BindingBuilder.bind(emailDlq()).to(deadLetterExchange()).with(emailDlqRoutingKey);
    }

    @Bean
    public Binding dispatchBinding() {
        return BindingBuilder.bind(dispatchQueue()).to(directExchange()).with(dispatchRoutingKey);
    }

    @Bean
    public Binding dispatchDlqBinding() {
        return BindingBuilder.bind(dispatchDlq()).to(deadLetterExchange()).with(dispatchDlqRoutingKey);
    }
}
