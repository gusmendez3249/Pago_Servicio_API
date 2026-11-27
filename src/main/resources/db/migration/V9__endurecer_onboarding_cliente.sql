-- =====================================================================================
-- V9: Endurecimiento del esquema de Onboarding (tb_cliente, tb_domicilio, tb_cuenta,
--     tb_usuario_login): índices, tipos, restricciones y reglas de negocio en BD.
--
-- Las restricciones CHECK / FK nuevas se crean NOT VALID: PostgreSQL las aplica a toda
-- fila que se inserte o actualice desde ahora, sin fallar la migración por datos
-- históricos capturados con las validaciones anteriores. Para auditar los históricos:
--   ALTER TABLE <tabla> VALIDATE CONSTRAINT <nombre>;
-- =====================================================================================

-- 1. Índices redundantes ---------------------------------------------------------------
-- Una restricción UNIQUE ya crea su propio índice B-tree; el índice adicional sobre la
-- misma columna duplica espacio en disco/memoria (shared_buffers) y el costo de cada INSERT.
DROP INDEX IF EXISTS idx_cliente_curp;
DROP INDEX IF EXISTS idx_cliente_rfc;
DROP INDEX IF EXISTS idx_cliente_correo;
DROP INDEX IF EXISTS idx_cuenta_numero;
DROP INDEX IF EXISTS idx_usuario_username;
-- Ninguna consulta filtra por face_id (el login siempre busca por username)
DROP INDEX IF EXISTS idx_usuario_face_id;

-- 2. Índice faltante en llave foránea ----------------------------------------------------
-- PostgreSQL NO indexa automáticamente las FK; tb_cuenta se consulta por cliente_id en
-- cada actualización y baja lógica (y lo revisa el motor al validar la FK).
CREATE INDEX IF NOT EXISTS idx_cuenta_cliente ON tb_cuenta (cliente_id);

-- 3. Normalización de datos existentes y tamaños de columna ajustados al dominio real -----------
-- (las normalizaciones van ANTES de crear las restricciones: un UPDATE también se valida
--  contra los CHECK aunque sean NOT VALID)
-- Catálogos estrictos: un solo valor válido por opción. Los registros capturados con variantes
-- (minúsculas, femenino, guion bajo) se llevan al valor único del catálogo.
UPDATE tb_cliente SET sexo = UPPER(TRIM(sexo)) WHERE sexo <> UPPER(TRIM(sexo));

UPDATE tb_cliente
   SET estado_civil = CASE REPLACE(UPPER(TRIM(estado_civil)), '_', ' ')
                          WHEN 'SOLTERA'    THEN 'SOLTERO'
                          WHEN 'CASADA'     THEN 'CASADO'
                          WHEN 'DIVORCIADA' THEN 'DIVORCIADO'
                          WHEN 'VIUDA'      THEN 'VIUDO'
                          ELSE REPLACE(UPPER(TRIM(estado_civil)), '_', ' ')
                      END
 WHERE estado_civil NOT IN ('SOLTERO', 'CASADO', 'DIVORCIADO', 'VIUDO', 'UNION LIBRE');

-- Filas guardadas con la clave ('MEX') o con otra capitalización ('Mexicana') pasan al nombre exacto ('MEXICANA')
UPDATE tb_cliente c
   SET nacionalidad = n.nombre
  FROM cat_nacionalidad n
 WHERE c.nacionalidad <> n.nombre
   AND (UPPER(TRIM(c.nacionalidad)) = n.clave OR UPPER(TRIM(c.nacionalidad)) = n.nombre);

-- (los tamaños solo se reducen si los datos existentes caben, para no romper la migración)

