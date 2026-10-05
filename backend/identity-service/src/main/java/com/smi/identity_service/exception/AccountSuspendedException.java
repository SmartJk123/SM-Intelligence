package com.smi.identity_service.exception;

/** The user exists and the credentials are right, but an administrator has suspended the account. */
public class AccountSuspendedException extends RuntimeException {
    public AccountSuspendedException() {
        super("This account has been suspended. Contact support to restore access.");
    }
}
