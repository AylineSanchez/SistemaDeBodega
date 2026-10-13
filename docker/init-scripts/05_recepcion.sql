-- ============================================================
-- MÓDULO 3: RECEPCIÓN DE MERCADERÍA
-- Sistema de Gestión Integral de Bodega - DSM Santa Cruz
-- Cubre: RF-020 a RF-028

-- Flujo:
--   1. Llega un camión con factura/guía
--   2. Se registra el documento (proveedor + N° + fecha)
--   3. Se agregan los productos recibidos (SKU, cantidad, lote, vencimiento)
--   4. Al confirmar → se actualiza stock_por_bodega y se registra Kardex
-- ============================================================

-- ============================================================
-- TABLA: RECEPCIONES (cabecera del documento de recepción)
-- ============================================================
CREATE TABLE recepciones (
    id                       BIGSERIAL    PRIMARY KEY,

    -- Correlativo interno
    numero_recepcion         VARCHAR(30)  NOT NULL UNIQUE,   -- ej: "REC-2026-0001"

    -- Documento de respaldo (factura o guía de despacho)
    tipo_documento           VARCHAR(20)  NOT NULL,          -- FACTURA | GUIA_DESPACHO
    numero_documento         VARCHAR(50)  NOT NULL,          -- N° factura o guía
    numero_orden_compra      VARCHAR(50),                    -- OC Mercado Público
    numero_guia_despacho      VARCHAR(50),                   -- N° guía si es factura

    -- Proveedor
    rut_proveedor            VARCHAR(12)  NOT NULL,
    nombre_proveedor         VARCHAR(200) NOT NULL,
    telefono_proveedor       VARCHAR(30),
    direccion_proveedor      VARCHAR(255),

    -- Tipo de proveedor (RF-014)
    tipo_proveedor           VARCHAR(50),                    -- Cenabast | Mercado Público | Compra Ágil | Licitación

    -- Fechas
    fecha_emision            DATE         NOT NULL,          -- fecha del documento
    fecha_recepcion          DATE         NOT NULL,          -- fecha en que llegó físicamente
    fecha_hora_ingreso       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Bodega de destino
    bodega_id                INTEGER      NOT NULL REFERENCES bodegas(id),

    -- Visación del receptor (RF-023)
    receptor_rut             VARCHAR(12),                    -- RUT del funcionario que recibe
    receptor_nombre          VARCHAR(150),
    receptor_fecha           TIMESTAMP,

    -- Totales
    monto_neto               NUMERIC(12,2) DEFAULT 0,
    monto_iva                NUMERIC(12,2) DEFAULT 0,
    monto_total              NUMERIC(12,2) DEFAULT 0,

    -- Estados
    estado                   VARCHAR(20)  NOT NULL DEFAULT 'BORRADOR',
    -- BORRADOR: se está armando
    -- CONFIRMADA: ya se ingresó al stock
    -- ANULADA: se anuló
    -- PARCIAL: se recibió menos de lo esperado

    observaciones            TEXT,

    -- Control
    activo                   BOOLEAN      NOT NULL DEFAULT TRUE,
    fecha_creacion           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_actualizacion      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    creado_por               VARCHAR(12),
    actualizado_por          VARCHAR(12),

    CONSTRAINT chk_tipo_documento CHECK (tipo_documento IN ('FACTURA', 'GUIA_DESPACHO')),
    CONSTRAINT chk_estado_recepcion CHECK (estado IN ('BORRADOR', 'CONFIRMADA', 'ANULADA', 'PARCIAL'))
);

CREATE INDEX idx_recepciones_numero       ON recepciones(numero_recepcion);
CREATE INDEX idx_recepciones_documento    ON recepciones(tipo_documento, numero_documento);
CREATE INDEX idx_recepciones_rut_prov     ON recepciones(rut_proveedor);
CREATE INDEX idx_recepciones_fecha        ON recepciones(fecha_recepcion);
CREATE INDEX idx_recepciones_bodega       ON recepciones(bodega_id);
CREATE INDEX idx_recepciones_estado       ON recepciones(estado);
CREATE INDEX idx_recepciones_oc           ON recepciones(numero_orden_compra);

