package com.smi.identity_service.activity;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailTemplateOverrideRepository extends JpaRepository<EmailTemplateOverride, String> {
}
