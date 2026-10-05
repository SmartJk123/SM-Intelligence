package io.smartmoney.api.config;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The webhook base address, read and changed from the admin interface's Bank
 * Integration Settings. Every bank's webhook URL is this address followed by
 * /api/v1/webhooks/{bank}.
 */
@RestController
@RequestMapping("/api/v1/admin/bank-integrations/platform/public-base-url")
public class PublicBaseUrlController {

    private final PublicBaseUrlService service;
    private final PlatformProperties platform;

    public PublicBaseUrlController(PublicBaseUrlService service, PlatformProperties platform) {
        this.service = service;
        this.platform = platform;
    }

    @GetMapping
    public Map<String, Object> current() {
        return body();
    }

    @PutMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> update(@RequestBody(required = false) Map<String, String> request) {
        service.update(request == null ? null : request.get("publicBaseUrl"));
        return body();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> refused(IllegalArgumentException error) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("status", 400, "message", error.getMessage()));
    }

    private Map<String, Object> body() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("publicBaseUrl", service.current());
        body.put("callbackIsPublic", platform.callbackIsPublic());
        return body;
    }
}
