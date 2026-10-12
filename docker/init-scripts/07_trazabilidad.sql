-- ============================================================
-- MÓDULO 7: TRAZABILIDAD SANITARIA (NT 226 y NT 247)
-- Sistema de Gestión Integral de Bodega - DSM Santa Cruz
-- Cubre: RF-067 a RF-074, RN-013
--
-- Flujo:
--   1. ISP emite una alerta sobre un lote específico
--   2. La Encargada NT 226 bloquea el lote en todo el sistema
--   3. Ningún despacho puede usar ese lote
--   4. Al resolverse la alerta → se desbloquea
-- ============================================================

-- ============================================================
-- TABLA: BLOQUEOS_SANITARIOS (bloqueos de lote por alerta ISP)
-- ============================================================
CREATE TABLE bloqueos_sanitarios (
    id                       BIGSERIAL    PRIMARY KEY,
    
    -- Correlativo
    numero_bloqueo           VARCHAR(30)  NOT NULL UNIQUE,   -- ej: "BLQ-2026-0001"
    
    -- Lote afectado
    lote_id                  BIGINT       NOT NULL REFERENCES lotes(id),
    articulo_id              BIGINT       NOT NULL REFERENCES maestro_articulos(id),
    numero_lote              VARCHAR(50)  NOT NULL,          -- snapshot
    nombre_producto          VARCHAR(200) NOT NULL,          -- snapshot
    sku                      VARCHAR(50)  NOT NULL,          -- snapshot
    fecha_vencimiento        DATE,                           -- snapshot
    registro_isp             VARCHAR(50),                    -- snapshot
    
    -- Alerta ISP (RF-069)
    tipo_alerta              VARCHAR(30)  NOT NULL,
    -- ALERTA_ISP | RETIRO_MERCADO | DEFECTO_FABRICACION | CONTAMINACION | OTRO
    numero_oficio_isp        VARCHAR(50),                    -- N° oficio ISP
    fecha_alerta             DATE         NOT NULL,
    descripcion_alerta       TEXT,
    
    -- Justificación del bloqueo (RF-069)
    justificacion            TEXT         NOT NULL,
    
    -- Quién bloqueó
    bloqueado_por_rut        VARCHAR(12)  NOT NULL,
    bloqueado_por_nombre     VARCHAR(150) NOT NULL,
    fecha_bloqueo            TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- Cantidad afectada
    stock_afectado           INTEGER      NOT NULL DEFAULT 0, -- unidades bloqueadas
    bodegas_afectadas        INTEGER      NOT NULL DEFAULT 0, -- cuántas bodegas
    
    -- Estado del bloqueo
    estado                   VARCHAR(20)  NOT NULL DEFAULT 'ACTIVO',
    -- ACTIVO | LEVANTADO | PARCIAL
    
    -- Levantamiento del bloqueo (RF-069)
    levantado_por_rut        VARCHAR(12),
    levantado_por_nombre     VARCHAR(150),
    fecha_levantamiento      TIMESTAMP,
    motivo_levantamiento     TEXT,
    
    -- Control
    activo                   BOOLEAN      NOT NULL DEFAULT TRUE,
    fecha_creacion           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_actualizacion      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT chk_tipo_alerta CHECK (tipo_alerta IN ('ALERTA_ISP', 'RETIRO_MERCADO', 'DEFECTO_FABRICACION', 'CONTAMINACION', 'OTRO')),
    CONSTRAINT chk_estado_bloqueo CHECK (estado IN ('ACTIVO', 'LEVANTADO', 'PARCIAL'))
);

CREATE INDEX idx_bloqueos_numero          ON bloqueos_sanitarios(numero_bloqueo);
CREATE INDEX idx_bloqueos_lote            ON bloqueos_sanitarios(lote_id);
CREATE INDEX idx_bloqueos_articulo        ON bloqueos_sanitarios(articulo_id);
CREATE INDEX idx_bloqueos_estado          ON bloqueos_sanitarios(estado);
CREATE INDEX idx_bloqueos_fecha_alerta    ON bloqueos_sanitarios(fecha_alerta);
CREATE INDEX idx_bloqueos_oficio          ON bloqueos_sanitarios(numero_oficio_isp);

