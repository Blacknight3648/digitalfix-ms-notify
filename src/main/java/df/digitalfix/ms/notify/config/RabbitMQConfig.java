package df.digitalfix.ms.notify.config;

import df.digitalfix.ms.notify.config.TopologiaRabbitProperties.Flujo;
import org.springframework.amqp.core.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Topología RabbitMQ del caso DigitalFix (sección 8): 3 exchanges, 3 flujos con su DLQ (6 colas)
 * y 9 bindings (direct, topic y DLQ por flujo), todos declarados como beans. Al arrancar, RabbitAdmin los crea en el broker si no
 * existen (la declaración es idempotente). ms-notify es el dueño de la topología porque es quien
 * consume; ms-workorders solo declara los exchanges donde publica.
 * <p>
 * Los nombres vienen de {@link TopologiaRabbitProperties} ({@code app.rabbitmq} en application.yaml).
 * <p>
 * Todas las colas son quorum: se replican en los 2 nodos del clúster, así un mensaje confirmado no
 * se pierde si cae un nodo. Con 2 nodos la mayoría es 2/2, por lo que la cola queda detenida hasta
 * que el nodo vuelva.
 *
 * <pre>
 *   productor ──► cmd.direct ──email.send──────► q.cmd.email ──NACK──► cmd.dead.dlx ──email.dead──► q.cmd.email.dlq
 *             └─► cmd.topic  ──email.*─────────┘
 *   productor ──► cmd.direct ──dispatch.ticket─► q.cmd.dispatch ─NACK─► cmd.dead.dlx ─dispatch.dead─► q.cmd.dispatch.dlq
 *             └─► cmd.topic  ──dispatch.#──────┘
 *   productor ──► cmd.direct ──report.gen──────► q.cmd.report ──NACK──► cmd.dead.dlx ──report.dead──► q.cmd.report.dlq
 *             └─► cmd.topic  ──report.*────────┘
 * </pre>
 */
@Configuration
@EnableConfigurationProperties(TopologiaRabbitProperties.class)
public class RabbitMQConfig {

    private final TopologiaRabbitProperties topologia;

    public RabbitMQConfig(TopologiaRabbitProperties topologia) {
        this.topologia = topologia;
    }

    // =====================================================================
    // EXCHANGES (compartidos por los tres flujos)
    // =====================================================================

    /** cmd.direct: comandos punto a punto, la routing key debe calzar exacto. */
    @Bean
    public DirectExchange cmdDirectExchange() {
        return ExchangeBuilder.directExchange(topologia.exchanges().direct()).durable(true).build();
    }

    /** cmd.topic: los mismos comandos por patrón (email.*, dispatch.#, report.*). */
    @Bean
    public TopicExchange cmdTopicExchange() {
        return ExchangeBuilder.topicExchange(topologia.exchanges().topic()).durable(true).build();
    }

    /** cmd.dead.dlx: Dead Letter Exchange; recibe los mensajes rechazados (NACK sin requeue). */
    @Bean
    public DirectExchange cmdDeadLetterExchange() {
        return ExchangeBuilder.directExchange(topologia.exchanges().dlx()).durable(true).build();
    }

    // =====================================================================
    // FLUJO 1 · EMAIL / PUSH AL CLIENTE
    // Orden creada, asignada, técnico en camino, cerrada → correo HTML (EmailListener).
    // =====================================================================

    @Bean
    public Queue emailQueue() {
        return colaPrincipal(topologia.flujos().email());
    }

    @Bean
    public Queue emailDlq() {
        return colaMuerta(topologia.flujos().email());
    }

    /** cmd.direct ──email.send──► q.cmd.email */
    @Bean
    public Binding emailDirectBinding() {
        return BindingBuilder.bind(emailQueue()).to(cmdDirectExchange()).with(topologia.flujos().email().routingKey());
    }

