-- ============================================================
-- TABLA: TOKENS DE RESETEO DE CONTRASEÑA
-- Módulo 1: Autenticación y Gestión de Usuarios (RF-005)
-- ============================================================

CREATE TABLE IF NOT EXISTS password_reset_tokens (
    id              BIGSERIAL PRIMARY KEY,
    usuario_id      BIGINT       NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
    token           VARCHAR(64)  NOT NULL UNIQUE,
    expira_en       TIMESTAMP    NOT NULL,
    usado           BOOLEAN      NOT NULL DEFAULT FALSE,
    fecha_uso       TIMESTAMP,
    ip_solicitud    VARCHAR(45),
    user_agent      TEXT,
    fecha_creacion  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_reset_token    ON password_reset_tokens(token);
CREATE INDEX IF NOT EXISTS idx_reset_usuario  ON password_reset_tokens(usuario_id);
CREATE INDEX IF NOT EXISTS idx_reset_expira   ON password_reset_tokens(expira_en);

COMMENT ON TABLE password_reset_tokens IS 
    'Tokens de un solo uso para restablecer contraseña. Expiran en 15 min.';