package com.smi.identity_service.settings;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/** One saved settings section of the admin portal, as JSON. */
@Entity
@Table(name = "platform_settings")
public class PlatformSetting {

    @Id
    @Column(name = "setting_key", nullable = false)
    private String key;

    @Column(name = "setting_value", nullable = false, length = 20000)
    private String value;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @Column(name = "updated_by")
    private String updatedBy;

    protected PlatformSetting() {
    }

    public PlatformSetting(String key) {
        this.key = key;
    }

    public void update(String value, String updatedBy) {
        this.value = value;
        this.updatedBy = updatedBy;
        this.updatedAt = OffsetDateTime.now();
    }

    public String getKey() { return key; }
    public String getValue() { return value; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public String getUpdatedBy() { return updatedBy; }
}
