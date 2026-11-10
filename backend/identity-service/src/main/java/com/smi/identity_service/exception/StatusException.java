package com.smi.identity_service.exception;

import org.springframework.http.HttpStatus;

/**
 * A refusal with its own HTTP status and a code the frontends can test, such as
 * 403 PERMISSION_DENIED, 429 TOO_MANY_ATTEMPTS or 503 MAINTENANCE.
 */
public class StatusException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public StatusException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
