-- =====================================================================================
-- V12: Pago de servicios robusto (reserva de saldo, idempotencia, conciliación) y bloqueo del PIN
--
--   tb_cliente.pin                    a partir de esta versión se guarda como hash PBKDF2 (los PIN en claro
--                                     existentes se re-cifran automáticamente en su siguiente uso correcto)
--   tb_cliente.pin_intentos_fallidos  intentos fallidos consecutivos del PIN
--   tb_cliente.pin_bloqueado_hasta    bloqueo temporal tras 3 intentos fallidos
--
--   tb_transaccion_pago               ahora registra TODA operación desde que se reserva el saldo:
--     estatus            PENDIENTE      saldo reservado, envío a GestoPago en curso
--                        CONFIRMADA     GestoPago respondió CODIGO 01
--                        RECHAZADA      el proveedor rechazó o la petición no llegó a enviarse; saldo reintegrado
--                        INDETERMINADA  no se sabe si el proveedor la aplicó (timeout, 5xx, respuesta ilegible):
--                                       el saldo queda retenido hasta conciliar; NUNCA se reintenta ni se reintegra solo
--     idempotency_key    UUID enviado por el cliente en el encabezado Idempotency-Key (único)
--     hash_solicitud     SHA-256 de los datos de la solicitud (detecta reuso de la llave con otros datos)
--     upc                identificador de la operación ante GestoPago: exactamente 10 caracteres [A-Z0-9]
-- =====================================================================================

ALTER TABLE tb_cliente
    ADD COLUMN IF NOT EXISTS pin_intentos_fallidos SMALLINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS pin_bloqueado_hasta   TIMESTAMP;

-- Las filas previas (V11) solo contenían pagos exitosos: quedan como CONFIRMADA
ALTER TABLE tb_transaccion_pago
    ADD COLUMN IF NOT EXISTS estatus             VARCHAR(15) NOT NULL DEFAULT 'CONFIRMADA',
    ADD COLUMN IF NOT EXISTS idempotency_key     VARCHAR(36),
    ADD COLUMN IF NOT EXISTS hash_solicitud      VARCHAR(64),
    ADD COLUMN IF NOT EXISTS usuario_id          BIGINT REFERENCES tb_usuario_login (id),
    ADD COLUMN IF NOT EXISTS fecha_actualizacion TIMESTAMP;

ALTER TABLE tb_transaccion_pago ALTER COLUMN estatus DROP DEFAULT;
-- Mientras la operación está PENDIENTE o INDETERMINADA aún no hay código del proveedor
ALTER TABLE tb_transaccion_pago ALTER COLUMN codigo_respuesta DROP NOT NULL;

ALTER TABLE tb_transaccion_pago
    ADD CONSTRAINT chk_transaccion_estatus
        CHECK (estatus IN ('PENDIENTE', 'CONFIRMADA', 'RECHAZADA', 'INDETERMINADA'));

-- NOT VALID: se exige a toda fila nueva sin invalidar los registros de prueba creados con V11
ALTER TABLE tb_transaccion_pago
    ADD CONSTRAINT chk_transaccion_upc CHECK (upc ~ '^[A-Z0-9]{10}$') NOT VALID;
ALTER TABLE tb_transaccion_pago
    ADD CONSTRAINT chk_transaccion_idempotency
        CHECK (idempotency_key ~ '^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$') NOT VALID;

-- Evita cargos duplicados: una llave de idempotencia solo puede generar una operación
CREATE UNIQUE INDEX IF NOT EXISTS uq_transaccion_idempotency ON tb_transaccion_pago (idempotency_key);
-- Búsqueda de la conciliación (operaciones PENDIENTE / INDETERMINADA antiguas)
CREATE INDEX IF NOT EXISTS idx_transaccion_estatus_fecha ON tb_transaccion_pago (estatus, fecha_transaccion);
