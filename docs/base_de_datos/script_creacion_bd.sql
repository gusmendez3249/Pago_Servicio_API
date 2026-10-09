-- =====================================================================================
--  Proyecto Integrador: Onboarding de Clientes Personas Físicas
--  Script de creación de la base de datos (PostgreSQL 14+)
--
--  Esquema final consolidado. En la aplicación este mismo esquema lo construye Flyway
--  (src/main/resources/db/migration V3 + V8 + V9); este archivo sirve para crear la base
--  manualmente desde cero y como documentación del diseño.
--
--  Uso:
--    CREATE DATABASE pago_servicios_db;
--    \c pago_servicios_db
--    \i script_creacion_bd.sql
-- =====================================================================================

BEGIN;

-- -------------------------------------------------------------------------------------
-- 1. Catálogo de nacionalidades
-- -------------------------------------------------------------------------------------
CREATE TABLE cat_nacionalidad (
    id      BIGSERIAL    PRIMARY KEY,
    clave   VARCHAR(10)  NOT NULL UNIQUE,
    nombre  VARCHAR(100) NOT NULL UNIQUE,
    activo  BOOLEAN      NOT NULL DEFAULT TRUE
);

COMMENT ON TABLE  cat_nacionalidad        IS 'Catálogo de nacionalidades válidas para el registro de clientes';
COMMENT ON COLUMN cat_nacionalidad.nombre IS 'Valor exacto que debe enviar el API (p.ej. MEXICANA)';
COMMENT ON COLUMN cat_nacionalidad.activo IS 'Solo las nacionalidades activas se aceptan en nuevos registros';

INSERT INTO cat_nacionalidad (clave, nombre) VALUES
    ('MEX', 'MEXICANA'),
    ('USA', 'ESTADOUNIDENSE'),
    ('COL', 'COLOMBIANA'),
    ('ARG', 'ARGENTINA'),
    ('ESP', 'ESPAÑOLA'),
    ('CAN', 'CANADIENSE'),
    ('CUB', 'CUBANA'),
    ('VEN', 'VENEZOLANA'),
    ('PER', 'PERUANA'),
    ('GTM', 'GUATEMALTECA'),
    ('EXT', 'EXTRANJERA');

-- -------------------------------------------------------------------------------------
-- 2. Domicilios
-- -------------------------------------------------------------------------------------
CREATE TABLE tb_domicilio (
    id               BIGSERIAL    PRIMARY KEY,
    calle            VARCHAR(100) NOT NULL,
    numero_exterior  VARCHAR(20)  NOT NULL,
    numero_interior  VARCHAR(20),
    colonia          VARCHAR(100) NOT NULL,
    municipio        VARCHAR(100) NOT NULL,
    estado           VARCHAR(100) NOT NULL,
    codigo_postal    VARCHAR(5)   NOT NULL,
    pais             VARCHAR(100) NOT NULL DEFAULT 'México',
    CONSTRAINT chk_domicilio_cp CHECK (codigo_postal ~ '^[0-9]{5}$')
);

COMMENT ON TABLE tb_domicilio IS 'Dirección del cliente (relación 1:1 con tb_cliente)';

