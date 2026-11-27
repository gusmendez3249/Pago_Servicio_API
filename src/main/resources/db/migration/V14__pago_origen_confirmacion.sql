-- =====================================================================================
-- V14: origen de la confirmacion de un pago (GESTOPAGO | SIMULADO)
--
--   origen_confirmacion   GESTOPAGO = el proveedor aplico el pago; SIMULADO = GestoPago no funciono al pagar y el
--                         backend lo dio por exitoso (pagos.simulacion.habilitada). Nulo mientras no este CONFIRMADA.
--
-- Las filas CONFIRMADA anteriores solo pudieron confirmarse por GestoPago (o un ejecutivo): quedan como GESTOPAGO.
-- =====================================================================================

ALTER TABLE tb_transaccion_pago
    ADD COLUMN IF NOT EXISTS origen_confirmacion VARCHAR(10);

UPDATE tb_transaccion_pago
   SET origen_confirmacion = 'GESTOPAGO'
 WHERE estatus = 'CONFIRMADA' AND origen_confirmacion IS NULL;

ALTER TABLE tb_transaccion_pago DROP CONSTRAINT IF EXISTS chk_transaccion_origen_confirmacion;
ALTER TABLE tb_transaccion_pago
    ADD CONSTRAINT chk_transaccion_origen_confirmacion
        CHECK (origen_confirmacion IS NULL OR origen_confirmacion IN ('GESTOPAGO', 'SIMULADO'));

-- Reportes de pagos simulados frente a reales
CREATE INDEX IF NOT EXISTS idx_transaccion_origen_confirmacion ON tb_transaccion_pago (origen_confirmacion);
