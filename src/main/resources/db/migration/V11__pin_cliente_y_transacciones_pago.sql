-- =====================================================================================
-- V11: PIN (2FA) del cliente y registro de transacciones de pago de servicios (sendTx)
--
--   tb_cliente.pin           PIN del cliente para autorizar pagos (nullable: clientes existentes sin PIN)
--   tb_transaccion_pago      una fila por cada pago exitoso (CODIGO "01" de GestoPago)
-- =====================================================================================

ALTER TABLE tb_cliente
    ADD COLUMN IF NOT EXISTS pin VARCHAR(100);

CREATE TABLE IF NOT EXISTS tb_transaccion_pago (
    id                BIGSERIAL     PRIMARY KEY,
    cuenta_id         BIGINT        NOT NULL REFERENCES tb_cuenta (id),
    id_servicio       INTEGER       NOT NULL,
    id_producto       INTEGER       NOT NULL,
    monto             NUMERIC(15,2) NOT NULL CHECK (monto > 0),
    telefono          VARCHAR(10),
    referencia        VARCHAR(100),
    upc               VARCHAR(15)   NOT NULL UNIQUE,
    codigo_respuesta  VARCHAR(10)   NOT NULL,
    texto_respuesta   VARCHAR(500),
    fecha_transaccion TIMESTAMP     NOT NULL,
    -- una operación usa teléfono o referencia, nunca ambos ni ninguno
    CONSTRAINT chk_transaccion_tel_o_ref CHECK (
        (telefono IS NOT NULL AND referencia IS NULL) OR (telefono IS NULL AND referencia IS NOT NULL)
    )
);

CREATE INDEX IF NOT EXISTS idx_transaccion_cuenta ON tb_transaccion_pago (cuenta_id);
