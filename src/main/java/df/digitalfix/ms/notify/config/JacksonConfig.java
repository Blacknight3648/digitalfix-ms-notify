package df.digitalfix.ms.notify.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.amqp.support.converter.SimpleMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class JacksonConfig {

    @Bean
    @Primary
    public MessageConverter jsonMessageConverter() {
        ObjectMapper objectMapper = new ObjectMapper();
        // Registra el módulo para manejar serialización de fechas como LocalDateTime
        objectMapper.registerModule(new JavaTimeModule());
        // Lector tolerante: los productores (ms-workorders) pueden enviar campos extra (crewId,
        // orderStatus...) sin que el mensaje falle y termine en la DLQ.
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    /**
     * Convertidor sin JSON para el listener de cuarentena (DlqListener): recibe el mensaje crudo
     * para registrarlo tal cual, aunque su cuerpo no calce con ningún DTO.
     */
    @Bean
    public MessageConverter rawMessageConverter() {
        return new SimpleMessageConverter();
    }
}
