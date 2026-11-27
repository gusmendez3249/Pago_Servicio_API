-- =====================================================================================
-- V10: Autenticación por token y roles (autorización a nivel de recurso)
--
--   rol          CLIENTE   -> solo puede consultar/actualizar SU propio registro
--                EJECUTIVO -> puede operar sobre cualquier cliente (usuario sin cliente asociado)
--   token_hash   SHA-256 (hex) del token de sesión. El token en claro nunca se guarda.
--   token_expira vencimiento del token (se renueva con cada petición autenticada)
-- =====================================================================================

ALTER TABLE tb_usuario_login
    ADD COLUMN IF NOT EXISTS rol          VARCHAR(10) NOT NULL DEFAULT 'CLIENTE',
    ADD COLUMN IF NOT EXISTS token_hash   VARCHAR(64),
    ADD COLUMN IF NOT EXISTS token_expira TIMESTAMP;

-- Los usuarios que ya existían sin cliente asociado eran cuentas de servicio/administración
UPDATE tb_usuario_login SET rol = 'EJECUTIVO' WHERE cliente_id IS NULL;

ALTER TABLE tb_usuario_login
    ADD CONSTRAINT chk_usuario_rol CHECK (rol IN ('CLIENTE', 'EJECUTIVO')),
    -- un ejecutivo no pertenece a un cliente; un usuario cliente siempre pertenece a uno
    ADD CONSTRAINT chk_usuario_rol_cliente CHECK (
        (rol = 'EJECUTIVO' AND cliente_id IS NULL) OR (rol = 'CLIENTE' AND cliente_id IS NOT NULL)
    );

-- Búsqueda del usuario por token en cada petición autenticada (solo filas con sesión activa)
CREATE UNIQUE INDEX IF NOT EXISTS uq_usuario_token
    ON tb_usuario_login (token_hash) WHERE token_hash IS NOT NULL;
