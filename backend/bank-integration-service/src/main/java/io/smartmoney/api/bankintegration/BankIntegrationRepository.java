package io.smartmoney.api.bankintegration;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BankIntegrationRepository extends JpaRepository<BankIntegrationEntity, String> {
}
