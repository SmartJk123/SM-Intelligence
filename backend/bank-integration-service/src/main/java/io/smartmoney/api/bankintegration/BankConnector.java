package io.smartmoney.api.bankintegration;

/**
 * One implementation per bank. Adding a bank means adding a connector, not
 * changing the transaction domain.
 */
public interface BankConnector {

    String bankId();

    /** Human readable provider name, for example Stanbic Bank Kenya. */
    String displayName();

    BankConnectionTest testConnection(BankConnectionSettings settings);
}
