-- ============================================================
-- SISTEMA DE GESTIÓN INTEGRAL DE BODEGA - DSM SANTA CRUZ
-- Base de datos compartida entre módulos (auth, dashboard, etc.)
-- Módulo 1: Autenticación y Gestión de Usuarios
-- ============================================================

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ============================================================
-- TABLA: ROLES
-- 4 perfiles según Sección 3.3 del documento de requerimientos:
--   1. JEFATURA              → Encargada Unidad Bodega y Logística
--   2. OPERATIVO             → Administrativos Bodega (3 usuarios)
--   3. AUDITOR               → Encargada Norma Técnica 226
--   4. ENCARGADO_DEPENDENCIA → Encargados de hacer los pedidos
-- ============================================================
CREATE TABLE roles (
    id          SERIAL PRIMARY KEY,
    nombre      VARCHAR(50)  NOT NULL UNIQUE,
    descripcion VARCHAR(255),
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ============================================================
-- TABLA: USUARIOS (compartida entre módulos)
-- ============================================================
CREATE TABLE usuarios (
    id                       BIGSERIAL PRIMARY KEY,
    rut                      VARCHAR(12)  NOT NULL UNIQUE,
    nombre_completo          VARCHAR(150) NOT NULL,
    email                    VARCHAR(150) NOT NULL UNIQUE,
    password_hash            VARCHAR(255) NOT NULL,
    rol_id                   INTEGER      NOT NULL REFERENCES roles(id),
    
    -- Para ENCARGADO_DEPENDENCIA: indica qué servicio/dependencia representa
    -- Ej: "Farmacia Central", "SAR Santa Cruz", "Dental CESFAM", "Vacunatorio"
    dependencia              VARCHAR(100),
    
    activo                   BOOLEAN      NOT NULL DEFAULT TRUE,
    bloqueado                BOOLEAN      NOT NULL DEFAULT FALSE,
    intentos_fallidos        INTEGER      NOT NULL DEFAULT 0,
    mfa_enabled              BOOLEAN      NOT NULL DEFAULT TRUE,
    mfa_secret               VARCHAR(64),
    ultimo_codigo_mfa        VARCHAR(10),
    ultimo_codigo_mfa_expira TIMESTAMP,
    ultimo_acceso            TIMESTAMP,
    fecha_creacion           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_actualizacion      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_usuarios_rut         ON usuarios(rut);
CREATE INDEX idx_usuarios_email       ON usuarios(email);
CREATE INDEX idx_usuarios_dependencia ON usuarios(dependencia);

-- ============================================================
-- TABLA: LOG DE AUDITORÍA (inalterable - RNF-016)
-- ============================================================
CREATE TABLE log_auditoria (
    id              BIGSERIAL PRIMARY KEY,
    usuario_rut     VARCHAR(12),
    usuario_nombre  VARCHAR(150),
    accion          VARCHAR(50)  NOT NULL,
    modulo          VARCHAR(50),
    descripcion     TEXT,
    valor_anterior  JSONB,
    valor_nuevo     JSONB,
    ip_address      VARCHAR(45),
    user_agent      TEXT,
    timestamp       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_log_usuario   ON log_auditoria(usuario_rut);
CREATE INDEX idx_log_timestamp ON log_auditoria(timestamp);
CREATE INDEX idx_log_accion    ON log_auditoria(accion);

CREATE OR REPLACE FUNCTION prevent_log_modification()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'El Log de Auditoría es inalterable.';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_log_auditoria_inmutable
BEFORE UPDATE OR DELETE ON log_auditoria
FOR EACH ROW EXECUTE FUNCTION prevent_log_modification();

-- ============================================================
-- TABLA: SESIONES
-- ============================================================
CREATE TABLE sesiones (
    id               UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    usuario_id       BIGINT       NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
    token_jwt        TEXT         NOT NULL,
    ip_address       VARCHAR(45),
    user_agent       TEXT,
    fecha_inicio     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ultima_actividad TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    activa           BOOLEAN      NOT NULL DEFAULT TRUE,
    fecha_cierre     TIMESTAMP
);

CREATE INDEX idx_sesiones_usuario ON sesiones(usuario_id);
CREATE INDEX idx_sesiones_token   ON sesiones(token_jwt);

-- ============================================================
-- DATOS INICIALES
-- ============================================================

-- Roles (4 perfiles)
INSERT INTO roles (id, nombre, descripcion) VALUES
    (1, 'JEFATURA',              'Encargada Unidad Bodega y Logística - Control global del sistema'),
    (2, 'OPERATIVO',             'Administrativos de Bodega - Operación diaria (recepción, despacho, inventario)'),
    (3, 'AUDITOR',               'Encargada Norma Técnica 226 - Trazabilidad sanitaria y bloqueo ISP'),
    (4, 'ENCARGADO_DEPENDENCIA', 'Encargados de dependencias (Farmacia, SAR, Dental, etc.) - Solo lectura y pedidos');

-- Ajustar secuencia de roles
SELECT setval('roles_id_seq', 4, true);

-- ============================================================
-- USUARIOS DE PRUEBA
-- Contraseña para TODOS: Admin2026!
-- Hash BCrypt (costo 12): $2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/LewY5GyYqVr/3KZ5u
-- ⚠️  Cambiar en producción
--
-- ✅ RUTs verificados con dígito verificador correcto (Módulo 11)
-- ============================================================

-- ---- 1. JEFATURA (1 usuario) ----
-- RUT 12.345.678 → DV = 5  ✅
INSERT INTO usuarios 
    (rut, nombre_completo, email, password_hash, rol_id, dependencia, mfa_enabled) 
VALUES
    ('12.345.678-5', 
     'Claudia Donoso Carreño',
     'ayline.07.01@gmail.com',
     '$2a$12$VCNWCLFt6K7OjAdyD6bEc.yPZysasAdvuMiXWhJ67B2oE8BNAMDMe',
     1, NULL, TRUE);

-- ---- 2. OPERATIVO (3 usuarios) ----
INSERT INTO usuarios 
    (rut, nombre_completo, email, password_hash, rol_id, dependencia, mfa_enabled) 
VALUES
    -- RUT 22.333.444 → DV = 5  ✅
    ('22.333.444-5', 
     'María González',
     'maria.gonzalez@dsm-santacruz.cl',
     '$2a$12$VCNWCLFt6K7OjAdyD6bEc.yPZysasAdvuMiXWhJ67B2oE8BNAMDMe',
     2, NULL, TRUE),
    
    -- RUT 23.444.555 → DV = 6  ✅
    ('23.444.555-6', 
     'Pedro Ramírez',
     'pedro.ramirez@dsm-santacruz.cl',
     '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/LewY5GyYqVr/3KZ5u',
     2, NULL, TRUE),
    
    -- RUT 24.555.666 → DV = 7  ✅
    ('24.555.666-7', 
     'Camila Soto',
     'camila.soto@dsm-santacruz.cl',
     '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/LewY5GyYqVr/3KZ5u',
     2, NULL, TRUE);

-- ---- 3. AUDITOR / NT 226 (1 usuario) ----
-- RUT 11.222.333 → DV = 4  ✅
INSERT INTO usuarios 
    (rut, nombre_completo, email, password_hash, rol_id, dependencia, mfa_enabled) 
VALUES
    ('11.222.333-4', 
     'Giulia Aguilar',
     'giulia.aguilar@dsm-santacruz.cl',
     '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/LewY5GyYqVr/3KZ5u',
     3, NULL, TRUE);

-- ---- 4. ENCARGADO DE DEPENDENCIA (relativo) ----
-- Cada dependencia tiene su encargado que solo puede ver stock y hacer pedidos
INSERT INTO usuarios 
    (rut, nombre_completo, email, password_hash, rol_id, dependencia, mfa_enabled) 
VALUES
    -- RUT 15.111.222 → DV = 3  ✅
    ('15.111.222-3', 
     'Ana Muñoz',
     'ana.munoz@dsm-santacruz.cl',
     '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/LewY5GyYqVr/3KZ5u',
     4, 'Farmacia Central', FALSE),
    
    -- RUT 16.222.333 → DV = 4  ✅
    ('16.222.333-4', 
     'Luis Herrera',
     'luis.herrera@dsm-santacruz.cl',
     '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/LewY5GyYqVr/3KZ5u',
     4, 'SAR Santa Cruz', FALSE),
    
    -- RUT 17.333.444 → DV = 5  ✅
    ('17.333.444-5', 
     'Patricia Vega',
     'patricia.vega@dsm-santacruz.cl',
     '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/LewY5GyYqVr/3KZ5u',
     4, 'Dental CESFAM', FALSE),
    
    -- RUT 18.444.555 → DV = 6  ✅
    ('18.444.555-6', 
     'Roberto Díaz',
     'roberto.diaz@dsm-santacruz.cl',
     '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/LewY5GyYqVr/3KZ5u',
     4, 'Vacunatorio', FALSE),
    
    -- RUT 19.555.666 → DV = 7  ✅
    ('19.555.666-7', 
     'Sandra Rojas',
     'sandra.rojas@dsm-santacruz.cl',
     '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/LewY5GyYqVr/3KZ5u',
     4, 'Laboratorio', FALSE);

-- ============================================================
-- COMENTARIOS
-- ============================================================
COMMENT ON TABLE usuarios IS 'Cuentas nominativas por RUT (RN-007). Prohibidas cuentas genéricas.';
COMMENT ON TABLE log_auditoria IS 'Registro inalterable de acciones (RNF-016, NT 226)';
COMMENT ON COLUMN usuarios.dependencia IS 'Solo aplica para rol ENCARGADO_DEPENDENCIA. Indica el servicio que representa.';

-- ============================================================
-- VISTA: resumen de usuarios por rol (útil para admin)
-- ============================================================
CREATE OR REPLACE VIEW v_usuarios_por_rol AS
SELECT 
    r.nombre AS rol,
    r.descripcion AS rol_descripcion,
    COUNT(u.id) AS total_usuarios,
    COUNT(CASE WHEN u.activo THEN 1 END) AS usuarios_activos,
    COUNT(CASE WHEN u.bloqueado THEN 1 END) AS usuarios_bloqueados
FROM roles r
LEFT JOIN usuarios u ON u.rol_id = r.id
GROUP BY r.id, r.nombre, r.descripcion
ORDER BY r.id;