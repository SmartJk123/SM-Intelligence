package io.smartmoney.api.bankintegration.ncba;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** How far an import has read from a remote source, so a restart carries on where it stopped. */
@Entity
@Table(name = "import_cursor")
public class ImportCursorEntity {

    @Id
    @Column(name = "source", length = 60)
    private String source;

    @Column(name = "last_id", nullable = false)
    private long lastId;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected ImportCursorEntity() {
    }

    public ImportCursorEntity(String source) {
        this.source = source;
    }

    public long getLastId() { return lastId; }

    public void setLastId(long lastId) {
        this.lastId = lastId;
        this.updatedAt = Instant.now();
    }
}
