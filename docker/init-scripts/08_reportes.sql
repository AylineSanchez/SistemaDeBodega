-- ============================================================
-- MÓDULO 8: REPORTES
-- Sistema de Gestión Integral de Bodega - DSM Santa Cruz
-- Cubre: RF-075 a RF-085
--
-- Este script NO crea tablas nuevas (los datos ya están en las tablas
-- anteriores). Solo crea VISTAS y FUNCIONES para generar reportes.
-- ============================================================

-- ============================================================
-- TABLA: REPORTES_GENERADOS (auditoría de reportes generados)
-- ============================================================
CREATE TABLE reportes_generados (
    id                       BIGSERIAL    PRIMARY KEY,
    tipo_reporte             VARCHAR(50)  NOT NULL,
    -- ALERTAS_VENCIMIENTO | ALERTAS_STOCK | MERMAS_BAJAS | CONSUMO_CENTRO_COSTO
    -- KARDEX | LOG_AUDITORIA | VALORIZACION | TASA_MERMA | CUMPLIMIENTO_OC
    
    parametros               JSONB,                          -- filtros usados
    formato                  VARCHAR(20)  NOT NULL DEFAULT 'JSON',   -- JSON | PDF | EXCEL
    
    -- Resultado
    total_registros          INTEGER      DEFAULT 0,
    archivo_url              VARCHAR(500),                   -- URL del archivo generado
    
    -- Quién lo generó
    generado_por_rut         VARCHAR(12),
    generado_por_nombre      VARCHAR(150),
    fecha_generacion         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_reportes_tipo            ON reportes_generados(tipo_reporte);
CREATE INDEX idx_reportes_fecha           ON reportes_generados(fecha_generacion);
CREATE INDEX idx_reportes_usuario         ON reportes_generados(generado_por_rut);

COMMENT ON TABLE reportes_generados IS 
    'Auditoría de reportes generados (RF-080)';

-- ============================================================
-- VISTA: Reporte de Alertas de Vencimiento (RF-075)
-- ============================================================
CREATE OR REPLACE VIEW r_alertas_vencimiento AS
SELECT 
    a.id                     AS articulo_id,
    a.sku,
    a.nombre_tecnico,
    c.codigo                 AS categoria_codigo,
    c.nombre                 AS categoria_nombre,
    l.id                     AS lote_id,
    l.numero_lote,
    l.fecha_vencimiento,
    (l.fecha_vencimiento - CURRENT_DATE) AS dias_para_vencer,
    CASE 
        WHEN l.fecha_vencimiento < CURRENT_DATE                             THEN 'VENCIDO'
        WHEN l.fecha_vencimiento <= CURRENT_DATE + INTERVAL '120 days'       THEN 'CRITICO'
        WHEN l.fecha_vencimiento <= CURRENT_DATE + INTERVAL '180 days'       THEN 'PROXIMO'
        WHEN l.fecha_vencimiento <= CURRENT_DATE + INTERVAL '365 days'       THEN 'VIGILAR'
        ELSE 'OK'
    END AS estado_vencimiento,
    b.codigo                 AS bodega_codigo,
    b.nombre                 AS bodega_nombre,
    sl.cantidad              AS stock_en_bodega,
    l.bloqueado
FROM lotes l
JOIN maestro_articulos a ON a.id = l.articulo_id
JOIN categorias c ON c.id = a.categoria_id
LEFT JOIN stock_lotes sl ON sl.lote_id = l.id
LEFT JOIN stock_por_bodega sb ON sb.id = sl.stock_id
LEFT JOIN bodegas b ON b.id = sb.bodega_id
WHERE l.activo = TRUE
  AND a.activo = TRUE
  AND l.fecha_vencimiento IS NOT NULL
ORDER BY 
    CASE 
        WHEN l.fecha_vencimiento < CURRENT_DATE THEN 1
        WHEN l.fecha_vencimiento <= CURRENT_DATE + INTERVAL '120 days' THEN 2
        WHEN l.fecha_vencimiento <= CURRENT_DATE + INTERVAL '180 days' THEN 3
        WHEN l.fecha_vencimiento <= CURRENT_DATE + INTERVAL '365 days' THEN 4
        ELSE 5
    END,
    l.fecha_vencimiento ASC;

COMMENT ON VIEW r_alertas_vencimiento IS 
    'Reporte de alertas de vencimiento por lote (RF-075)';

-- ============================================================
-- VISTA: Reporte de Alertas de Stock de Seguridad (RF-076)
-- ============================================================
CREATE OR REPLACE VIEW r_alertas_stock AS
SELECT 
    a.id                     AS articulo_id,
    a.sku,
    a.nombre_tecnico,
    c.codigo                 AS categoria_codigo,
    c.nombre                 AS categoria_nombre,
    a.unidad_medida_base,
    a.stock_minimo,
    a.stock_maximo,
    a.stock_seguridad,
    COALESCE(SUM(sb.stock_actual), 0) AS stock_total,
    CASE 
        WHEN COALESCE(SUM(sb.stock_actual), 0) = 0                       THEN 'QUIEBRE'
        WHEN COALESCE(SUM(sb.stock_actual), 0) <= a.stock_minimo         THEN 'CRITICO'
        WHEN COALESCE(SUM(sb.stock_actual), 0) <= a.stock_minimo * 1.5   THEN 'BAJO'
        ELSE 'NORMAL'
    END AS estado_stock,
    (SELECT COUNT(DISTINCT sb2.bodega_id) FROM stock_por_bodega sb2 
     WHERE sb2.articulo_id = a.id AND sb2.activo = TRUE AND sb2.stock_actual > 0) AS bodegas_con_stock
FROM maestro_articulos a
JOIN categorias c ON c.id = a.categoria_id
LEFT JOIN stock_por_bodega sb ON sb.articulo_id = a.id AND sb.activo = TRUE
WHERE a.activo = TRUE
GROUP BY a.id, a.sku, a.nombre_tecnico, c.codigo, c.nombre, 
         a.unidad_medida_base, a.stock_minimo, a.stock_maximo, a.stock_seguridad
HAVING COALESCE(SUM(sb.stock_actual), 0) <= a.stock_minimo
ORDER BY 
    CASE 
        WHEN COALESCE(SUM(sb.stock_actual), 0) = 0 THEN 1
        ELSE 2
    END,
    COALESCE(SUM(sb.stock_actual), 0) ASC;

COMMENT ON VIEW r_alertas_stock IS 
    'Reporte de artículos con stock crítico o bajo (RF-076)';

-- ============================================================
-- VISTA: Reporte de Mermas y Bajas (RF-077)
-- ============================================================
CREATE OR REPLACE VIEW r_mermas_bajas AS
SELECT 
    k.id                     AS kardex_id,
    k.fecha_movimiento,
    a.sku,
    a.nombre_tecnico,
    c.codigo                 AS categoria_codigo,
    c.nombre                 AS categoria_nombre,
    b.codigo                 AS bodega_codigo,
    b.nombre                 AS bodega_nombre,
    k.tipo_movimiento,
    k.cantidad,
    k.valor_unitario,
    k.valor_total,
    k.observaciones,
    k.usuario_rut,
    k.usuario_nombre
FROM kardex k
JOIN maestro_articulos a ON a.id = k.articulo_id
JOIN categorias c ON c.id = a.categoria_id
JOIN bodegas b ON b.id = k.bodega_id
WHERE k.tipo_movimiento IN ('MERMA_VENCIMIENTO', 'MERMA_DAÑO', 'MERMA_OBSOLESCENCIA')
ORDER BY k.fecha_movimiento DESC;

COMMENT ON VIEW r_mermas_bajas IS 
    'Reporte de mermas y bajas justificadas (RF-077)';

-- ============================================================
-- VISTA: Reporte de Consumo por Centro de Costo (RF-078)
-- ============================================================
CREATE OR REPLACE VIEW r_consumo_centro_costo AS
SELECT 
    cc.id                    AS centro_costo_id,
    cc.codigo                AS centro_codigo,
    cc.nombre                AS centro_nombre,
    cc.tipo                  AS centro_tipo,
    a.id                     AS articulo_id,
    a.sku,
    a.nombre_tecnico,
    c.codigo                 AS categoria_codigo,
    c.nombre                 AS categoria_nombre,
    a.unidad_medida_base,
    SUM(dd.cantidad)         AS cantidad_total_despachada,
    SUM(dd.valor_total)      AS valor_total_despachado,
    DATE_TRUNC('month', d.fecha_despacho) AS mes_despacho,
    COUNT(DISTINCT d.id)     AS total_despachos
FROM despacho_detalle dd
JOIN despachos d ON d.id = dd.despacho_id
JOIN centros_costo cc ON cc.id = d.centro_costo_id
JOIN maestro_articulos a ON a.id = dd.articulo_id
JOIN categorias c ON c.id = a.categoria_id
WHERE d.estado = 'CONFIRMADO'
GROUP BY cc.id, cc.codigo, cc.nombre, cc.tipo,
         a.id, a.sku, a.nombre_tecnico, c.codigo, c.nombre, a.unidad_medida_base,
         DATE_TRUNC('month', d.fecha_despacho)
ORDER BY mes_despacho DESC, cc.nombre, a.nombre_tecnico;

COMMENT ON VIEW r_consumo_centro_costo IS 
    'Reporte de consumo mensual por centro de costo (RF-078)';

-- ============================================================
-- VISTA: Reporte de Kardex de Movimientos (RF-079)
-- ============================================================
CREATE OR REPLACE VIEW r_kardex AS
SELECT 
    k.id,
    k.fecha_movimiento,
    a.sku,
    a.nombre_tecnico,
    c.codigo                 AS categoria_codigo,
    b.codigo                 AS bodega_codigo,
    b.nombre                 AS bodega_nombre,
    l.numero_lote,
    k.tipo_movimiento,
    k.documento_tipo,
    k.documento_numero,
    k.documento_referencia,
    k.cantidad,
    k.stock_anterior,
    k.stock_nuevo,
    k.valor_unitario,
    k.valor_total,
    k.usuario_rut,
    k.usuario_nombre,
    k.observaciones
FROM kardex k
JOIN maestro_articulos a ON a.id = k.articulo_id
JOIN categorias c ON c.id = a.categoria_id
JOIN bodegas b ON b.id = k.bodega_id
LEFT JOIN lotes l ON l.id = k.lote_id
ORDER BY k.fecha_movimiento DESC;

COMMENT ON VIEW r_kardex IS 
    'Reporte de Kardex de movimientos (RF-079, RF-055, RF-056)';

-- ============================================================
-- VISTA: Reporte de Log de Auditoría (RF-080)
-- ============================================================
CREATE OR REPLACE VIEW r_log_auditoria AS
SELECT 
    la.id,
    la.timestamp,
    la.usuario_rut,
    la.usuario_nombre,
    la.accion,
    la.modulo,
    la.descripcion,
    la.valor_anterior,
    la.valor_nuevo,
    la.ip_address,
    la.user_agent
FROM log_auditoria la
ORDER BY la.timestamp DESC;

COMMENT ON VIEW r_log_auditoria IS 
    'Reporte del Log de Auditoría (RF-080, RNF-016)';

-- ============================================================
-- VISTA: Reporte de Valorización de Inventario (RF-081)
-- ============================================================
CREATE OR REPLACE VIEW r_valorizacion_inventario AS
SELECT 
    c.id                     AS categoria_id,
    c.codigo                 AS categoria_codigo,
    c.nombre                 AS categoria_nombre,
    COUNT(DISTINCT a.id)     AS total_articulos,
    COALESCE(SUM(sb.stock_actual), 0) AS stock_total,
    COALESCE(SUM(sb.stock_actual * COALESCE(a.precio_promedio_ponderado, 0)), 0) AS valor_total,
    AVG(a.precio_promedio_ponderado) AS precio_promedio
FROM categorias c
LEFT JOIN maestro_articulos a ON a.categoria_id = c.id AND a.activo = TRUE
LEFT JOIN stock_por_bodega sb ON sb.articulo_id = a.id AND sb.activo = TRUE
WHERE c.activa = TRUE
GROUP BY c.id, c.codigo, c.nombre
ORDER BY valor_total DESC;

COMMENT ON VIEW r_valorizacion_inventario IS 
    'Reporte de valorización del inventario por categoría (RF-081)';

-- ============================================================
-- VISTA: Reporte de Tasa de Merma Mensual (RF-082)
-- ============================================================
CREATE OR REPLACE VIEW r_tasa_merma AS
SELECT 
    DATE_TRUNC('month', k.fecha_movimiento) AS mes,
    COUNT(DISTINCT k.articulo_id)           AS articulos_afectados,
    SUM(k.cantidad)                         AS cantidad_total_merma,
    SUM(k.valor_total)                      AS valor_total_merma,
    (SELECT SUM(dd.cantidad) 
     FROM despacho_detalle dd 
     JOIN despachos d ON d.id = dd.despacho_id
     WHERE DATE_TRUNC('month', d.fecha_despacho) = DATE_TRUNC('month', k.fecha_movimiento)
       AND d.estado = 'CONFIRMADO') AS cantidad_total_despachada,
    CASE 
        WHEN (SELECT SUM(dd.cantidad) 
              FROM despacho_detalle dd 
              JOIN despachos d ON d.id = dd.despacho_id
              WHERE DATE_TRUNC('month', d.fecha_despacho) = DATE_TRUNC('month', k.fecha_movimiento)
                AND d.estado = 'CONFIRMADO') > 0
        THEN (SUM(k.cantidad) * 100.0) / 
             (SELECT SUM(dd.cantidad) 
              FROM despacho_detalle dd 
              JOIN despachos d ON d.id = dd.despacho_id
              WHERE DATE_TRUNC('month', d.fecha_despacho) = DATE_TRUNC('month', k.fecha_movimiento)
                AND d.estado = 'CONFIRMADO')
        ELSE 0
    END AS tasa_merma_porcentaje
FROM kardex k
WHERE k.tipo_movimiento IN ('MERMA_VENCIMIENTO', 'MERMA_DAÑO', 'MERMA_OBSOLESCENCIA')
GROUP BY DATE_TRUNC('month', k.fecha_movimiento)
ORDER BY mes DESC;

COMMENT ON VIEW r_tasa_merma IS 
    'Reporte de tasas de merma mensual (RF-082)';

-- ============================================================
-- VISTA: Reporte de Cumplimiento de OC (RF-084)
-- ============================================================
CREATE OR REPLACE VIEW r_cumplimiento_oc AS
SELECT 
    r.numero_orden_compra,
    r.rut_proveedor,
    r.nombre_proveedor,
    r.tipo_proveedor,
    r.fecha_emision,
    r.fecha_recepcion,
    COUNT(rd.id)                         AS total_lineas,
    SUM(rd.cantidad_recibida)            AS total_unidades_recibidas,
    SUM(rd.cantidad_pendiente)           AS total_unidades_pendientes,
    SUM(rd.monto_total)                  AS monto_total,
    CASE 
        WHEN SUM(rd.cantidad_pendiente) = 0 THEN 'COMPLETO'
        WHEN SUM(rd.cantidad_recibida) > 0 THEN 'PARCIAL'
        ELSE 'NO_ENTREGADO'
    END AS estado_cumplimiento
FROM recepciones r
JOIN recepcion_detalle rd ON rd.recepcion_id = r.id
WHERE r.numero_orden_compra IS NOT NULL
  AND r.estado = 'CONFIRMADA'
GROUP BY r.numero_orden_compra, r.rut_proveedor, r.nombre_proveedor, 
         r.tipo_proveedor, r.fecha_emision, r.fecha_recepcion
ORDER BY r.fecha_recepcion DESC;

COMMENT ON VIEW r_cumplimiento_oc IS 
    'Reporte de cumplimiento de órdenes de compra (RF-084)';

-- ============================================================
-- FUNCIÓN: Reconstitución de stock a una fecha pasada (RF-056)
-- ============================================================
CREATE OR REPLACE FUNCTION fn_stock_a_fecha(
    p_articulo_id BIGINT,
    p_bodega_id INTEGER,
    p_fecha TIMESTAMP
) RETURNS INTEGER AS $$
DECLARE
    v_stock INTEGER;
BEGIN
    SELECT stock_nuevo INTO v_stock
    FROM kardex
    WHERE articulo_id = p_articulo_id
      AND bodega_id = p_bodega_id
      AND fecha_movimiento <= p_fecha
    ORDER BY fecha_movimiento DESC, id DESC
    LIMIT 1;
    
    RETURN COALESCE(v_stock, 0);
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION fn_stock_a_fecha IS 
    'Reconstituye el stock de un artículo en una bodega a una fecha pasada (RF-056)';

-- ============================================================
-- FUNCIÓN: Resumen de KPIs para dashboard
-- ============================================================
CREATE OR REPLACE FUNCTION fn_kpis_dashboard()
RETURNS TABLE (
    kpi VARCHAR,
    valor BIGINT,
    detalle VARCHAR
) AS $$
BEGIN
    RETURN QUERY
    SELECT 'articulos_totales'::VARCHAR, 
           COUNT(*)::BIGINT,
           'Total de artículos en el maestro'::VARCHAR
    FROM maestro_articulos WHERE activo = TRUE;
    
    RETURN QUERY
    SELECT 'stock_critico'::VARCHAR,
           COUNT(*)::BIGINT,
           'Artículos con stock <= mínimo'::VARCHAR
    FROM (
        SELECT a.id, COALESCE(SUM(sb.stock_actual), 0) AS stock_total, a.stock_minimo
        FROM maestro_articulos a
        LEFT JOIN stock_por_bodega sb ON sb.articulo_id = a.id AND sb.activo = TRUE
        WHERE a.activo = TRUE
        GROUP BY a.id, a.stock_minimo
        HAVING COALESCE(SUM(sb.stock_actual), 0) <= a.stock_minimo
    ) t;
    
    RETURN QUERY
    SELECT 'quiebres_stock'::VARCHAR,
           COUNT(*)::BIGINT,
           'Artículos sin stock'::VARCHAR
    FROM (
        SELECT a.id
        FROM maestro_articulos a
        LEFT JOIN stock_por_bodega sb ON sb.articulo_id = a.id AND sb.activo = TRUE
        WHERE a.activo = TRUE
        GROUP BY a.id
        HAVING COALESCE(SUM(sb.stock_actual), 0) = 0
    ) t;
    
    RETURN QUERY
    SELECT 'lotes_bloqueados'::VARCHAR,
           COUNT(*)::BIGINT,
           'Lotes bloqueados por ISP'::VARCHAR
    FROM lotes WHERE bloqueado = TRUE AND activo = TRUE;
    
    RETURN QUERY
    SELECT 'pedidos_pendientes'::VARCHAR,
           COUNT(*)::BIGINT,
           'Pedidos internos por preparar'::VARCHAR
    FROM pedidos_internos WHERE estado IN ('PENDIENTE', 'EN_PREPARACION') AND activo = TRUE;
    
    RETURN QUERY
    SELECT 'bodegas_activas'::VARCHAR,
           COUNT(*)::BIGINT,
           'Bodegas operativas'::VARCHAR
    FROM bodegas WHERE activa = TRUE;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION fn_kpis_dashboard IS 
    'KPIs agregados para el dashboard';

-- ============================================================
-- VISTAS de KPIs (alternativa a la función)
-- ============================================================
CREATE OR REPLACE VIEW v_kpis_articulos AS
SELECT 
    COUNT(*) FILTER (WHERE activo = TRUE) AS articulos_activos,
    COUNT(*) FILTER (WHERE activo = FALSE) AS articulos_inactivos
FROM maestro_articulos;

CREATE OR REPLACE VIEW v_kpis_pedidos AS
SELECT 
    COUNT(*) FILTER (WHERE estado = 'PENDIENTE') AS pendientes,
    COUNT(*) FILTER (WHERE estado = 'EN_PREPARACION') AS en_preparacion,
    COUNT(*) FILTER (WHERE estado = 'DESPACHADO') AS despachados
FROM pedidos_internos
WHERE activo = TRUE;

CREATE OR REPLACE VIEW v_kpis_bloqueos AS
SELECT 
    COUNT(*) FILTER (WHERE estado = 'ACTIVO') AS activos,
    COUNT(*) FILTER (WHERE estado = 'LEVANTADO') AS levantados
FROM bloqueos_sanitarios
WHERE activo = TRUE;

COMMENT ON VIEW v_kpis_articulos IS 'KPIs de artículos para dashboard';
COMMENT ON VIEW v_kpis_pedidos IS 'KPIs de pedidos para dashboard';
COMMENT ON VIEW v_kpis_bloqueos IS 'KPIs de bloqueos para dashboard';