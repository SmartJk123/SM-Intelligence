package com.smi.api_gateway;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * The browser only ever talks to this gateway, so CORS has to be answered
 * here. Spring Cloud Gateway (webmvc) answers an OPTIONS preflight itself
 * before a route ever proxies it, so a route's backend never sees preflight
 * requests at all; configuring CORS on identity-service (or any other
 * backend) has no effect on what the browser receives. Do not also enable
 * CORS on a backend behind this gateway: its headers would be proxied
 * through on the real request and stack with this filter's, producing a
 * duplicate Access-Control-Allow-Origin header that browsers reject outright.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Value("${APP_CORS_ALLOWED_ORIGINS:http://localhost:4200,http://localhost:4300}")
    private String allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins(allowedOrigins.split(","))
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
    }
}
