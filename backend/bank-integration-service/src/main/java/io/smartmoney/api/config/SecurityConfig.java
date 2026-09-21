package io.smartmoney.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Development configuration permits everything so the interface can be tested
 * immediately. Set PERMIT_ALL=false before any shared or public deployment, then
 * replace HTTP Basic with the real token based authentication.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain apiSecurity(HttpSecurity http, PlatformProperties platform) throws Exception {
        boolean permitAll = platform.security() == null || platform.security().permitAll();

        http.csrf(csrf -> csrf.disable());
        http.headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()));
        http.cors(Customizer.withDefaults());
        http.authorizeHttpRequests(registry -> {
            if (permitAll) {
                registry.anyRequest().permitAll();
                return;
            }
            registry.requestMatchers("/", "/oauth/**", "/api/v1/webhooks/**", "/actuator/health")
                    .permitAll();
            registry.requestMatchers("/api/v1/admin/**").authenticated();
            registry.anyRequest().authenticated();
        });

        if (!permitAll) {
            http.httpBasic(Customizer.withDefaults());
        }
        return http.build();
    }

    /**
     * The Angular development server uses a port of its own choosing, so every
     * localhost port is allowed while developing. Narrow this before release.
     */
    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of("http://localhost:*", "http://127.0.0.1:*"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
