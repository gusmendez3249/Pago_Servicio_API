-- =====================================================================================
-- V13: folio y datos del proveedor, confirmTx y motivo en tb_transaccion_pago (Sprint 3)
--
--   folio                     NUM_AUTORIZACION de sendTx/confirmTx: lo que ve el usuario como folio
--   id_tx                     ID_TX del proveedor
--   comision / monto_proveedor / fecha_proveedor   datos informativos del XML (nunca el SALDO del distribuidor)
--   pin_servicio              PIN de tarjeta de regalo, CIFRADO con AES (nunca en claro ni en logs)
--   leyenda                   texto informativo del proveedor para el comprobante de un pago exitoso
--   nombre_servicio / nombre_producto / tipo_front   instantanea del catalogo al reservar (comprobante e historial)
--   origen_monto              PRECIO_FIJO | ADEUDO | CAPTURADO
--   motivo                    motivo enumerado de un rechazo (REFERENCIA_INVALIDA, RECHAZADO_PROVEEDOR, ...)
--   payload_sendtx            JSON exacto enviado a sendTx: confirmTx reenvia los mismos parametros
--   intentos_confirmacion / fecha_ultima_confirmacion   control de las consultas a confirmTx
--
-- Todas las columnas son nulables (o con valor por defecto): las filas anteriores quedan intactas. Las filas sin
-- payload_sendtx no se consultan en confirmTx y se concilian manualmente.
-- =====================================================================================

ALTER TABLE tb_transaccion_pago
    ADD COLUMN IF NOT EXISTS folio                     VARCHAR(30),
    ADD COLUMN IF NOT EXISTS id_tx                     VARCHAR(30),
    ADD COLUMN IF NOT EXISTS comision                  NUMERIC(15,2),
    ADD COLUMN IF NOT EXISTS monto_proveedor           NUMERIC(15,2),
    ADD COLUMN IF NOT EXISTS fecha_proveedor           VARCHAR(30),
    ADD COLUMN IF NOT EXISTS pin_servicio              VARCHAR(512),
    ADD COLUMN IF NOT EXISTS leyenda                   VARCHAR(500),
    ADD COLUMN IF NOT EXISTS nombre_servicio           VARCHAR(150),
    ADD COLUMN IF NOT EXISTS nombre_producto           VARCHAR(150),
    ADD COLUMN IF NOT EXISTS tipo_front                SMALLINT,
    ADD COLUMN IF NOT EXISTS origen_monto              VARCHAR(12),
    ADD COLUMN IF NOT EXISTS motivo                    VARCHAR(40),
    ADD COLUMN IF NOT EXISTS payload_sendtx            TEXT,
    ADD COLUMN IF NOT EXISTS intentos_confirmacion     SMALLINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS fecha_ultima_confirmacion TIMESTAMP;

ALTER TABLE tb_transaccion_pago DROP CONSTRAINT IF EXISTS chk_transaccion_origen_monto;
ALTER TABLE tb_transaccion_pago
    ADD CONSTRAINT chk_transaccion_origen_monto
        CHECK (origen_monto IS NULL OR origen_monto IN ('PRECIO_FIJO', 'ADEUDO', 'CAPTURADO'));

-- Historial por usuario (lo usara el Sprint 4)
CREATE INDEX IF NOT EXISTS idx_transaccion_usuario_fecha ON tb_transaccion_pago (usuario_id, fecha_transaccion DESC);
