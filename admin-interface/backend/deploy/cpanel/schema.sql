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
