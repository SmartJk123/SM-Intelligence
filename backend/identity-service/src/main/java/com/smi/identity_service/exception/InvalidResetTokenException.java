package com.smi.identity_service.exception;

/** The reset link is unknown, expired or already used. */
public class InvalidResetTokenException extends RuntimeException {
    public InvalidResetTokenException() {
        super("This reset link is invalid or has expired. Request a new one.");
    }
}
