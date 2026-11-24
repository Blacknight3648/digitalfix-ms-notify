package df.digitalfix.ms.notify.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health/**", "/actuator/info").permitAll() // Permitir salud pública
                // Control de listeners: solo lo llama ms-admin-queues por la red interna digitalfix-net
                // (el puerto 8083 no se publica); el BFF exige SUPERADMIN antes de llegar ahí.
                .requestMatchers("/api/v1/listeners", "/api/v1/listeners/**").permitAll()
                .anyRequest().authenticated()
            );
        return http.build();
    }
}
