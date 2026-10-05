package com.smi.identity_service.config;

import com.smi.identity_service.domain.User;
import jakarta.servlet.DispatcherType;
import com.smi.identity_service.security.JwtAuthenticationFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Security configuration for identity-service.
 * Allows unauthenticated access to health actuator and auth endpoints,
 * whilst keeping API stateless.
 *
 * CORS is answered only by api-gateway (see its CorsConfig), never here:
 * a backend route's headers would be proxied through and stack with the
 * gateway's, producing a duplicate Access-Control-Allow-Origin header that
 * browsers reject outright.
 *
 * Everything the admin interface uses (users, organisations) requires a Bearer
 * token belonging to an active PLATFORM_ADMIN account. No token, or a token for
 * a suspended or deleted account, is answered 401; a customer token is 403.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationFilter jwtFilter) throws Exception {
        String admin = User.ROLE_PLATFORM_ADMIN;
        http
            .cors(AbstractHttpConfigurer::disable)
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // A 403 or 404 is rendered by an error dispatch, which carries no token.
                // Denying it would turn every refusal into a misleading 401.
                .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                .requestMatchers("/actuator/**").permitAll()
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/api/organizations/**").hasRole(admin)
                .requestMatchers("/api/v1/admin/**").hasRole(admin)
                .anyRequest().denyAll()
            )
            .exceptionHandling(errors -> errors.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * The filter is a component so it can be injected above. Without this it
     * would also run as a plain servlet filter outside the security chain.
     */
    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtFilterRegistration(JwtAuthenticationFilter filter) {
        FilterRegistrationBean<JwtAuthenticationFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}
