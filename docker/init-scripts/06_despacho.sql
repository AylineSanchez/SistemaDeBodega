-- ============================================================
-- MÓDULO 4: DESPACHO INTERNO (con FEFO obligatorio)
-- Sistema de Gestión Integral de Bodega - DSM Santa Cruz
-- Cubre: RF-029 a RF-041, RN-001 (FEFO)
--
-- Flujo:
--   1. Un servicio (Farmacia, SAR, Dental, etc.) pide insumos
--   2. Bodega prepara el pedido (picking)
--   3. Al escanear el QR del producto, FEFO obliga a usar el lote
--      con vencimiento más próximo
--   4. Al confirmar → se rebaja stock_por_bodega y se registra Kardex
-- ============================================================

-- ============================================================
-- TABLA: CENTROS_COSTO (servicios/dependencias del DSM)
-- ============================================================
CREATE TABLE centros_costo (
    id                SERIAL PRIMARY KEY,
    codigo            VARCHAR(20)  NOT NULL UNIQUE,     -- ej: "FARMACIA", "SAR", "DENTAL"
    nombre            VARCHAR(150) NOT NULL,            -- ej: "Farmacia Central"
    descripcion       VARCHAR(255),
    tipo              VARCHAR(30),                      -- FARMACIA | SAR | DENTAL | VACUNATORIO | LABORATORIO | BODEGA | OTRO
    responsable_rut   VARCHAR(12),                      -- RUT del encargado
    responsable_nombre VARCHAR(150),
    activo            BOOLEAN      NOT NULL DEFAULT TRUE,
    fecha_creacion    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_actualizacion TIMESTAMP  NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_centros_costo_codigo     ON centros_costo(codigo);
CREATE INDEX idx_centros_costo_tipo       ON centros_costo(tipo);

COMMENT ON TABLE centros_costo IS 
    'Servicios y dependencias que reciben despachos (Farmacia, SAR, Dental, etc.)';

-- ============================================================
-- TABLA: PEDIDOS_INTERNOS (solicitudes de servicios)
-- ============================================================
CREATE TABLE pedidos_internos (
    id                       BIGSERIAL    PRIMARY KEY,
    
    -- Correlativo por dependencia (RN-011)
    numero_pedido            VARCHAR(30)  NOT NULL UNIQUE,   -- ej: "PED-FARMACIA-2026-0001"
    
    -- Dependencia que solicita
    centro_costo_id          INTEGER      NOT NULL REFERENCES centros_costo(id),
    
    -- Quién solicita
    solicitante_rut          VARCHAR(12)  NOT NULL,
    solicitante_nombre       VARCHAR(150) NOT NULL,
    solicitante_cargo        VARCHAR(100),
    
    -- Prioridad
    prioridad                VARCHAR(20)  NOT NULL DEFAULT 'NORMAL',  -- URGENTE | NORMAL
    
    -- Fechas
    fecha_solicitud          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_requerida          DATE,                                    -- cuándo lo necesitan
    fecha_despacho           TIMESTAMP,                               -- cuándo se despachó
    
    -- Estado
    estado                   VARCHAR(20)  NOT NULL DEFAULT 'PENDIENTE',
    -- PENDIENTE: creado, esperando picking
    -- EN_PREPARACION: siendo preparado
    -- DESPACHADO: ya salió
    -- PARCIAL: se despachó menos de lo pedido
    -- CANCELADO: anulado
    -- RECHAZADO: no se puede cumplir
    
    -- Visación digital
    jefe_servicio_rut        VARCHAR(12),
    jefe_servicio_nombre     VARCHAR(150),
    jefe_bodega_rut          VARCHAR(12),
    jefe_bodega_nombre       VARCHAR(150),
    receptor_rut             VARCHAR(12),                             -- quién recibe físicamente
    receptor_nombre          VARCHAR(150),
    firma_receptor_fecha     TIMESTAMP,
    
    observaciones            TEXT,
    
    -- Control
    activo                   BOOLEAN      NOT NULL DEFAULT TRUE,
    fecha_creacion           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_actualizacion      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    creado_por               VARCHAR(12),
    actualizado_por          VARCHAR(12),
    
    CONSTRAINT chk_prioridad CHECK (prioridad IN ('URGENTE', 'NORMAL')),
    CONSTRAINT chk_estado_pedido CHECK (estado IN ('PENDIENTE', 'EN_PREPARACION', 'DESPACHADO', 'PARCIAL', 'CANCELADO', 'RECHAZADO'))
);

CREATE INDEX idx_pedidos_numero           ON pedidos_internos(numero_pedido);
CREATE INDEX idx_pedidos_centro           ON pedidos_internos(centro_costo_id);
CREATE INDEX idx_pedidos_estado           ON pedidos_internos(estado);
CREATE INDEX idx_pedidos_fecha_sol        ON pedidos_internos(fecha_solicitud);
CREATE INDEX idx_pedidos_prioridad        ON pedidos_internos(prioridad);

COMMENT ON TABLE pedidos_internos IS 
    'Solicitudes internas de los servicios del DSM (RF-029, RF-040, RF-041)';

-- ============================================================
-- TABLA: PEDIDO_DETALLE (líneas de cada pedido)
-- ============================================================
CREATE TABLE pedido_detalle (
    id                       BIGSERIAL    PRIMARY KEY,
    pedido_id                BIGINT       NOT NULL REFERENCES pedidos_internos(id) ON DELETE CASCADE,
    
    -- Producto
    articulo_id              BIGINT       NOT NULL REFERENCES maestro_articulos(id),
    sku                      VARCHAR(50)  NOT NULL,           -- snapshot
    nombre_producto          VARCHAR(200) NOT NULL,           -- snapshot
    
    -- Cantidades
    cantidad_solicitada      INTEGER      NOT NULL DEFAULT 0,
    cantidad_despachada      INTEGER      NOT NULL DEFAULT 0,  -- puede diferir (despacho parcial)
    
    -- Estado de la línea
    estado                   VARCHAR(20)  NOT NULL DEFAULT 'PENDIENTE',
    -- PENDIENTE | PREPARADO | DESPACHADO | PARCIAL | CANCELADO
    
    observaciones            TEXT,
    
    fecha_creacion           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_actualizacion      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT chk_cantidad_solicitada CHECK (cantidad_solicitada > 0),
    CONSTRAINT chk_cantidad_despachada CHECK (cantidad_despachada >= 0),
    CONSTRAINT chk_estado_pedido_linea CHECK (estado IN ('PENDIENTE', 'PREPARADO', 'DESPACHADO', 'PARCIAL', 'CANCELADO'))
);

CREATE INDEX idx_pedido_detalle_pedido    ON pedido_detalle(pedido_id);
CREATE INDEX idx_pedido_detalle_articulo  ON pedido_detalle(articulo_id);

COMMENT ON TABLE pedido_detalle IS 
    'Detalle de productos solicitados por pedido';

-- ============================================================
-- TABLA: DESPACHOS (cabecera del despacho efectivo)
-- Un pedido puede generar varios despachos (parciales)
-- ============================================================
CREATE TABLE despachos (
    id                       BIGSERIAL    PRIMARY KEY,
    
    -- Correlativo
    numero_despacho          VARCHAR(30)  NOT NULL UNIQUE,   -- ej: "DES-2026-0001"
    
    -- Pedido de origen (puede ser NULL si es despacho express sin pedido)
    pedido_id                BIGINT       REFERENCES pedidos_internos(id),
    
    -- Centro de costo destino
    centro_costo_id          INTEGER      NOT NULL REFERENCES centros_costo(id),
    
    -- Bodega de origen
    bodega_id                INTEGER      NOT NULL REFERENCES bodegas(id),
    
    -- Destinatario
    receptor_rut             VARCHAR(12)  NOT NULL,
    receptor_nombre          VARCHAR(150) NOT NULL,
    receptor_cargo           VARCHAR(100),
    
    -- Fechas
    fecha_despacho           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- Estado
    estado                   VARCHAR(20)  NOT NULL DEFAULT 'BORRADOR',
    -- BORRADOR | CONFIRMADO | ANULADO | PARCIAL
    
    -- Documento
    guia_interna             VARCHAR(50),                     -- N° guía interna
    
    observaciones            TEXT,
    
    -- Control
    activo                   BOOLEAN      NOT NULL DEFAULT TRUE,
    fecha_creacion           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_actualizacion      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    creado_por               VARCHAR(12),
    actualizado_por          VARCHAR(12),
    
    CONSTRAINT chk_estado_despacho CHECK (estado IN ('BORRADOR', 'CONFIRMADO', 'ANULADO', 'PARCIAL'))
);

CREATE INDEX idx_despachos_numero         ON despachos(numero_despacho);
CREATE INDEX idx_despachos_pedido         ON despachos(pedido_id);
CREATE INDEX idx_despachos_centro         ON despachos(centro_costo_id);
CREATE INDEX idx_despachos_bodega         ON despachos(bodega_id);
CREATE INDEX idx_despachos_fecha          ON despachos(fecha_despacho);
CREATE INDEX idx_despachos_estado         ON despachos(estado);

COMMENT ON TABLE despachos IS 
    'Cabecera del despacho efectivo (RF-029 a RF-038)';

-- ============================================================
-- TABLA: DESPACHO_DETALLE (productos + lotes despachados)
-- Registra QUÉ lote se usó para cumplir con FEFO
-- ============================================================
CREATE TABLE despacho_detalle (
    id                       BIGSERIAL    PRIMARY KEY,
    despacho_id              BIGINT       NOT NULL REFERENCES despachos(id) ON DELETE CASCADE,
    
    -- Producto
    articulo_id              BIGINT       NOT NULL REFERENCES maestro_articulos(id),
    sku                      VARCHAR(50)  NOT NULL,           -- snapshot
    nombre_producto          VARCHAR(200) NOT NULL,           -- snapshot
    
    -- Lote específico usado (FEFO obligatorio para productos con lote)
    lote_id                  BIGINT       REFERENCES lotes(id),
    numero_lote              VARCHAR(50),                     -- snapshot
    fecha_vencimiento        DATE,                            -- snapshot
    
    -- Cantidad despachada
    cantidad                 INTEGER      NOT NULL DEFAULT 0,
    
    -- Valorización (para reportes de consumo por centro de costo)
    valor_unitario           NUMERIC(12,2),
    valor_total              NUMERIC(12,2),
    
    observaciones            TEXT,
    
    fecha_creacion           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_actualizacion      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT chk_cantidad_despacho CHECK (cantidad > 0)
);

CREATE INDEX idx_despacho_detalle_desp     ON despacho_detalle(despacho_id);
CREATE INDEX idx_despacho_detalle_art      ON despacho_detalle(articulo_id);
CREATE INDEX idx_despacho_detalle_lote     ON despacho_detalle(lote_id);

COMMENT ON TABLE despacho_detalle IS 
    'Detalle de productos y lotes despachados. Registra el uso específico de cada lote (FEFO).';

-- ============================================================
-- TRIGGERS
-- ============================================================
CREATE TRIGGER trg_centros_costo_updated_at
BEFORE UPDATE ON centros_costo
FOR EACH ROW EXECUTE FUNCTION trigger_set_timestamp();

CREATE TRIGGER trg_pedidos_internos_updated_at
BEFORE UPDATE ON pedidos_internos
FOR EACH ROW EXECUTE FUNCTION trigger_set_timestamp();

CREATE TRIGGER trg_pedido_detalle_updated_at
BEFORE UPDATE ON pedido_detalle
FOR EACH ROW EXECUTE FUNCTION trigger_set_timestamp();

CREATE TRIGGER trg_despachos_updated_at
BEFORE UPDATE ON despachos
FOR EACH ROW EXECUTE FUNCTION trigger_set_timestamp();

CREATE TRIGGER trg_despacho_detalle_updated_at
BEFORE UPDATE ON despacho_detalle
FOR EACH ROW EXECUTE FUNCTION trigger_set_timestamp();

-- ============================================================
-- VISTAS
-- ============================================================

-- Vista: pedidos con info completa
CREATE OR REPLACE VIEW v_pedidos_completo AS
SELECT 
    p.id,
    p.numero_pedido,
    cc.codigo   AS centro_codigo,
    cc.nombre   AS centro_nombre,
    cc.tipo     AS centro_tipo,
    p.solicitante_rut,
    p.solicitante_nombre,
    p.prioridad,
    p.fecha_solicitud,
    p.fecha_requerida,
    p.fecha_despacho,
    p.estado,
    p.observaciones,
    (SELECT COUNT(*) FROM pedido_detalle pd WHERE pd.pedido_id = p.id) AS total_lineas,
    (SELECT COALESCE(SUM(pd.cantidad_solicitada), 0) FROM pedido_detalle pd WHERE pd.pedido_id = p.id) AS total_unidades_solicitadas,
    (SELECT COALESCE(SUM(pd.cantidad_despachada), 0) FROM pedido_detalle pd WHERE pd.pedido_id = p.id) AS total_unidades_despachadas
FROM pedidos_internos p
JOIN centros_costo cc ON cc.id = p.centro_costo_id;

COMMENT ON VIEW v_pedidos_completo IS 
    'Vista de pedidos con centro de costo y totales';

-- Vista: despachos con info completa
CREATE OR REPLACE VIEW v_despachos_completo AS
SELECT 
    d.id,
    d.numero_despacho,
    d.pedido_id,
    p.numero_pedido,
    cc.codigo   AS centro_codigo,
    cc.nombre   AS centro_nombre,
    cc.tipo     AS centro_tipo,
    b.codigo    AS bodega_codigo,
    b.nombre    AS bodega_nombre,
    d.receptor_rut,
    d.receptor_nombre,
    d.fecha_despacho,
    d.estado,
    d.observaciones,
    (SELECT COUNT(*) FROM despacho_detalle dd WHERE dd.despacho_id = d.id) AS total_lineas,
    (SELECT COALESCE(SUM(dd.cantidad), 0) FROM despacho_detalle dd WHERE dd.despacho_id = d.id) AS total_unidades
FROM despachos d
JOIN centros_costo cc ON cc.id = d.centro_costo_id
JOIN bodegas b ON b.id = d.bodega_id
LEFT JOIN pedidos_internos p ON p.id = d.pedido_id;

COMMENT ON VIEW v_despachos_completo IS 
    'Vista de despachos con centro de costo, bodega y totales';

-- Vista: stock disponible por artículo-bodega-lote (para FEFO)
CREATE OR REPLACE VIEW v_stock_fefo AS
SELECT 
    sl.id                     AS stock_lote_id,
    sb.id                     AS stock_id,
    sb.codigo_qr,
    a.id                      AS articulo_id,
    a.sku,
    a.nombre_tecnico,
    a.requiere_lote,
    a.requiere_vencimiento,
    a.unidad_medida_base,
    b.id                      AS bodega_id,
    b.codigo                  AS bodega_codigo,
    b.nombre                  AS bodega_nombre,
    l.id                      AS lote_id,
    l.numero_lote,
    l.fecha_vencimiento,
    (l.fecha_vencimiento - CURRENT_DATE) AS dias_para_vencer,
    l.bloqueado,
    sl.cantidad               AS stock_disponible,
    sb.ubicacion_fisica,
    sb.estante,
    sb.nivel,
    CASE 
        WHEN l.bloqueado = TRUE THEN 'BLOQUEADO'
        WHEN sl.cantidad = 0 THEN 'SIN_STOCK'
        WHEN l.fecha_vencimiento < CURRENT_DATE THEN 'VENCIDO'
        ELSE 'DISPONIBLE'
    END AS estado_disponibilidad
FROM stock_por_bodega sb
LEFT JOIN stock_lotes sl ON sl.stock_id = sb.id
LEFT JOIN lotes l ON l.id = sl.lote_id
JOIN maestro_articulos a ON a.id = sb.articulo_id
JOIN bodegas b ON b.id = sb.bodega_id
WHERE sb.activo = TRUE;

COMMENT ON VIEW v_stock_fefo IS 
    'Stock disponible por artículo-bodega-lote ordenado para FEFO. Excluye bloqueados.';

-- Vista: pedidos pendientes por centro de costo (para bandeja)
CREATE OR REPLACE VIEW v_pedidos_pendientes AS
SELECT 
    p.id,
    p.numero_pedido,
    cc.codigo   AS centro_codigo,
    cc.nombre   AS centro_nombre,
    p.solicitante_nombre,
    p.prioridad,
    p.fecha_solicitud,
    p.fecha_requerida,
    p.estado,
    EXTRACT(EPOCH FROM (CURRENT_TIMESTAMP - p.fecha_solicitud))/3600 AS horas_desde_solicitud
FROM pedidos_internos p
JOIN centros_costo cc ON cc.id = p.centro_costo_id
WHERE p.estado IN ('PENDIENTE', 'EN_PREPARACION')
  AND p.activo = TRUE
ORDER BY 
    CASE p.prioridad WHEN 'URGENTE' THEN 1 ELSE 2 END,
    p.fecha_solicitud ASC;

COMMENT ON VIEW v_pedidos_pendientes IS 
    'Pedidos pendientes ordenados por prioridad y antigüedad';

-- ============================================================
-- DATOS INICIALES: CENTROS DE COSTO
-- ============================================================
INSERT INTO centros_costo (codigo, nombre, descripcion, tipo, responsable_rut, responsable_nombre) VALUES
    ('FARMACIA',     'Farmacia Central',           'Farmacia del CESFAM',                       'FARMACIA',    NULL, NULL),
    ('SAR',          'SAR Santa Cruz',             'Servicio de Atención Primaria de Urgencia', 'SAR',         NULL, NULL),
    ('DENTAL',       'Dental CESFAM',              'Consultorio dental',                        'DENTAL',      NULL, NULL),
    ('VACUNATORIO',  'Vacunatorio',                'Vacunatorio del CESFAM',                    'VACUNATORIO', NULL, NULL),
    ('LABORATORIO',  'Laboratorio',                'Laboratorio clínico',                       'LABORATORIO', NULL, NULL),
    ('BODEGA_DENTAL','Bodega Dental',              'Bodega exclusiva de insumos dentales',      'BODEGA',      NULL, NULL),
    ('BODEGA_CESFAM','Bodega CESFAM',              'Bodega principal CESFAM',                   'BODEGA',      NULL, NULL),
    ('BODEGA_SAR',   'Bodega SAR',                 'Bodega del SAR',                            'BODEGA',      NULL, NULL);