DO $$
BEGIN
    IF (SELECT COALESCE(MAX(LENGTH(sexo)), 0) FROM tb_cliente) <= 10 THEN
        ALTER TABLE tb_cliente ALTER COLUMN sexo TYPE VARCHAR(10);
    END IF;
    IF (SELECT COALESCE(MAX(LENGTH(estado_civil)), 0) FROM tb_cliente) <= 12 THEN
        ALTER TABLE tb_cliente ALTER COLUMN estado_civil TYPE VARCHAR(12);
    END IF;
    IF (SELECT COALESCE(MAX(ABS(ingreso_mensual)), 0) FROM tb_cliente) < 1000000000000 THEN
        -- 12 enteros + 2 decimales, igual que @Digits(integer = 12, fraction = 2) del DTO
        ALTER TABLE tb_cliente ALTER COLUMN ingreso_mensual TYPE NUMERIC(14, 2);
    END IF;
    IF (SELECT COALESCE(MAX(LENGTH(estatus)), 0) FROM tb_cuenta) <= 10 THEN
        ALTER TABLE tb_cuenta ALTER COLUMN estatus TYPE VARCHAR(10);
    END IF;
END $$;

-- 4. Restricciones de dominio (reflejan las validaciones del API) ------------------------------
ALTER TABLE tb_cliente
    ADD CONSTRAINT chk_cliente_nombre      CHECK (CHAR_LENGTH(nombre) BETWEEN 3 AND 50) NOT VALID,
    ADD CONSTRAINT chk_cliente_segundo_nom CHECK (segundo_nombre IS NULL OR CHAR_LENGTH(segundo_nombre) BETWEEN 3 AND 50) NOT VALID,
    ADD CONSTRAINT chk_cliente_ap_paterno  CHECK (CHAR_LENGTH(apellido_paterno) BETWEEN 3 AND 50) NOT VALID,
    ADD CONSTRAINT chk_cliente_ap_materno  CHECK (CHAR_LENGTH(apellido_materno) BETWEEN 3 AND 50) NOT VALID,
    ADD CONSTRAINT chk_cliente_curp        CHECK (curp ~ '^[A-Z]{4}[0-9]{6}[HM][A-Z]{5}[A-Z0-9][0-9]$') NOT VALID,
    ADD CONSTRAINT chk_cliente_rfc         CHECK (rfc ~ '^[A-ZÑ&]{3,4}[0-9]{6}[A-Z0-9]{3}$') NOT VALID,
    -- Formato completo lo valida el API; la BD garantiza minúsculas (unicidad sin importar mayúsculas)
    ADD CONSTRAINT chk_cliente_correo      CHECK (correo = LOWER(correo) AND correo ~ '^[^@[:space:]]+@[^@[:space:]]+$') NOT VALID,
    ADD CONSTRAINT chk_cliente_tel_movil   CHECK (telefono_movil ~ '^[0-9]{10}$') NOT VALID,
    ADD CONSTRAINT chk_cliente_tel_alt     CHECK (telefono_alt IS NULL OR telefono_alt ~ '^[0-9]{10}$') NOT VALID,
    ADD CONSTRAINT chk_cliente_ingreso     CHECK (ingreso_mensual > 0) NOT VALID,
    ADD CONSTRAINT chk_cliente_sexo        CHECK (sexo IN ('MASCULINO', 'FEMENINO', 'OTRO')) NOT VALID,
    ADD CONSTRAINT chk_cliente_estado_civ  CHECK (estado_civil IN ('SOLTERO', 'CASADO', 'DIVORCIADO', 'VIUDO', 'UNION LIBRE')) NOT VALID,
    ADD CONSTRAINT chk_cliente_fecha_nac   CHECK (fecha_nacimiento >= DATE '1900-01-01') NOT VALID;

ALTER TABLE tb_domicilio
    ADD CONSTRAINT chk_domicilio_cp CHECK (codigo_postal ~ '^[0-9]{5}$') NOT VALID;

-- El generador actual produce siempre 12 dígitos; se admiten 13 solo porque la versión anterior
-- generaba algunos de 13 y esas cuentas deben poder seguir actualizándose (p.ej. en una baja).
ALTER TABLE tb_cuenta
    ADD CONSTRAINT chk_cuenta_numero  CHECK (numero_cuenta ~ '^[0-9]{12,13}$') NOT VALID,
    ADD CONSTRAINT chk_cuenta_estatus CHECK (estatus IN ('ACTIVA', 'INACTIVA')) NOT VALID;

