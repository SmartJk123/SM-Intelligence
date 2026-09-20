package io.smartmoney.api.bankintegration;

/**
 * Whether notifications from a bank have to be verified.
 *
 * The admin interface owns this switch. An operator can turn verification off
 * for a trial and back on before production, and the stored value is what the
 * webhook endpoints then obey. The environment variable is only the value used
 * until something has been saved, so a fresh checkout fails closed.
 *
 * This exists as an interface so the verifiers can be tested without a
 * database, and so a bank package does not have to know how settings are
 * stored.
 */
public interface SignaturePolicy {

    /**
     * @param bankId    the bank whose notifications are arriving
     * @param whenUnset the value to use when no setting has been saved yet
     * @return true when an incoming notification must be verified
     */
    boolean verifies(String bankId, boolean whenUnset);
}
