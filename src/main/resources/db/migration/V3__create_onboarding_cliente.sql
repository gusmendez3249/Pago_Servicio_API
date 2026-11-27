-- Tablas para el módulo de Onboarding de Clientes (Personas Físicas)

-- 1. Tabla Domicilio
CREATE TABLE IF NOT EXISTS tb_domicilio (
    id BIGSERIAL PRIMARY KEY,
    calle VARCHAR(100) NOT NULL,
    numero_exterior VARCHAR(20) NOT NULL,
    numero_interior VARCHAR(20),
    colonia VARCHAR(100) NOT NULL,
    municipio VARCHAR(100) NOT NULL,
    estado VARCHAR(100) NOT NULL,
    codigo_postal VARCHAR(5) NOT NULL,
    pais VARCHAR(100) NOT NULL DEFAULT 'México'
);

-- 2. Tabla Cliente
CREATE TABLE IF NOT EXISTS tb_cliente (
    id BIGSERIAL PRIMARY KEY,
    nombre VARCHAR(50) NOT NULL,
    segundo_nombre VARCHAR(50),
    apellido_paterno VARCHAR(50) NOT NULL,
    apellido_materno VARCHAR(50) NOT NULL,
    fecha_nacimiento DATE NOT NULL,
    curp VARCHAR(18) NOT NULL UNIQUE,
    rfc VARCHAR(13) NOT NULL UNIQUE,
    sexo VARCHAR(30) NOT NULL,
    nacionalidad VARCHAR(50) NOT NULL,
    estado_civil VARCHAR(50) NOT NULL,
    correo VARCHAR(100) NOT NULL UNIQUE,
    telefono_movil VARCHAR(10) NOT NULL,
    telefono_alt VARCHAR(10),
    ocupacion VARCHAR(100) NOT NULL,
    empresa VARCHAR(100) NOT NULL,
    ingreso_mensual NUMERIC(15, 2) NOT NULL,
    datos_biometricos BIGINT,
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    fecha_registro TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    domicilio_id BIGINT UNIQUE REFERENCES tb_domicilio(id) ON DELETE CASCADE
);

-- 3. Tabla Cuenta
CREATE TABLE IF NOT EXISTS tb_cuenta (
    id BIGSERIAL PRIMARY KEY,
    numero_cuenta VARCHAR(20) NOT NULL UNIQUE,
    saldo NUMERIC(15, 2) NOT NULL DEFAULT 1000.00 CHECK (saldo >= 0),
    estatus VARCHAR(20) NOT NULL DEFAULT 'ACTIVA',
    fecha_apertura TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    cliente_id BIGINT NOT NULL REFERENCES tb_cliente(id) ON DELETE CASCADE
);

-- 4. Tabla Usuario Login (Seguridad Cifrada)
CREATE TABLE IF NOT EXISTS tb_usuario_login (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password_hash VARCHAR(255),
    face_id_biometrico BIGINT,
    is_logged_in BOOLEAN NOT NULL DEFAULT FALSE,
    ultima_actividad TIMESTAMP,
    cliente_id BIGINT UNIQUE REFERENCES tb_cliente(id) ON DELETE CASCADE
);

-- Índices de optimización para búsquedas rápidas
CREATE INDEX IF NOT EXISTS idx_cliente_curp ON tb_cliente(curp);
CREATE INDEX IF NOT EXISTS idx_cliente_rfc ON tb_cliente(rfc);
CREATE INDEX IF NOT EXISTS idx_cliente_correo ON tb_cliente(correo);
CREATE INDEX IF NOT EXISTS idx_cuenta_numero ON tb_cuenta(numero_cuenta);
CREATE INDEX IF NOT EXISTS idx_usuario_username ON tb_usuario_login(username);
CREATE INDEX IF NOT EXISTS idx_usuario_face_id ON tb_usuario_login(face_id_biometrico);
