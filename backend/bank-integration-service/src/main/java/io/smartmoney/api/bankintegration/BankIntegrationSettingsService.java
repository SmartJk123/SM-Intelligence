package io.smartmoney.api.bankintegration;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BankIntegrationSettingsService implements SignaturePolicy {

    private final BankIntegrationRepository repository;

    public BankIntegrationSettingsService(BankIntegrationRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public BankConnectionSettings settingsFor(String bankId) {
        return repository.findById(bankId)
                .map(this::toSettings)
                .orElseGet(() -> BankConnectionSettings.defaults(bankId));
    }

    /**
     * The stored setting is the authority, so the toggle in the admin interface
     * changes what the webhook endpoints do. Until something has been saved the
     * value comes from the environment variable, which is what keeps a fresh
     * checkout failing closed. A database that cannot be read falls back to the
     * same place rather than silently switching verification off.
     */
    @Override
    @Transactional(readOnly = true)
    public boolean verifies(String bankId, boolean whenUnset) {
        try {
            return repository.findById(bankId)
                    .map(BankIntegrationEntity::isSignatureVerification)
                    .orElse(whenUnset);
        } catch (RuntimeException error) {
            return whenUnset;
        }
    }

    @Transactional
    public BankConnectionSettings save(String bankId, BankConnectionSettings settings) {
        BankIntegrationEntity entity = repository.findById(bankId)
                .orElseGet(() -> new BankIntegrationEntity(bankId));

        if (settings.environment() != null) {
            entity.setEnvironment(settings.environment());
        }
        entity.setApiTimeoutSeconds(settings.apiTimeoutSeconds());
        entity.setRetryAttempts(settings.retryAttempts());
        entity.setRetryDelaySeconds(settings.retryDelaySeconds());
        entity.setSignatureVerification(settings.signatureVerification());
        entity.setAutomaticRetry(settings.automaticRetry());
        entity.touch();

        return toSettings(repository.save(entity));
    }

    @Transactional
    public void rememberAccountNumber(String bankId, String accountNumber) {
        BankIntegrationEntity entity = repository.findById(bankId)
                .orElseGet(() -> new BankIntegrationEntity(bankId));
        entity.setAccountNumber(accountNumber);
        entity.touch();
        repository.save(entity);
    }

    private BankConnectionSettings toSettings(BankIntegrationEntity entity) {
        return new BankConnectionSettings(
                entity.getBankId(),
                entity.getEnvironment(),
                entity.getApiTimeoutSeconds(),
                entity.getRetryAttempts(),
                entity.getRetryDelaySeconds(),
                entity.isSignatureVerification(),
                entity.isAutomaticRetry());
    }
}
