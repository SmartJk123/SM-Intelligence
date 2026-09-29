package io.smartmoney.api.accountlink;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AccountLinkRepository extends JpaRepository<AccountLinkEntity, Long> {

    Optional<AccountLinkEntity> findByBankIdAndAccountNumber(String bankId, String accountNumber);

    List<AccountLinkEntity> findByUserIdOrderByCreatedAtAsc(String userId);

    List<AccountLinkEntity> findAllByOrderByCreatedAtAsc();
}