-- -------------------------------------------------------------------------------------
-- 3. Clientes (datos personales, contacto e información laboral)
-- -------------------------------------------------------------------------------------
CREATE TABLE tb_cliente (
    id                 BIGSERIAL     PRIMARY KEY,
    nombre             VARCHAR(50)   NOT NULL,
    segundo_nombre     VARCHAR(50),
    apellido_paterno   VARCHAR(50)   NOT NULL,
    apellido_materno   VARCHAR(50)   NOT NULL,
    fecha_nacimiento   DATE          NOT NULL,
    curp               VARCHAR(18)   NOT NULL,
    rfc                VARCHAR(13)   NOT NULL,
    sexo               VARCHAR(10)   NOT NULL,
    nacionalidad       VARCHAR(50)   NOT NULL,
    estado_civil       VARCHAR(12)   NOT NULL,
    correo             VARCHAR(100)  NOT NULL,
    telefono_movil     VARCHAR(10)   NOT NULL,
    telefono_alt       VARCHAR(10),
    ocupacion          VARCHAR(100)  NOT NULL,
    empresa            VARCHAR(100)  NOT NULL,
    ingreso_mensual    NUMERIC(14,2) NOT NULL,
    datos_biometricos  BIGINT,
    activo             BOOLEAN       NOT NULL DEFAULT TRUE,
    fecha_registro     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    domicilio_id       BIGINT,

    -- Unicidad (cada UNIQUE crea su propio índice B-tree: no se declaran índices adicionales)
    CONSTRAINT uq_cliente_curp       UNIQUE (curp),
    CONSTRAINT uq_cliente_rfc        UNIQUE (rfc),
    CONSTRAINT uq_cliente_correo     UNIQUE (correo),
    CONSTRAINT uq_cliente_domicilio  UNIQUE (domicilio_id),

    -- Llaves foráneas (RESTRICT: la baja es lógica, nunca física)
    CONSTRAINT fk_cliente_domicilio    FOREIGN KEY (domicilio_id) REFERENCES tb_domicilio (id) ON DELETE RESTRICT,
    CONSTRAINT fk_cliente_nacionalidad FOREIGN KEY (nacionalidad) REFERENCES cat_nacionalidad (nombre)
                                       ON UPDATE CASCADE ON DELETE RESTRICT,

    -- Reglas de dominio
    CONSTRAINT chk_cliente_nombre      CHECK (CHAR_LENGTH(nombre) BETWEEN 3 AND 50),
    CONSTRAINT chk_cliente_segundo_nom CHECK (segundo_nombre IS NULL OR CHAR_LENGTH(segundo_nombre) BETWEEN 3 AND 50),
    CONSTRAINT chk_cliente_ap_paterno  CHECK (CHAR_LENGTH(apellido_paterno) BETWEEN 3 AND 50),
    CONSTRAINT chk_cliente_ap_materno  CHECK (CHAR_LENGTH(apellido_materno) BETWEEN 3 AND 50),
    CONSTRAINT chk_cliente_curp        CHECK (curp ~ '^[A-Z]{4}[0-9]{6}[HM][A-Z]{5}[A-Z0-9][0-9]$'),
    CONSTRAINT chk_cliente_rfc         CHECK (rfc ~ '^[A-ZÑ&]{3,4}[0-9]{6}[A-Z0-9]{3}$'),
    CONSTRAINT chk_cliente_correo      CHECK (correo = LOWER(correo) AND correo ~ '^[^@[:space:]]+@[^@[:space:]]+$'),
    CONSTRAINT chk_cliente_tel_movil   CHECK (telefono_movil ~ '^[0-9]{10}$'),
    CONSTRAINT chk_cliente_tel_alt     CHECK (telefono_alt IS NULL OR telefono_alt ~ '^[0-9]{10}$'),
    CONSTRAINT chk_cliente_ingreso     CHECK (ingreso_mensual > 0),
    CONSTRAINT chk_cliente_sexo        CHECK (sexo IN ('MASCULINO', 'FEMENINO', 'OTRO')),
    CONSTRAINT chk_cliente_estado_civ  CHECK (estado_civil IN ('SOLTERO', 'CASADO', 'DIVORCIADO', 'VIUDO', 'UNION LIBRE')),
    CONSTRAINT chk_cliente_fecha_nac   CHECK (fecha_nacimiento >= DATE '1900-01-01')
);

COMMENT ON TABLE  tb_cliente                IS 'Clientes personas físicas';
COMMENT ON COLUMN tb_cliente.activo         IS 'Baja lógica: FALSE = cliente dado de baja (nunca se borra físicamente)';
COMMENT ON COLUMN tb_cliente.correo         IS 'Siempre en minúsculas, único';
COMMENT ON COLUMN tb_cliente.ingreso_mensual IS 'Hasta 12 enteros y 2 decimales; mayor a cero';

-- -------------------------------------------------------------------------------------
-- 4. Cuentas bancarias
-- -------------------------------------------------------------------------------------
CREATE TABLE tb_cuenta (
    id              BIGSERIAL     PRIMARY KEY,
    numero_cuenta   VARCHAR(12)   NOT NULL,
    saldo           NUMERIC(15,2) NOT NULL DEFAULT 1000.00,
    estatus         VARCHAR(10)   NOT NULL DEFAULT 'ACTIVA',
    fecha_apertura  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    cliente_id      BIGINT        NOT NULL,

    CONSTRAINT uq_cuenta_numero   UNIQUE (numero_cuenta),
    CONSTRAINT fk_cuenta_cliente  FOREIGN KEY (cliente_id) REFERENCES tb_cliente (id) ON DELETE RESTRICT,
    CONSTRAINT chk_cuenta_numero  CHECK (numero_cuenta ~ '^[0-9]{12}$'),
    CONSTRAINT chk_cuenta_saldo   CHECK (saldo >= 0),
    CONSTRAINT chk_cuenta_estatus CHECK (estatus IN ('ACTIVA', 'INACTIVA'))
);