COMMENT ON TABLE bloqueos_sanitarios IS 
    'Bloqueos de lote por alerta ISP. Base del Bloqueo Sanitario (RF-069, RF-070, RN-013)';

-- ============================================================
-- TABLA: BLOQUEO_BODEGAS (detalle por bodega afectada)
-- ============================================================
CREATE TABLE bloqueo_bodegas (
    id                       BIGSERIAL    PRIMARY KEY,
    bloqueo_id               BIGINT       NOT NULL REFERENCES bloqueos_sanitarios(id) ON DELETE CASCADE,
    bodega_id                INTEGER      NOT NULL REFERENCES bodegas(id),
    stock_id                 BIGINT       REFERENCES stock_por_bodega(id),
    stock_lote_id            BIGINT       REFERENCES stock_lotes(id),
    
    -- Cantidad bloqueada en esta bodega
    cantidad_bloqueada       INTEGER      NOT NULL DEFAULT 0,
    
    -- Estado del bloqueo en esta bodega
    estado                   VARCHAR(20)  NOT NULL DEFAULT 'BLOQUEADO',
    -- BLOQUEADO | DESBLOQUEADO
    
    fecha_creacion           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_actualizacion      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT chk_estado_bloqueo_bodega CHECK (estado IN ('BLOQUEADO', 'DESBLOQUEADO'))
);

CREATE INDEX idx_bloqueo_bodegas_bloqueo  ON bloqueo_bodegas(bloqueo_id);
CREATE INDEX idx_bloqueo_bodegas_bodega   ON bloqueo_bodegas(bodega_id);

COMMENT ON TABLE bloqueo_bodegas IS 
    'Detalle de bodegas afectadas por cada bloqueo sanitario';

-- ============================================================
-- TABLA: TRAZABILIDAD_DM (trazabilidad completa NT 226/247)
-- Registra todos los datos exigidos por la normativa
-- ============================================================
CREATE TABLE trazabilidad_dm (
    id                       BIGSERIAL    PRIMARY KEY,
    
    -- Vinculación
    articulo_id              BIGINT       NOT NULL REFERENCES maestro_articulos(id),
    lote_id                  BIGINT       REFERENCES lotes(id),
    stock_id                 BIGINT       REFERENCES stock_por_bodega(id),
    recepcion_detalle_id     BIGINT       REFERENCES recepcion_detalle(id),
    despacho_detalle_id      BIGINT       REFERENCES despacho_detalle(id),
    
    -- Datos NT 226 (RF-067)
    nombre_producto          VARCHAR(200) NOT NULL,
    rut_proveedor            VARCHAR(12),
    nombre_proveedor         VARCHAR(200),
    numero_factura           VARCHAR(50),
    numero_guia              VARCHAR(50),
    modelo                   VARCHAR(100),
    numero_lote              VARCHAR(50),
    numero_serie             VARCHAR(50),
    fecha_vencimiento        DATE,
    
    -- Datos NT 247 (RF-068)
    estado_dm                VARCHAR(50),                    -- NUEVO | USADO | VENCIDO | DAÑADO
    numero_registro_sanitario VARCHAR(50),
    forma_adquisicion        VARCHAR(50),                    -- Cenabast | Mercado Público | etc.
    fecha_recepcion_conforme DATE,
    numero_inventario        VARCHAR(50),
    
    -- Datos de paciente (solo para implantables) - RF-068
    paciente_run             VARCHAR(12),
    paciente_nombre          VARCHAR(150),
    fecha_implantacion       DATE,
    profesional_implantador  VARCHAR(150),
    procedimiento            VARCHAR(255),
    
    -- Estado en el sistema
    estado                   VARCHAR(30)  NOT NULL DEFAULT 'EN_BODEGA',
    -- EN_BODEGA | DESPACHADO | IMPLANTADO | VENCIDO | MERMA | BLOQUEADO
    
    observaciones            TEXT,
    
    -- Control
    activo                   BOOLEAN      NOT NULL DEFAULT TRUE,
    fecha_creacion           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_actualizacion      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    creado_por               VARCHAR(12),
    actualizado_por          VARCHAR(12),
    
    CONSTRAINT chk_estado_dm CHECK (estado IN ('EN_BODEGA', 'DESPACHADO', 'IMPLANTADO', 'VENCIDO', 'MERMA', 'BLOQUEADO'))
);

