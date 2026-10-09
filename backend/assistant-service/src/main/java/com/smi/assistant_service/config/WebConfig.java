package com.smi.assistant_service.config;

import org.springframework.context.annotation.Configuration;

/**
 * No CORS here on purpose: browsers reach this service only through
 * api-gateway, which answers CORS from its own allow-list
 * (APP_CORS_ALLOWED_ORIGINS). A second set of headers from this service would
 * be proxied alongside the gateway's, and the wildcard that used to be here
 * let any website call the service with credentials.
 */
@Configuration
public class WebConfig {

    @org.springframework.context.annotation.Bean
    public com.fasterxml.jackson.databind.ObjectMapper objectMapper() {
        return new com.fasterxml.jackson.databind.ObjectMapper();
    }
}
