package io.smartmoney.api.bankintegration;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BankConnectionTestRepository extends JpaRepository<BankConnectionTestEntity, String> {
}
