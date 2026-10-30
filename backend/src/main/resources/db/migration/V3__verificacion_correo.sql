ALTER TABLE usuarios ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE usuarios ADD COLUMN pending_email VARCHAR(254);

CREATE TABLE email_verification_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id UUID NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL,
    purpose VARCHAR(24) NOT NULL,
    target_email VARCHAR(254) NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    consumed_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uk_email_verification_hash UNIQUE (token_hash)
);
CREATE INDEX ix_email_verification_user_purpose ON email_verification_tokens(usuario_id, purpose);
