CREATE TABLE usuarios (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nombre VARCHAR(80) NOT NULL,
    apellido VARCHAR(80) NOT NULL,
    dni VARCHAR(12) NOT NULL,
    fecha_nacimiento DATE NOT NULL,
    direccion VARCHAR(180) NOT NULL,
    email VARCHAR(254) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    google_issuer VARCHAR(255),
    google_subject VARCHAR(255),
    CONSTRAINT uk_usuario_email UNIQUE (email),
    CONSTRAINT uk_usuario_dni UNIQUE (dni),
    CONSTRAINT uk_usuario_google UNIQUE (google_issuer, google_subject)
);
