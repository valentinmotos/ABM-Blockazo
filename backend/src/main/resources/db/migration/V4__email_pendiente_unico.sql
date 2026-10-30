CREATE UNIQUE INDEX uk_usuarios_pending_email ON usuarios(pending_email) WHERE pending_email IS NOT NULL;
