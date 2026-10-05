-- NCBA notifications received by the cPanel endpoint.
-- Run once in cPanel phpMyAdmin against the database named in ncba-config.php.
--
-- trans_id is unique, which is what makes a notification delivered twice be
-- stored once: the second insert fails and the endpoint answers
-- "OK: Duplicate Notification".

CREATE TABLE IF NOT EXISTS ncba_notifications (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    trans_id        VARCHAR(128)    NOT NULL,
    trans_type      VARCHAR(32)     NULL,
    trans_time      VARCHAR(32)     NULL COMMENT 'As sent, YYMMDDhhmm East Africa Time',
    booked_at       DATETIME        NULL COMMENT 'trans_time converted to UTC',
    amount          DECIMAL(18, 2)  NULL COMMENT 'Magnitude, direction is separate',
    direction       VARCHAR(8)      NULL COMMENT 'Credit or Debit, from the sign of TransAmount',
    amount_text     VARCHAR(64)     NULL COMMENT 'TransAmount exactly as sent',
    account_nr      VARCHAR(64)     NULL,
    narrative       VARCHAR(512)    NULL,
    phone_nr        VARCHAR(32)     NULL,
    customer_name   VARCHAR(255)    NULL,
    status          VARCHAR(64)     NULL,
    signature_valid TINYINT(1)      NULL COMMENT '1 verified, NULL when verification was off',
    raw_body        MEDIUMTEXT      NOT NULL COMMENT 'Body as received, password redacted',
    remote_addr     VARCHAR(64)     NULL,
    received_at     DATETIME        NOT NULL COMMENT 'UTC',
    UNIQUE KEY uq_ncba_notifications_trans_id (trans_id),
    KEY ix_ncba_notifications_received_at (received_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- KCB instant payment notifications received by the cPanel endpoint
-- (kcb-webhook.php). transaction_reference is unique, so a notification KCB
-- delivers twice is stored once and acknowledged as a duplicate.

CREATE TABLE IF NOT EXISTS kcb_notifications (
    id                      BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    transaction_reference   VARCHAR(128)    NOT NULL,
    request_id              VARCHAR(128)    NULL,
    channel_code            VARCHAR(32)     NULL,
    timestamp_text          VARCHAR(32)     NULL COMMENT 'As sent, yyyyMMddHHmmss East Africa Time',
    booked_at               DATETIME        NULL COMMENT 'timestamp converted to UTC',
    amount                  DECIMAL(18, 2)  NULL,
    amount_text             VARCHAR(64)     NULL COMMENT 'transactionAmount exactly as sent',
    currency                VARCHAR(8)      NULL,
    customer_reference      VARCHAR(128)    NULL,
    customer_name           VARCHAR(255)    NULL,
    customer_mobile         VARCHAR(32)     NULL,
    narration               VARCHAR(512)    NULL,
    credit_account          VARCHAR(64)     NULL COMMENT 'creditAccountIdentifier, the credited KCB account',
    organization_short_code VARCHAR(32)     NULL,
    till_number             VARCHAR(32)     NULL,
    balance_text            VARCHAR(64)     NULL,
    signature_valid         TINYINT(1)      NULL COMMENT '1 verified, NULL when verification was off',
    raw_body                MEDIUMTEXT      NOT NULL COMMENT 'Body exactly as received',
    remote_addr             VARCHAR(64)     NULL,
    received_at             DATETIME        NOT NULL COMMENT 'UTC',
    UNIQUE KEY uq_kcb_notifications_reference (transaction_reference),
    KEY ix_kcb_notifications_received_at (received_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Equity (Jenga) Instant Payment Notifications received by the cPanel endpoint
-- (equity-webhook.php). reference is unique, so a repeated notification is
-- stored once and acknowledged as a duplicate. Failed payments are stored too
-- (status FAILED) and are never credited.

CREATE TABLE IF NOT EXISTS equity_notifications (
    id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    reference        VARCHAR(128)    NOT NULL COMMENT 'transaction.reference, else bank.reference',
    bank_reference   VARCHAR(128)    NULL,
    payment_mode     VARCHAR(32)     NULL COMMENT 'CARD, MPESA, PWE, EQUITEL or PAYPAL',
    transaction_date VARCHAR(32)     NULL COMMENT 'As sent, yyyy-MM-dd HH:mm:ss East Africa Time',
    amount           DECIMAL(18, 2)  NULL,
    currency         VARCHAR(8)      NULL,
    status           VARCHAR(16)     NULL COMMENT 'SUCCESS or FAILED',
    remarks          VARCHAR(255)    NULL,
    bill_number      VARCHAR(128)    NULL,
    customer_name    VARCHAR(255)    NULL,
    customer_mobile  VARCHAR(32)     NULL,
    account          VARCHAR(64)     NULL,
    transaction_type VARCHAR(8)      NULL COMMENT 'C for a credit',
    signature_valid  TINYINT(1)      NULL COMMENT '1 Basic Auth checked, NULL when checking was off',
    raw_body         MEDIUMTEXT      NOT NULL COMMENT 'Body exactly as received',
    remote_addr      VARCHAR(64)     NULL,
    received_at      DATETIME        NOT NULL COMMENT 'UTC',
    UNIQUE KEY uq_equity_notifications_reference (reference),
    KEY ix_equity_notifications_received_at (received_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