CREATE INDEX idx_trazabilidad_articulo    ON trazabilidad_dm(articulo_id);
CREATE INDEX idx_trazabilidad_lote        ON trazabilidad_dm(lote_id);
CREATE INDEX idx_trazabilidad_numero_lote ON trazabilidad_dm(numero_lote);
CREATE INDEX idx_trazabilidad_estado      ON trazabilidad_dm(estado);
CREATE INDEX idx_trazabilidad_paciente    ON trazabilidad_dm(paciente_run);
CREATE INDEX idx_trazabilidad_recepcion   ON trazabilidad_dm(recepcion_detalle_id);
CREATE INDEX idx_trazabilidad_despacho    ON trazabilidad_dm(despacho_detalle_id);

COMMENT ON TABLE trazabilidad_dm IS 
    'Trazabilidad completa de dispositivos médicos (RF-067, RF-068, RF-072, RF-074)';

-- ============================================================
-- TRIGGERS
-- ============================================================
CREATE TRIGGER trg_bloqueos_updated_at
BEFORE UPDATE ON bloqueos_sanitarios
FOR EACH ROW EXECUTE FUNCTION trigger_set_timestamp();

CREATE TRIGGER trg_bloqueo_bodegas_updated_at
BEFORE UPDATE ON bloqueo_bodegas
FOR EACH ROW EXECUTE FUNCTION trigger_set_timestamp();

CREATE TRIGGER trg_trazabilidad_updated_at
BEFORE UPDATE ON trazabilidad_dm
FOR EACH ROW EXECUTE FUNCTION trigger_set_timestamp();

-- ============================================================
-- VISTAS
-- ============================================================

-- Vista: bloqueos activos con info completa
CREATE OR REPLACE VIEW v_bloqueos_activos AS
SELECT 
    b.id,
    b.numero_bloqueo,
    b.numero_lote,
    b.sku,
    b.nombre_producto,
    b.fecha_vencimiento,
    b.registro_isp,
    b.tipo_alerta,
    b.numero_oficio_isp,
    b.fecha_alerta,
    b.justificacion,
    b.bloqueado_por_rut,
    b.bloqueado_por_nombre,
    b.fecha_bloqueo,
    b.stock_afectado,
    b.bodegas_afectadas,
    b.estado,
    (SELECT COUNT(*) FROM bloqueo_bodegas bb WHERE bb.bloqueo_id = b.id AND bb.estado = 'BLOQUEADO') AS bodegas_bloqueadas
FROM bloqueos_sanitarios b
WHERE b.estado = 'ACTIVO' AND b.activo = TRUE
ORDER BY b.fecha_bloqueo DESC;

COMMENT ON VIEW v_bloqueos_activos IS 
    'Bloqueos sanitarios activos (RF-069)';

-- Vista: lotes bloqueados actualmente
CREATE OR REPLACE VIEW v_lotes_bloqueados AS
SELECT 
    l.id                  AS lote_id,
    l.numero_lote,
    a.sku,
    a.nombre_tecnico,
    l.fecha_vencimiento,
    l.motivo_bloqueo,
    l.fecha_bloqueo,
    l.bloqueado_por,
    (SELECT COUNT(*) FROM stock_lotes sl WHERE sl.lote_id = l.id AND sl.cantidad > 0) AS bodegas_con_stock,
    (SELECT COALESCE(SUM(sl.cantidad), 0) FROM stock_lotes sl WHERE sl.lote_id = l.id) AS stock_total
