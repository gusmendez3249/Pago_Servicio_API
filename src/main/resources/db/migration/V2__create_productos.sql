CREATE TABLE IF NOT EXISTS productos (
    id SERIAL PRIMARY KEY,
    tipo_front INTEGER NOT NULL,
    servicio VARCHAR(255),
    producto VARCHAR(255),
    id_servicio INTEGER,
    id_producto INTEGER,
    id_cat_tipo_servicio INTEGER,
    has_digito_verificador BOOLEAN,
    precio NUMERIC(15, 2),
    show_ayuda BOOLEAN,
    tipo_referencia VARCHAR(20),
    legend TEXT
);