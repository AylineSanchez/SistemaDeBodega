-- ============================================================
-- MÓDULO 2: MAESTRO DE ARTÍCULOS (PRODUCTOS)
-- Sistema de Gestión Integral de Bodega - DSM Santa Cruz
-- Cubre: RF-011 a RF-019
-- 
-- Adaptado a la realidad del DSM:
--   - 6 bodegas físicas distribuidas por dependencia
--   - Un artículo puede existir en varias bodegas
--   - El stock se controla a nivel artículo+bodega
-- ============================================================

-- ============================================================
-- TABLA: CATEGORIAS
-- Clasificación de productos según el Excel de insumos
-- ============================================================
CREATE TABLE categorias (
    id          SERIAL PRIMARY KEY,
    codigo      VARCHAR(20)  NOT NULL UNIQUE,
    nombre      VARCHAR(100) NOT NULL,
    descripcion VARCHAR(255),
    color       VARCHAR(7)   DEFAULT '#d35400',
    orden       INTEGER      NOT NULL DEFAULT 0,
    activa      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ============================================================
-- TABLA: BODEGAS (6 bodegas reales del DSM)
-- ============================================================
CREATE TABLE bodegas (
    id           SERIAL PRIMARY KEY,
    codigo       VARCHAR(20)  NOT NULL UNIQUE,   -- ej: "B1", "B2", "B3"...
    nombre       VARCHAR(150) NOT NULL,          -- ej: "Bodega N°1 - Dental"
    descripcion  VARCHAR(255),
    dependencia  VARCHAR(100),                   -- ej: "Dental", "CESFAM", "SAR"
    ubicacion    VARCHAR(255),                   -- ubicación física
    es_principal BOOLEAN      NOT NULL DEFAULT FALSE,
    activa       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ============================================================
-- TABLA: MAESTRO_ARTICULOS (catálogo único de productos)
-- Un artículo se registra UNA SOLA VEZ, sin importar en cuántas
-- bodegas esté. Ej: "Guantes Nitrilo M" es 1 solo artículo que
-- puede estar en Bodega 2, 4 y 6.
-- ============================================================
CREATE TABLE maestro_articulos (
    id                          BIGSERIAL    PRIMARY KEY,
    
    -- --------------------------------------------------------
    -- 1. IDENTIFICACIÓN BÁSICA Y CLASIFICACIÓN (RF-011)
    -- --------------------------------------------------------
    sku                         VARCHAR(50)  NOT NULL UNIQUE,
    nombre_tecnico              VARCHAR(200) NOT NULL,
    presentacion                VARCHAR(150),
    concentracion               VARCHAR(100),
    categoria_id                INTEGER      NOT NULL REFERENCES categorias(id),
    subcategoria                VARCHAR(100),
    descripcion                 TEXT,
    
    -- --------------------------------------------------------
    -- 2. DATOS LOGÍSTICOS (RF-012)
    -- NOTA: stock_minimo/maximo son GLOBALES del artículo.
    -- El stock actual va por bodega en `stock_por_bodega`.
    -- --------------------------------------------------------
    unidad_medida_base          VARCHAR(30)  NOT NULL,
    unidad_compra               VARCHAR(30),
    factor_conversion           INTEGER      NOT NULL DEFAULT 1,
    stock_minimo                INTEGER      NOT NULL DEFAULT 0,
    stock_maximo                INTEGER,
    stock_seguridad             INTEGER      NOT NULL DEFAULT 0,
    punto_reorden               INTEGER,
    
    -- --------------------------------------------------------
    -- 3. DATOS SANITARIOS (RF-013)
    -- --------------------------------------------------------
    requiere_lote               BOOLEAN      NOT NULL DEFAULT FALSE,
    requiere_vencimiento        BOOLEAN      NOT NULL DEFAULT FALSE,
    registro_isp                VARCHAR(50),
    es_dispositivo_medico       BOOLEAN      NOT NULL DEFAULT FALSE,
    es_insumo_critico           BOOLEAN      NOT NULL DEFAULT FALSE,
    condiciones_almacenamiento  VARCHAR(255),
    
    -- --------------------------------------------------------
    -- 4. DATOS DE COMPRAS PÚBLICAS (RF-014)
    -- --------------------------------------------------------
    codigo_unspsc               VARCHAR(20),
    tipo_proveedor_habitual     VARCHAR(50),
    codigo_cenabast             VARCHAR(50),
    fabricante                  VARCHAR(150),
    pais_origen                 VARCHAR(80),
    
    -- --------------------------------------------------------
    -- CONTROL
    -- --------------------------------------------------------
    precio_promedio_ponderado   NUMERIC(12,2) DEFAULT 0,
    activo                      BOOLEAN      NOT NULL DEFAULT TRUE,
    observaciones               TEXT,
    
    -- Trazabilidad de importación
    origen_importacion          VARCHAR(50),  -- "EXCEL_INSUMOS_ASEO", "MANUAL", etc.
    
    fecha_creacion              TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_actualizacion         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    creado_por                  VARCHAR(12),
    actualizado_por             VARCHAR(12)
);

-- ============================================================
-- TABLA: STOCK_POR_BODEGA (stock específico de cada bodega)
-- Relación N:M entre artículos y bodegas, con datos propios.
-- ============================================================
CREATE TABLE stock_por_bodega (
    id                    BIGSERIAL    PRIMARY KEY,
    articulo_id           BIGINT       NOT NULL REFERENCES maestro_articulos(id) ON DELETE CASCADE,
    bodega_id             INTEGER      NOT NULL REFERENCES bodegas(id) ON DELETE CASCADE,
    
    -- Stock específico de esta bodega
    stock_actual          INTEGER      NOT NULL DEFAULT 0,
    ubicacion_fisica      VARCHAR(150),           -- Estantería A3, Pasillo 2
    estante               VARCHAR(50),
    nivel                 VARCHAR(50),
    
    -- Control
    activo                BOOLEAN      NOT NULL DEFAULT TRUE,
    fecha_creacion        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_actualizacion   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    creado_por            VARCHAR(12),
    actualizado_por       VARCHAR(12),
    
    -- Un artículo no puede estar 2 veces en la misma bodega
    CONSTRAINT uk_articulo_bodega UNIQUE (articulo_id, bodega_id),
    
    -- No permitir stock negativo
    CONSTRAINT chk_stock_no_negativo CHECK (stock_actual >= 0)
);

-- ============================================================
-- ÍNDICES
-- ============================================================
CREATE INDEX idx_articulos_sku              ON maestro_articulos(sku);
CREATE INDEX idx_articulos_nombre           ON maestro_articulos(LOWER(nombre_tecnico));
CREATE INDEX idx_articulos_categoria        ON maestro_articulos(categoria_id);
CREATE INDEX idx_articulos_activo           ON maestro_articulos(activo);
CREATE INDEX idx_articulos_requiere_lote    ON maestro_articulos(requiere_lote);
CREATE INDEX idx_articulos_dm               ON maestro_articulos(es_dispositivo_medico);
CREATE INDEX idx_articulos_unspsc           ON maestro_articulos(codigo_unspsc);
CREATE INDEX idx_articulos_origen           ON maestro_articulos(origen_importacion);

CREATE INDEX idx_stock_articulo             ON stock_por_bodega(articulo_id);
CREATE INDEX idx_stock_bodega               ON stock_por_bodega(bodega_id);
CREATE INDEX idx_stock_bajo                 ON stock_por_bodega(stock_actual)
    WHERE stock_actual = 0;

CREATE INDEX idx_bodegas_dependencia        ON bodegas(dependencia);

-- ============================================================
-- TRIGGERS: actualizar timestamp automáticamente
-- ============================================================
CREATE OR REPLACE FUNCTION trigger_set_timestamp()
RETURNS TRIGGER AS $$
BEGIN
    NEW.fecha_actualizacion = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_articulos_updated_at
BEFORE UPDATE ON maestro_articulos
FOR EACH ROW EXECUTE FUNCTION trigger_set_timestamp();

CREATE TRIGGER trg_stock_updated_at
BEFORE UPDATE ON stock_por_bodega
FOR EACH ROW EXECUTE FUNCTION trigger_set_timestamp();

CREATE TRIGGER trg_categorias_updated_at
BEFORE UPDATE ON categorias
FOR EACH ROW EXECUTE FUNCTION trigger_set_timestamp();

CREATE TRIGGER trg_bodegas_updated_at
BEFORE UPDATE ON bodegas
FOR EACH ROW EXECUTE FUNCTION trigger_set_timestamp();

-- ============================================================
-- DATOS INICIALES: CATEGORÍAS
-- Basadas en los sheets del Excel de insumos
-- ============================================================
INSERT INTO categorias (codigo, nombre, descripcion, color, orden) VALUES
    ('DENTAL',      'Insumos Dentales',              'Materiales e insumos odontológicos (Sheet: INSUMOS DENTALES)',       '#2980b9', 1),
    ('IMPL-DENTAL', 'Implementación Dental',         'Instrumental y equipamiento dental (Sheet: IMPLEMENTACION DENTAL)',  '#8e44ad', 2),
    ('MEDICOS',     'Insumos Médicos',               'Insumos médicos generales (Sheet: INSUMOS MEDICOS)',                 '#c0392b', 3),
    ('ASEO',        'Aseo',                          'Productos de aseo y limpieza (Sheet: INSUMOS ASEO)',                 '#27ae60', 4),
    ('LIBRERIA',    'Librería',                      'Artículos de oficina y papelería (Sheet: INSUMOS LIBRERIA)',         '#f39c12', 5),
    ('PODOLOGIA',   'Podología',                     'Instrumental para podología (Sheet: INSTRUMENTAL PODOLOGA CESFAM)',  '#16a085', 6);

-- ============================================================
-- DATOS INICIALES: BODEGAS (las 6 reales del DSM)
-- ============================================================
INSERT INTO bodegas (codigo, nombre, descripcion, dependencia, ubicacion, es_principal) VALUES
    ('B1', 'Bodega N°1 - Dental',                'Implementación, insumos médicos y librería DENTAL', 'Dental',  'Diego Portales 625, Santa Cruz', FALSE),
    ('B2', 'Bodega N°2 - CESFAM Insumos',        'Insumos médicos CESFAM',                            'CESFAM',  'Diego Portales 625, Santa Cruz', TRUE),
    ('B3', 'Bodega N°3 - CESFAM Aseo/Librería',  'Aseo y librería CESFAM',                            'CESFAM',  'Diego Portales 625, Santa Cruz', FALSE),
    ('B4', 'Bodega N°4 - SAR Insumos',           'Insumos médicos SAR',                               'SAR',     'Diego Portales 625, Santa Cruz', FALSE),
    ('B5', 'Bodega N°5 - SAR Madera 1',          'Bodega madera 1, SAR',                              'SAR',     'Diego Portales 625, Santa Cruz', FALSE),
    ('B6', 'Bodega N°6 - CESFAM Específicos',    'Bodega madera 2, Insumos médicos y específicos CESFAM', 'CESFAM', 'Diego Portales 625, Santa Cruz', FALSE);

-- ============================================================
-- VISTA: artículos con info de categoría + resumen de stock
-- ============================================================
CREATE OR REPLACE VIEW v_articulos_completo AS
SELECT 
    a.id,
    a.sku,
    a.nombre_tecnico,
    a.presentacion,
    a.concentracion,
    c.codigo   AS categoria_codigo,
    c.nombre   AS categoria_nombre,
    c.color    AS categoria_color,
    a.subcategoria,
    a.unidad_medida_base,
    a.stock_minimo,
    a.stock_maximo,
    a.stock_seguridad,
    a.requiere_lote,
    a.requiere_vencimiento,
    a.registro_isp,
    a.es_dispositivo_medico,
    a.es_insumo_critico,
    a.codigo_unspsc,
    a.tipo_proveedor_habitual,
    a.fabricante,
    a.activo,
    
    -- Stock total sumado de todas las bodegas
    COALESCE((
        SELECT SUM(sb.stock_actual)
        FROM stock_por_bodega sb
        WHERE sb.articulo_id = a.id AND sb.activo = TRUE
    ), 0) AS stock_total,
    
    -- Cantidad de bodegas donde existe
    COALESCE((
        SELECT COUNT(*)
        FROM stock_por_bodega sb
        WHERE sb.articulo_id = a.id AND sb.activo = TRUE
    ), 0) AS bodegas_count,
    
    -- Estado del stock (calculado sobre el total)
    CASE 
        WHEN COALESCE((
            SELECT SUM(sb.stock_actual)
            FROM stock_por_bodega sb
            WHERE sb.articulo_id = a.id AND sb.activo = TRUE
        ), 0) = 0 THEN 'QUIEBRE'
        WHEN COALESCE((
            SELECT SUM(sb.stock_actual)
            FROM stock_por_bodega sb
            WHERE sb.articulo_id = a.id AND sb.activo = TRUE
        ), 0) <= a.stock_minimo THEN 'CRITICO'
        WHEN COALESCE((
            SELECT SUM(sb.stock_actual)
            FROM stock_por_bodega sb
            WHERE sb.articulo_id = a.id AND sb.activo = TRUE
        ), 0) <= a.stock_minimo * 1.5 THEN 'BAJO'
        ELSE 'NORMAL'
    END AS estado_stock,
    
    a.fecha_creacion,
    a.fecha_actualizacion,
    a.creado_por,
    a.actualizado_por
FROM maestro_articulos a
JOIN categorias c ON c.id = a.categoria_id;

COMMENT ON VIEW v_articulos_completo IS 
    'Vista completa de artículos con categoría, stock total y estado calculado';

-- ============================================================
-- VISTA: stock detallado por artículo y bodega (para listado)
-- ============================================================
CREATE OR REPLACE VIEW v_stock_detallado AS
SELECT 
    sb.id                    AS stock_id,
    a.id                     AS articulo_id,
    a.sku,
    a.nombre_tecnico,
    c.codigo                 AS categoria_codigo,
    c.nombre                 AS categoria_nombre,
    c.color                  AS categoria_color,
    b.id                     AS bodega_id,
    b.codigo                 AS bodega_codigo,
    b.nombre                 AS bodega_nombre,
    b.dependencia,
    sb.stock_actual,
    sb.ubicacion_fisica,
    a.stock_minimo,
    a.stock_maximo,
    a.unidad_medida_base,
    a.requiere_lote,
    a.requiere_vencimiento,
    a.activo                 AS articulo_activo,
    sb.activo                AS stock_activo,
    CASE 
        WHEN sb.stock_actual = 0                       THEN 'QUIEBRE'
        WHEN sb.stock_actual <= a.stock_minimo         THEN 'CRITICO'
        WHEN sb.stock_actual <= a.stock_minimo * 1.5   THEN 'BAJO'
        ELSE 'NORMAL'
    END AS estado_stock
FROM stock_por_bodega sb
JOIN maestro_articulos a ON a.id = sb.articulo_id
JOIN categorias c ON c.id = a.categoria_id
JOIN bodegas b ON b.id = sb.bodega_id;

COMMENT ON VIEW v_stock_detallado IS 
    'Vista de stock por artículo-bodega con estado calculado';

-- ============================================================
-- VISTA: resumen por bodega (para dashboard)
-- ============================================================
CREATE OR REPLACE VIEW v_resumen_bodegas AS
SELECT 
    b.id                     AS bodega_id,
    b.codigo,
    b.nombre,
    b.dependencia,
    COUNT(DISTINCT sb.articulo_id)                              AS total_articulos,
    COALESCE(SUM(sb.stock_actual), 0)                           AS stock_total,
    COUNT(CASE WHEN sb.stock_actual = 0 THEN 1 END)             AS articulos_quiebre,
    COUNT(CASE WHEN sb.stock_actual > 0 
                AND sb.stock_actual <= a.stock_minimo THEN 1 END) AS articulos_criticos
FROM bodegas b
LEFT JOIN stock_por_bodega sb ON sb.bodega_id = b.id AND sb.activo = TRUE
LEFT JOIN maestro_articulos a ON a.id = sb.articulo_id
WHERE b.activa = TRUE
GROUP BY b.id, b.codigo, b.nombre, b.dependencia
ORDER BY b.codigo;

COMMENT ON VIEW v_resumen_bodegas IS 
    'Resumen de artículos y stock por bodega (para dashboard)';

-- ============================================================
-- VISTA: resumen por categoría (para dashboard)
-- ============================================================
CREATE OR REPLACE VIEW v_resumen_categorias AS
SELECT 
    c.id                     AS categoria_id,
    c.codigo,
    c.nombre,
    c.color,
    c.orden,
    COUNT(DISTINCT a.id)                                        AS total_articulos,
    COUNT(CASE WHEN a.activo THEN 1 END)                        AS articulos_activos,
    COALESCE(SUM(sb.stock_actual), 0)                           AS stock_total,
    COUNT(DISTINCT CASE WHEN sb.stock_actual > 0 THEN sb.articulo_id END) AS articulos_con_stock
FROM categorias c
LEFT JOIN maestro_articulos a ON a.categoria_id = c.id
LEFT JOIN stock_por_bodega sb ON sb.articulo_id = a.id AND sb.activo = TRUE
WHERE c.activa = TRUE
GROUP BY c.id, c.codigo, c.nombre, c.color, c.orden
ORDER BY c.orden;

COMMENT ON VIEW v_resumen_categorias IS 
    'Resumen de artículos por categoría (para dashboard)';