-- PostgreSQL no indexa las FK automáticamente; se consulta por cliente_id en cada actualización y baja
CREATE INDEX idx_cuenta_cliente ON tb_cuenta (cliente_id);

COMMENT ON TABLE  tb_cuenta       IS 'Cuenta bancaria creada automáticamente al registrar al cliente';
COMMENT ON COLUMN tb_cuenta.saldo IS 'Saldo inicial definido por el sistema (1000.00); nunca negativo';

-- -------------------------------------------------------------------------------------
-- 5. Credenciales de acceso
-- -------------------------------------------------------------------------------------
CREATE TABLE tb_usuario_login (
    id                  BIGSERIAL    PRIMARY KEY,
    username            VARCHAR(50)  NOT NULL,
    password_hash       VARCHAR(255),
    face_id_biometrico  BIGINT,
    is_logged_in        BOOLEAN      NOT NULL DEFAULT FALSE,
    ultima_actividad    TIMESTAMP,
    intentos_fallidos   SMALLINT     NOT NULL DEFAULT 0,
    bloqueado_hasta     TIMESTAMP,
    cliente_id          BIGINT,

    CONSTRAINT uq_usuario_username  UNIQUE (username),
    CONSTRAINT uq_usuario_cliente   UNIQUE (cliente_id),
    CONSTRAINT fk_usuario_cliente   FOREIGN KEY (cliente_id) REFERENCES tb_cliente (id) ON DELETE RESTRICT,
    CONSTRAINT chk_usuario_intentos CHECK (intentos_fallidos >= 0)
);

COMMENT ON COLUMN tb_usuario_login.password_hash   IS 'PBKDF2-HMAC-SHA256 con sal aleatoria: pbkdf2$iteraciones$sal$hash';
COMMENT ON COLUMN tb_usuario_login.bloqueado_hasta IS 'Bloqueo temporal tras 5 intentos fallidos de login';

-- -------------------------------------------------------------------------------------
-- 6. Reglas de negocio garantizadas por la base de datos
-- -------------------------------------------------------------------------------------

-- 6.1 El cliente debe ser mayor de edad
CREATE OR REPLACE FUNCTION fn_cliente_mayor_edad() RETURNS TRIGGER AS $$
BEGIN
    IF NEW.fecha_nacimiento > (CURRENT_DATE - INTERVAL '18 years')::DATE THEN
        RAISE EXCEPTION 'El cliente debe ser mayor de edad (fecha_nacimiento=%)', NEW.fecha_nacimiento
            USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_cliente_mayor_edad
    BEFORE INSERT OR UPDATE OF fecha_nacimiento ON tb_cliente
    FOR EACH ROW EXECUTE FUNCTION fn_cliente_mayor_edad();

-- 6.2 Solo los clientes activos pueden tener cuentas activas
CREATE OR REPLACE FUNCTION fn_cuenta_requiere_cliente_activo() RETURNS TRIGGER AS $$
BEGIN
    IF NEW.estatus = 'ACTIVA'
       AND NOT EXISTS (SELECT 1 FROM tb_cliente WHERE id = NEW.cliente_id AND activo) THEN
        RAISE EXCEPTION 'Solo los clientes activos pueden tener cuentas activas (cliente_id=%)', NEW.cliente_id
            USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_cuenta_cliente_activo
    BEFORE INSERT OR UPDATE OF estatus, cliente_id ON tb_cuenta
    FOR EACH ROW EXECUTE FUNCTION fn_cuenta_requiere_cliente_activo();

-- 6.3 Al dar de baja a un cliente se inactivan sus cuentas
CREATE OR REPLACE FUNCTION fn_cliente_baja_inactiva_cuentas() RETURNS TRIGGER AS $$
BEGIN
    IF OLD.activo AND NOT NEW.activo THEN
        UPDATE tb_cuenta SET estatus = 'INACTIVA' WHERE cliente_id = NEW.id AND estatus = 'ACTIVA';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_cliente_baja
    AFTER UPDATE OF activo ON tb_cliente
    FOR EACH ROW EXECUTE FUNCTION fn_cliente_baja_inactiva_cuentas();

COMMIT;
