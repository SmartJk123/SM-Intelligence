package com.smi.identity_service.exception;

/**
 * Thrown when an email verification link is unknown, expired, or already used.
 */
public class InvalidEmailVerificationTokenException extends RuntimeException {
    public InvalidEmailVerificationTokenException() {
        super("This verification link is invalid or has expired. Request a new one.");
    }

    public InvalidEmailVerificationTokenException(String message) {
        super(message);
    }
}
