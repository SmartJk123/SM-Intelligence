package io.smartmoney.api.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.DispatcherType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * The bank webhooks, the Stanbic OAuth callback, the info page and the health
 * check are open, because banks and monitors call them. Everything under
 * /api/v1/admin needs an admin token from identity-service, verified with the
 * shared JWT_SECRET (see AdminTokenVerifier): no token or a bad one is 401, and
 * a customer token is 403.
 *
 * One exception: a signed-in customer may POST /api/v1/admin/account-links to
 * link an account they saved themselves, which the web app does after
 * onboarding. AccountLinkController checks that the account is really theirs.
 *
 * PERMIT_ALL=true switches all of this off. It exists for tests and isolated
 * debugging only, and is off by default.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);
    public static final String CUSTOMER_ROLE = "USER";

    @Bean
    SecurityFilterChain apiSecurity(HttpSecurity http, PlatformProperties platform, ObjectMapper mapper)
            throws Exception {
        PlatformProperties.Security security = platform.security();
        boolean permitAll = security != null && security.permitAll();

        http.csrf(csrf -> csrf.disable());
        http.headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()));
        http.cors(Customizer.withDefaults());
        http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        if (permitAll) {
            log.warn("PERMIT_ALL is true: the admin API is open to anyone who can reach this service");
            http.authorizeHttpRequests(registry -> registry.anyRequest().permitAll());
            return http.build();
        }

        AdminTokenVerifier verifier = new AdminTokenVerifier(security == null ? null : security.jwtSecret(), mapper);
        if (!verifier.configured()) {
            log.warn("JWT_SECRET is not set (or shorter than 32 characters): every admin API call will be refused. "
                    + "Set it to the same value identity-service uses.");
        }

        http.authorizeHttpRequests(registry -> registry
                // A refusal is rendered by an error dispatch that carries no token;
                // denying it would turn every 403 or 404 into a misleading 401.
                .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                .requestMatchers("/", "/oauth/**", "/api/v1/webhooks/**", "/actuator/health", "/actuator/info")
                .permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/admin/account-links")
                .hasAnyRole(AdminTokenVerifier.ADMIN_ROLE, CUSTOMER_ROLE)
                .requestMatchers("/api/v1/admin/**").hasRole(AdminTokenVerifier.ADMIN_ROLE)
                .anyRequest().denyAll());
        http.exceptionHandling(errors -> errors.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)));
        http.addFilterBefore(new AdminTokenFilter(verifier), UsernamePasswordAuthenticationFilter.class);
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
