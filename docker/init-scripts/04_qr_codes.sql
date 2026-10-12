-- ============================================================
-- MIGRACIÓN: Códigos QR por artículo+bodega
-- Cada fila de stock_por_bodega tiene un código QR único.
-- ============================================================

-- Agregar columna codigo_qr
ALTER TABLE stock_por_bodega 
ADD COLUMN IF NOT EXISTS codigo_qr VARCHAR(100) UNIQUE;

-- Índice para búsquedas rápidas
CREATE INDEX IF NOT EXISTS idx_stock_codigo_qr ON stock_por_bodega(codigo_qr);

-- Rellenar códigos QR de los stocks que ya existen
UPDATE stock_por_bodega sb
SET codigo_qr = 'DSM-' 
    || (SELECT codigo FROM bodegas WHERE id = sb.bodega_id) 
    || '-' 
    || (SELECT sku FROM maestro_articulos WHERE id = sb.articulo_id) 
    || '-' 
    || UPPER(SUBSTRING(MD5(RANDOM()::TEXT || sb.id::TEXT), 1, 6))
WHERE codigo_qr IS NULL;

-- Comentario
COMMENT ON COLUMN stock_por_bodega.codigo_qr IS 
    'Código QR único para identificar artículo+bodega. Formato: DSM-{BODEGA}-{SKU}-{HASH6}';

-- Vista para consultar rápido
CREATE OR REPLACE VIEW v_qr_stock AS
SELECT 
    sb.id                AS stock_id,
    sb.codigo_qr,
    a.sku                AS articulo_sku,
    a.nombre_tecnico     AS articulo_nombre,
    a.unidad_medida_base,
    a.requiere_lote,
    a.requiere_vencimiento,
    b.codigo             AS bodega_codigo,
    b.nombre             AS bodega_nombre,
    b.dependencia,
    sb.stock_actual,
    sb.ubicacion_fisica,
    sb.estante,
    sb.nivel
FROM stock_por_bodega sb
JOIN maestro_articulos a ON a.id = sb.articulo_id
JOIN bodegas b ON b.id = sb.bodega_id
WHERE sb.activo = TRUE;

COMMENT ON VIEW v_qr_stock IS 
    'Vista de QRs con información completa para despacho';