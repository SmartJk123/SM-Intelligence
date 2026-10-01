package com.smi.identity_service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security configuration for identity-service.
 * Allows unauthenticated access to health actuator and auth endpoints,
 * whilst keeping API stateless.
 *
 * CORS is deliberately not configured here: api-gateway's CorsConfig answers
 * every browser preflight before a route ever proxies to this service, so
 * CORS headers from here would never reach the browser on a preflight, and
 * would stack into a duplicate Access-Control-Allow-Origin header (which
 * browsers reject outright) on the real request.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/**").permitAll()
                .requestMatchers("/api/auth/**").permitAll()
                // SECURITY GAP: admin-interface has no real authentication against this service yet
                // (its AuthService is a local-only signal). This endpoint only reads data
                // (no destructive/account-takeover primitive), so it is left open for now,
                // but it must be locked down once admin-interface gets real admin auth.
                .requestMatchers("/api/v1/admin/users/**").permitAll()
                .anyRequest().denyAll()
            );

        return http.build();
    }
}
