-- Multi-Factor Authentication (TOTP) credentials and emergency recovery backup codes.
ALTER TABLE users ADD COLUMN mfa_secret VARCHAR(64);
ALTER TABLE users ADD COLUMN mfa_backup_codes TEXT;