FROM lotes l
JOIN maestro_articulos a ON a.id = l.articulo_id
WHERE l.bloqueado = TRUE AND l.activo = TRUE
ORDER BY l.fecha_bloqueo DESC;

COMMENT ON VIEW v_lotes_bloqueados IS 
    'Lotes actualmente bloqueados por alerta ISP (RF-070)';

-- Vista: dispositivos médicos con trazabilidad completa
CREATE OR REPLACE VIEW v_trazabilidad_completa AS
SELECT 
    t.id,
    t.nombre_producto,
    t.numero_lote,
    t.fecha_vencimiento,
    t.rut_proveedor,
    t.nombre_proveedor,
    t.numero_factura,
    t.numero_guia,
    t.registro_isp,
    t.estado_dm,
    t.forma_adquisicion,
    t.fecha_recepcion_conforme,
    t.estado,
    t.paciente_run,
    t.paciente_nombre,
    t.fecha_implantacion,
    t.profesional_implantador,
    t.fecha_creacion,
    t.fecha_actualizacion
FROM trazabilidad_dm t
WHERE t.activo = TRUE
ORDER BY t.fecha_creacion DESC;

COMMENT ON VIEW v_trazabilidad_completa IS 
    'Vista completa de trazabilidad de dispositivos médicos (RF-072)';

-- Vista: simulaciones anuales NT 247 (RF-071)
CREATE OR REPLACE VIEW v_simulacion_trazabilidad AS
SELECT 
    t.id,
    t.nombre_producto,
    t.numero_lote,
    t.numero_serie,
    t.registro_isp,
    t.estado_dm,
    t.fecha_recepcion_conforme,
    t.ubicacion_actual,
    t.stock_actual,
    t.estado
FROM (
    SELECT 
        td.id,
        td.nombre_producto,
        td.numero_lote,
        td.numero_serie,
        td.registro_sanitario AS registro_isp,
        td.estado_dm,
        td.fecha_recepcion_conforme,
        (SELECT b.nombre FROM stock_por_bodega sb 
         JOIN bodegas b ON b.id = sb.bodega_id 
         WHERE sb.id = td.stock_id) AS ubicacion_actual,
        (SELECT sb.stock_actual FROM stock_por_bodega sb 
         WHERE sb.id = td.stock_id) AS stock_actual,
        td.estado
    FROM trazabilidad_dm td
    WHERE td.activo = TRUE
) t;

COMMENT ON VIEW v_simulacion_trazabilidad IS 
    'Vista para simulaciones anuales de trazabilidad (RF-071)';

-- ============================================================
-- FUNCIÓN: Bloquear lote (llamada desde el backend)
-- ============================================================
CREATE OR REPLACE FUNCTION fn_bloquear_lote(
    p_lote_id BIGINT,
    p_motivo VARCHAR(255),
    p_usuario_rut VARCHAR(12)
) RETURNS VOID AS $$
BEGIN
    UPDATE lotes 
    SET bloqueado = TRUE,
        motivo_bloqueo = p_motivo,
        fecha_bloqueo = CURRENT_TIMESTAMP,
        bloqueado_por = p_usuario_rut,
        fecha_actualizacion = CURRENT_TIMESTAMP
    WHERE id = p_lote_id;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION fn_bloquear_lote IS 
    'Función auxiliar para bloquear un lote (llamada desde el backend)';

-- ============================================================
-- FUNCIÓN: Desbloquear lote
-- ============================================================
CREATE OR REPLACE FUNCTION fn_desbloquear_lote(
    p_lote_id BIGINT,
    p_usuario_rut VARCHAR(12)
) RETURNS VOID AS $$
BEGIN
    UPDATE lotes 
    SET bloqueado = FALSE,
        motivo_bloqueo = NULL,
        fecha_bloqueo = NULL,
        bloqueado_por = NULL,
        fecha_actualizacion = CURRENT_TIMESTAMP
    WHERE id = p_lote_id;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION fn_desbloquear_lote IS 
    'Función auxiliar para desbloquear un lote (llamada desde el backend)';