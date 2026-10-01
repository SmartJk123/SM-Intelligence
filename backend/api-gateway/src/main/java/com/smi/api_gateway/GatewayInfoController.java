package com.smi.api_gateway;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * A friendly root response instead of Spring's default Whitelabel error page.
 * This gateway only ever proxies specific /api/** paths; nothing is meant to
 * live at / itself, so this exists purely so a visit to the bare address in a
 * browser confirms the gateway is up rather than looking like a crash.
 */
@RestController
public class GatewayInfoController {

    @GetMapping("/")
    public Map<String, String> root() {
        return Map.of(
                "service", "api-gateway",
                "status", "up",
                "note", "This gateway only proxies /api/** routes. There is nothing else to see here.");
    }
}
