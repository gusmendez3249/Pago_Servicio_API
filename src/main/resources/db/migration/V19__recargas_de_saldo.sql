-- =====================================================================================
-- V19: recargas de saldo hechas por un ejecutivo (administrador). Hoy es la unica forma de abonar dinero a una cuenta.
-- Cada recarga queda auditada: quien la hizo, a que cuenta, cuanto y el saldo resultante. La Idempotency-Key evita
-- abonar dos veces si la misma solicitud se repite.
-- =====================================================================================

CREATE TABLE IF NOT EXISTS tb_recarga_saldo (
    id                BIGSERIAL     PRIMARY KEY,
    cuenta_id         BIGINT        NOT NULL REFERENCES tb_cuenta (id),
    monto             NUMERIC(15,2) NOT NULL CHECK (monto > 0),
    saldo_resultante  NUMERIC(15,2) NOT NULL,
    ejecutivo_id      BIGINT        NOT NULL REFERENCES tb_usuario_login (id),
    idempotency_key   VARCHAR(36)   NOT NULL UNIQUE,
    hash_solicitud    VARCHAR(64)   NOT NULL,
    fecha             TIMESTAMP     NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_recarga_fecha ON tb_recarga_saldo (fecha DESC);
CREATE INDEX IF NOT EXISTS idx_recarga_cuenta ON tb_recarga_saldo (cuenta_id);
