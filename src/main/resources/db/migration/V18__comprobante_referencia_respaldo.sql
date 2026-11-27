-- =====================================================================================
-- V18: respaldo en PostgreSQL de los comprobantes de validacion de referencia.
-- Redis sigue siendo el almacen principal; si no responde, el comprobante se emite y se consume aqui, de modo que
-- los pagos con referencia no dependen de que Redis este disponible.
-- =====================================================================================

CREATE TABLE IF NOT EXISTS tb_comprobante_referencia (
    id_validacion VARCHAR(36)  PRIMARY KEY,
    valor         TEXT         NOT NULL,
    expira        TIMESTAMP    NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_comprobante_referencia_expira ON tb_comprobante_referencia (expira);