-- 5. Nacionalidad ligada al catálogo (las filas ya se normalizaron en el paso 3) ----------------
ALTER TABLE tb_cliente
    ADD CONSTRAINT fk_cliente_nacionalidad FOREIGN KEY (nacionalidad)
        REFERENCES cat_nacionalidad (nombre) ON UPDATE CASCADE ON DELETE RESTRICT NOT VALID;

-- 6. Baja lógica: impedir borrados físicos en cascada ------------------------------------------
-- Con ON DELETE CASCADE, borrar un domicilio eliminaba al cliente, sus cuentas y su login.
-- RESTRICT obliga a usar la baja lógica (activo = FALSE) que exige el negocio.
ALTER TABLE tb_cliente
    DROP CONSTRAINT IF EXISTS tb_cliente_domicilio_id_fkey,
    ADD CONSTRAINT tb_cliente_domicilio_id_fkey FOREIGN KEY (domicilio_id)
        REFERENCES tb_domicilio (id) ON DELETE RESTRICT;

ALTER TABLE tb_cuenta
    DROP CONSTRAINT IF EXISTS tb_cuenta_cliente_id_fkey,
    ADD CONSTRAINT tb_cuenta_cliente_id_fkey FOREIGN KEY (cliente_id)
        REFERENCES tb_cliente (id) ON DELETE RESTRICT;

ALTER TABLE tb_usuario_login
    DROP CONSTRAINT IF EXISTS tb_usuario_login_cliente_id_fkey,
    ADD CONSTRAINT tb_usuario_login_cliente_id_fkey FOREIGN KEY (cliente_id)
        REFERENCES tb_cliente (id) ON DELETE RESTRICT;

-- 7. Bloqueo por intentos fallidos de login ---------------------------------------------------
ALTER TABLE tb_usuario_login
    ADD COLUMN IF NOT EXISTS intentos_fallidos SMALLINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS bloqueado_hasta   TIMESTAMP;

ALTER TABLE tb_usuario_login
    ADD CONSTRAINT chk_usuario_intentos CHECK (intentos_fallidos >= 0);

-- 8. Reglas de negocio garantizadas por la BD --------------------------------------------------
-- 8.1 El cliente debe ser mayor de edad (CURRENT_DATE no es inmutable, por eso trigger y no CHECK)
CREATE OR REPLACE FUNCTION fn_cliente_mayor_edad() RETURNS TRIGGER AS $$
BEGIN
    IF NEW.fecha_nacimiento > (CURRENT_DATE - INTERVAL '18 years')::DATE THEN
        RAISE EXCEPTION 'El cliente debe ser mayor de edad (fecha_nacimiento=%)', NEW.fecha_nacimiento
            USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_cliente_mayor_edad ON tb_cliente;
CREATE TRIGGER trg_cliente_mayor_edad
    BEFORE INSERT OR UPDATE OF fecha_nacimiento ON tb_cliente
    FOR EACH ROW EXECUTE FUNCTION fn_cliente_mayor_edad();

-- 8.2 Solo los clientes activos pueden tener cuentas activas
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

DROP TRIGGER IF EXISTS trg_cuenta_cliente_activo ON tb_cuenta;
CREATE TRIGGER trg_cuenta_cliente_activo
    BEFORE INSERT OR UPDATE OF estatus, cliente_id ON tb_cuenta
    FOR EACH ROW EXECUTE FUNCTION fn_cuenta_requiere_cliente_activo();

-- 8.3 Al dar de baja a un cliente se inactivan sus cuentas (aunque la baja no venga del API)
CREATE OR REPLACE FUNCTION fn_cliente_baja_inactiva_cuentas() RETURNS TRIGGER AS $$
BEGIN
    IF OLD.activo AND NOT NEW.activo THEN
        UPDATE tb_cuenta SET estatus = 'INACTIVA' WHERE cliente_id = NEW.id AND estatus = 'ACTIVA';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_cliente_baja ON tb_cliente;
CREATE TRIGGER trg_cliente_baja
    AFTER UPDATE OF activo ON tb_cliente
    FOR EACH ROW EXECUTE FUNCTION fn_cliente_baja_inactiva_cuentas();