COMMENT ON TABLE recepciones IS 
    'Cabecera de cada recepción de mercadería (RF-020 a RF-022)';

-- ============================================================
-- TABLA: RECEPCION_DETALLE (líneas de productos recibidos)
-- ============================================================
CREATE TABLE recepcion_detalle (
    id                       BIGSERIAL    PRIMARY KEY,
    recepcion_id             BIGINT       NOT NULL REFERENCES recepciones(id) ON DELETE CASCADE,

    -- Producto
    articulo_id              BIGINT       NOT NULL REFERENCES maestro_articulos(id),

    -- ⚡ NUEVO: Bodega destino del detalle (puede diferir de la recepción)
    bodega_id                INTEGER      REFERENCES bodegas(id),

    sku                      VARCHAR(50)  NOT NULL,          -- snapshot del SKU
    nombre_producto          VARCHAR(200) NOT NULL,          -- snapshot del nombre

    -- Cantidades y valores
    cantidad_recibida        INTEGER      NOT NULL DEFAULT 0,
    cantidad_pendiente       INTEGER      NOT NULL DEFAULT 0,   -- para recepciones parciales (RF-026)
    valor_unitario           NUMERIC(12,2) DEFAULT 0,
    monto_total              NUMERIC(12,2) DEFAULT 0,

    -- Datos sanitarios (RF-023)
    numero_lote              VARCHAR(50),
    fecha_vencimiento        DATE,
    registro_isp             VARCHAR(50),

    -- Ubicación física
    ubicacion_fisica         VARCHAR(150),
    estante                  VARCHAR(50),
    nivel                    VARCHAR(50),

    -- Estado de la línea
    estado                   VARCHAR(20)  NOT NULL DEFAULT 'PENDIENTE',
    -- PENDIENTE | RECIBIDA | PARCIAL | RECHAZADA

    observaciones            TEXT,

    fecha_creacion           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_actualizacion      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_cantidad_positiva CHECK (cantidad_recibida >= 0),
    CONSTRAINT chk_estado_linea CHECK (estado IN ('PENDIENTE', 'RECIBIDA', 'PARCIAL', 'RECHAZADA'))
);

CREATE INDEX idx_recepcion_detalle_rec    ON recepcion_detalle(recepcion_id);
CREATE INDEX idx_recepcion_detalle_art    ON recepcion_detalle(articulo_id);
CREATE INDEX idx_recepcion_detalle_lote   ON recepcion_detalle(numero_lote);
CREATE INDEX idx_recepcion_detalle_bodega ON recepcion_detalle(bodega_id);   -- ⚡ NUEVO

COMMENT ON TABLE recepcion_detalle IS 
    'Detalle de productos por recepción (RF-023 a RF-028)';

-- ============================================================
-- TABLA: LOTES (registro de lotes y vencimientos)
-- Base para FEFO y trazabilidad sanitaria
-- ============================================================
CREATE TABLE lotes (
    id                       BIGSERIAL    PRIMARY KEY,
    articulo_id              BIGINT       NOT NULL REFERENCES maestro_articulos(id) ON DELETE CASCADE,
    numero_lote              VARCHAR(50)  NOT NULL,
    fecha_vencimiento        DATE,
    fecha_fabricacion        DATE,
    registro_isp             VARCHAR(50),
    fabricante               VARCHAR(150),
    pais_origen              VARCHAR(80),

    -- Control
    activo                   BOOLEAN      NOT NULL DEFAULT TRUE,
    bloqueado                BOOLEAN      NOT NULL DEFAULT FALSE,  -- bloqueo ISP (07_trazabilidad)
    motivo_bloqueo           VARCHAR(255),
    fecha_bloqueo            TIMESTAMP,
    bloqueado_por            VARCHAR(12),

    fecha_creacion           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_actualizacion      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_lote_articulo UNIQUE (articulo_id, numero_lote)
);

CREATE INDEX idx_lotes_articulo           ON lotes(articulo_id);
CREATE INDEX idx_lotes_numero             ON lotes(numero_lote);
CREATE INDEX idx_lotes_vencimiento        ON lotes(fecha_vencimiento);
CREATE INDEX idx_lotes_bloqueado          ON lotes(bloqueado) WHERE bloqueado = TRUE;
CREATE INDEX idx_lotes_por_vencer         ON lotes(fecha_vencimiento, bloqueado) 
    WHERE bloqueado = FALSE AND activo = TRUE;

