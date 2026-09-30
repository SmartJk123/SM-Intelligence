package io.smartmoney.api.bankintegration.ncba;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ImportCursorRepository extends JpaRepository<ImportCursorEntity, String> {
}