    /** cmd.topic ──email.*──► q.cmd.email */
    @Bean
    public Binding emailTopicBinding() {
        return BindingBuilder.bind(emailQueue()).to(cmdTopicExchange()).with(topologia.flujos().email().patronTopic());
    }

    /** cmd.dead.dlx ──email.dead──► q.cmd.email.dlq */
    @Bean
    public Binding emailDlqBinding() {
        return BindingBuilder.bind(emailDlq()).to(cmdDeadLetterExchange()).with(topologia.flujos().email().routingKeyDlq());
    }

    // =====================================================================
    // FLUJO 2 · TICKET DE DESPACHO AL TÉCNICO
    // Cuadrilla asignada a la orden → ticket de terreno (DispatchListener).
    // =====================================================================

    @Bean
    public Queue dispatchQueue() {
        return colaPrincipal(topologia.flujos().dispatch());
    }

    @Bean
    public Queue dispatchDlq() {
        return colaMuerta(topologia.flujos().dispatch());
    }

    /** cmd.direct ──dispatch.ticket──► q.cmd.dispatch */
    @Bean
    public Binding dispatchDirectBinding() {
        return BindingBuilder.bind(dispatchQueue()).to(cmdDirectExchange()).with(topologia.flujos().dispatch().routingKey());
    }

    /** cmd.topic ──dispatch.#──► q.cmd.dispatch */
    @Bean
    public Binding dispatchTopicBinding() {
        return BindingBuilder.bind(dispatchQueue()).to(cmdTopicExchange()).with(topologia.flujos().dispatch().patronTopic());
    }

    /** cmd.dead.dlx ──dispatch.dead──► q.cmd.dispatch.dlq */
    @Bean
    public Binding dispatchDlqBinding() {
        return BindingBuilder.bind(dispatchDlq()).to(cmdDeadLetterExchange()).with(topologia.flujos().dispatch().routingKeyDlq());
    }

    // =====================================================================
    // FLUJO 3 · INFORME TÉCNICO / CERTIFICADO PDF
    // Orden cerrada → informe de cierre en PDF (ReportListener).
    // =====================================================================

    @Bean
    public Queue reportQueue() {
        return colaPrincipal(topologia.flujos().report());
    }

    @Bean
    public Queue reportDlq() {
        return colaMuerta(topologia.flujos().report());
    }

    /** cmd.direct ──report.gen──► q.cmd.report */
    @Bean
    public Binding reportDirectBinding() {
        return BindingBuilder.bind(reportQueue()).to(cmdDirectExchange()).with(topologia.flujos().report().routingKey());
    }

    /** cmd.topic ──report.*──► q.cmd.report */
    @Bean
    public Binding reportTopicBinding() {
        return BindingBuilder.bind(reportQueue()).to(cmdTopicExchange()).with(topologia.flujos().report().patronTopic());
    }

    /** cmd.dead.dlx ──report.dead──► q.cmd.report.dlq */
    @Bean
    public Binding reportDlqBinding() {
        return BindingBuilder.bind(reportDlq()).to(cmdDeadLetterExchange()).with(topologia.flujos().report().routingKeyDlq());
    }

    // =====================================================================
    // Construcción común de colas
    // =====================================================================

    /** Cola de trabajo: lo que se rechaza con NACK sin requeue va al DLX con la routing key de su DLQ. */
    private Queue colaPrincipal(Flujo flujo) {
        return QueueBuilder.durable(flujo.cola())
                .quorum()
                .deadLetterExchange(topologia.exchanges().dlx())
                .deadLetterRoutingKey(flujo.routingKeyDlq())
                .build();
    }

    /** DLQ: guarda los mensajes fallidos para revisarlos (DlqListener los registra en el log). */
    private Queue colaMuerta(Flujo flujo) {
        return QueueBuilder.durable(flujo.dlq())
                .quorum()
                .ttl((int) topologia.dlqTtl().toMillis())
                .build();
    }
}