COMMENT ON TABLE lotes IS 
    'Registro maestro de lotes. Base para FEFO y bloqueo ISP (RN-001, RN-013)';

-- ============================================================
-- VINCULAR stock_por_bodega con lotes (FEFO)
-- Cada fila de stock_por_bodega puede tener múltiples lotes
-- ============================================================
CREATE TABLE stock_lotes (
    id                       BIGSERIAL    PRIMARY KEY,
    stock_id                 BIGINT       NOT NULL REFERENCES stock_por_bodega(id) ON DELETE CASCADE,
    lote_id                  BIGINT       NOT NULL REFERENCES lotes(id) ON DELETE CASCADE,
    cantidad                 INTEGER      NOT NULL DEFAULT 0,

    fecha_creacion           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_actualizacion      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_stock_lote UNIQUE (stock_id, lote_id),
    CONSTRAINT chk_cantidad_lote CHECK (cantidad >= 0)
);

CREATE INDEX idx_stock_lotes_stock        ON stock_lotes(stock_id);
CREATE INDEX idx_stock_lotes_lote         ON stock_lotes(lote_id);

COMMENT ON TABLE stock_lotes IS 
    'Relación stock-bodega-lote con cantidades específicas. Base del FEFO.';

-- ============================================================
-- TABLA: KARDEX (historial perpetuo de movimientos)
-- Se actualiza en Recepción, Despacho, Ajustes, Mermas
-- ============================================================
CREATE TABLE kardex (
    id                       BIGSERIAL    PRIMARY KEY,

    -- Producto y bodega
    articulo_id              BIGINT       NOT NULL REFERENCES maestro_articulos(id),
    bodega_id                INTEGER      NOT NULL REFERENCES bodegas(id),
    lote_id                  BIGINT       REFERENCES lotes(id),

    -- Tipo de movimiento
    tipo_movimiento          VARCHAR(30)  NOT NULL,
    -- INGRESO_RECEPCION | EGRESO_DESPACHO | AJUSTE_POSITIVO | AJUSTE_NEGATIVO
    -- MERMA_VENCIMIENTO | MERMA_DAÑO | TRASPASO_ENTRADA | TRASPASO_SALIDA
    -- DEVOLUCION_PROVEEDOR | BLOQUEO_ISP | DESBLOQUEO_ISP

    -- Documento de respaldo
    documento_tipo           VARCHAR(30),                    -- RECEPCION | DESPACHO | AJUSTE | MERMA | TRASPASO
    documento_numero         VARCHAR(50),                    -- N° interno del documento
    documento_referencia     VARCHAR(50),                    -- N° factura, guía, pedido, etc.

    -- Cantidades
    cantidad                 INTEGER      NOT NULL,           -- positivo=ingreso, negativo=egreso
    stock_anterior           INTEGER      NOT NULL,
    stock_nuevo              INTEGER      NOT NULL,

    -- Detalles
    valor_unitario           NUMERIC(12,2),
    valor_total              NUMERIC(12,2),
    observaciones            TEXT,

    -- Auditoría
    usuario_rut              VARCHAR(12),
    usuario_nombre           VARCHAR(150),
    ip_address               VARCHAR(45),
    fecha_movimiento         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_kardex_articulo          ON kardex(articulo_id);
CREATE INDEX idx_kardex_bodega            ON kardex(bodega_id);
CREATE INDEX idx_kardex_lote              ON kardex(lote_id);
CREATE INDEX idx_kardex_fecha             ON kardex(fecha_movimiento);
CREATE INDEX idx_kardex_tipo              ON kardex(tipo_movimiento);
CREATE INDEX idx_kardex_documento         ON kardex(documento_tipo, documento_numero);
CREATE INDEX idx_kardex_usuario           ON kardex(usuario_rut);

COMMENT ON TABLE kardex IS 
    'Historial perpetuo de movimientos. Base del Kardex (RF-055, RF-056, RNF-045)';

-- ============================================================
-- TRIGGERS
-- ============================================================
CREATE TRIGGER trg_recepciones_updated_at
BEFORE UPDATE ON recepciones
FOR EACH ROW EXECUTE FUNCTION trigger_set_timestamp();

CREATE TRIGGER trg_recepcion_detalle_updated_at
BEFORE UPDATE ON recepcion_detalle
FOR EACH ROW EXECUTE FUNCTION trigger_set_timestamp();

CREATE TRIGGER trg_lotes_updated_at
BEFORE UPDATE ON lotes
FOR EACH ROW EXECUTE FUNCTION trigger_set_timestamp();

CREATE TRIGGER trg_stock_lotes_updated_at
BEFORE UPDATE ON stock_lotes
FOR EACH ROW EXECUTE FUNCTION trigger_set_timestamp();

-- ============================================================
-- VISTAS
-- ============================================================

-- Vista: recepciones con info de bodega
CREATE OR REPLACE VIEW v_recepciones_completo AS
SELECT 
    r.id,
    r.numero_recepcion,
    r.tipo_documento,
    r.numero_documento,
    r.numero_orden_compra,
    r.rut_proveedor,
    r.nombre_proveedor,
    r.tipo_proveedor,
    r.fecha_emision,
    r.fecha_recepcion,
    b.codigo   AS bodega_codigo,
    b.nombre   AS bodega_nombre,
    b.dependencia,
    r.receptor_rut,
    r.receptor_nombre,
    r.monto_total,
    r.estado,
    r.observaciones,
    r.fecha_creacion,
    r.creado_por,
    (SELECT COUNT(*) FROM recepcion_detalle rd WHERE rd.recepcion_id = r.id) AS total_lineas,
    (SELECT COALESCE(SUM(rd.cantidad_recibida), 0) FROM recepcion_detalle rd WHERE rd.recepcion_id = r.id) AS total_unidades
FROM recepciones r
JOIN bodegas b ON b.id = r.bodega_id;

COMMENT ON VIEW v_recepciones_completo IS 
    'Vista de recepciones con bodega y totales';

-- Vista: lotes por vencer (base para alertas de vencimiento)
CREATE OR REPLACE VIEW v_lotes_por_vencer AS
SELECT 
    l.id                     AS lote_id,
    a.id                     AS articulo_id,
    a.sku,
    a.nombre_tecnico,
    c.codigo                 AS categoria_codigo,
    c.nombre                 AS categoria_nombre,
    l.numero_lote,
    l.fecha_vencimiento,
    (l.fecha_vencimiento - CURRENT_DATE) AS dias_para_vencer,
    CASE 
        WHEN l.fecha_vencimiento IS NULL                         THEN 'SIN_VENCIMIENTO'
        WHEN l.fecha_vencimiento < CURRENT_DATE                  THEN 'VENCIDO'
        WHEN l.fecha_vencimiento <= CURRENT_DATE + INTERVAL '120 days' THEN 'CRITICO'   -- 120 días
        WHEN l.fecha_vencimiento <= CURRENT_DATE + INTERVAL '180 days' THEN 'PROXIMO'   -- 180 días
        WHEN l.fecha_vencimiento <= CURRENT_DATE + INTERVAL '365 days' THEN 'VIGILAR'   -- 365 días
        ELSE 'OK'
    END AS estado_vencimiento,
    l.bloqueado,
    l.motivo_bloqueo,
    COALESCE(SUM(sl.cantidad), 0) AS stock_total_lote
FROM lotes l
JOIN maestro_articulos a ON a.id = l.articulo_id
JOIN categorias c ON c.id = a.categoria_id
LEFT JOIN stock_lotes sl ON sl.lote_id = l.id
WHERE l.activo = TRUE
GROUP BY l.id, a.id, a.sku, a.nombre_tecnico, c.codigo, c.nombre,
         l.numero_lote, l.fecha_vencimiento, l.bloqueado, l.motivo_bloqueo;

COMMENT ON VIEW v_lotes_por_vencer IS 
    'Lotes activos con estado de vencimiento calculado (RF-057, RF-058, RF-059)';

-- ============================================================
-- DATOS INICIALES
-- ============================================================
-- No hay datos iniciales: las recepciones se crean desde la app.