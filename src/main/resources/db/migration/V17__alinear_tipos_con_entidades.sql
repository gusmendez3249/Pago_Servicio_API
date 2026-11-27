-- =====================================================================================
-- V17: tipos de columna alineados con las entidades JPA. Con ddl-auto=update Hibernate toleraba la diferencia;
-- con ddl-auto=validate el arranque falla si el tipo no coincide. Ampliar SMALLINT a INTEGER no pierde datos.
-- =====================================================================================

ALTER TABLE tb_transaccion_pago ALTER COLUMN tipo_front TYPE INTEGER;
ALTER TABLE tb_transaccion_pago ALTER COLUMN intentos_confirmacion TYPE INTEGER;
