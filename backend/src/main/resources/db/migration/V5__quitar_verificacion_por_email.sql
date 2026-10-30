DROP INDEX IF EXISTS uk_usuarios_pending_email;
DROP TABLE IF EXISTS email_verification_tokens;
ALTER TABLE usuarios DROP COLUMN IF EXISTS pending_email;
ALTER TABLE usuarios DROP COLUMN IF EXISTS email_verified;
