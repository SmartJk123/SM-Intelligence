package io.smartmoney.api.accountlink;

import org.springframework.http.HttpStatus;

/** A refusal with the status and message the admin interface should show. */
public class AccountLinkException extends RuntimeException {

    private final HttpStatus status;

    public AccountLinkException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
