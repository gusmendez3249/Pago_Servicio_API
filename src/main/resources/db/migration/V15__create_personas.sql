-- =====================================================================================
-- V15: tabla personas (modulo /personas). Hasta ahora solo la creaba Hibernate (ddl-auto=update);
-- con ddl-auto=validate debe existir por migracion. IF NOT EXISTS: las bases que ya la tienen quedan igual.
-- =====================================================================================

CREATE TABLE IF NOT EXISTS personas (
    id               SERIAL PRIMARY KEY,
    nombre           VARCHAR(255),
    apellido_paterno VARCHAR(255),
    apellido_materno VARCHAR(255)
);
