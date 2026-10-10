-- Tabla catálogo de Nacionalidades
CREATE TABLE IF NOT EXISTS cat_nacionalidad (
    id BIGSERIAL PRIMARY KEY,
    clave VARCHAR(10) NOT NULL UNIQUE,
    nombre VARCHAR(100) NOT NULL UNIQUE,
    activo BOOLEAN NOT NULL DEFAULT TRUE
);

-- Insertar valores iniciales de catálogo
INSERT INTO cat_nacionalidad (clave, nombre, activo) VALUES
('MEX', 'MEXICANA', TRUE),
('USA', 'ESTADOUNIDENSE', TRUE),
('COL', 'COLOMBIANA', TRUE),
('ARG', 'ARGENTINA', TRUE),
('ESP', 'ESPAÑOLA', TRUE),
('CAN', 'CANADIENSE', TRUE),
('CUB', 'CUBANA', TRUE),
('VEN', 'VENEZOLANA', TRUE),
('PER', 'PERUANA', TRUE),
('GTM', 'GUATEMALTECA', TRUE),
('EXT', 'EXTRANJERA', TRUE)
ON CONFLICT (nombre) DO NOTHING;
