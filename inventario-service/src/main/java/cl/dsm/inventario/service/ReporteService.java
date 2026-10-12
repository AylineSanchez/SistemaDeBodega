package cl.dsm.inventario.service;

import cl.dsm.inventario.dto.ReporteFiltroRequest;
import cl.dsm.inventario.dto.ReporteResponse;
import cl.dsm.inventario.dto.ReporteResponse.ResumenItem;
import cl.dsm.inventario.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Servicio de generación de reportes (RF-075 a RF-085).
 * 
 * Usa JdbcTemplate directamente para consultas complejas con agregaciones.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReporteService {
    
    private final JdbcTemplate jdbc;
    
    private static final DateTimeFormatter FMT_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FMT_FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    
    // ============================================================
    // DISPATCHER PRINCIPAL
    // ============================================================
    @Transactional(readOnly = true)
    public ReporteResponse generar(ReporteFiltroRequest filtro, String rutUsuario, String nombreUsuario) {
        
        if (filtro.getTipoReporte() == null || filtro.getTipoReporte().isBlank()) {
            throw new BusinessException("Debe especificar el tipo de reporte");
        }
        
        return switch (filtro.getTipoReporte()) {
            case "STOCK_CRITICO"         -> reporteStockCritico(filtro, rutUsuario, nombreUsuario);
            case "VENCIMIENTOS"          -> reporteVencimientos(filtro, rutUsuario, nombreUsuario);
            case "MERMAS_BAJAS"          -> reporteMermas(filtro, rutUsuario, nombreUsuario);
            case "CONSUMO_CENTRO_COSTO"  -> reporteConsumoCentro(filtro, rutUsuario, nombreUsuario);
            case "KARDEX"                -> reporteKardex(filtro, rutUsuario, nombreUsuario);
            case "LOG_AUDITORIA"         -> reporteLogAuditoria(filtro, rutUsuario, nombreUsuario);
            case "VALORIZACION"          -> reporteValorizacion(filtro, rutUsuario, nombreUsuario);
            case "CUMPLIMIENTO_OC"       -> reporteCumplimientoOC(filtro, rutUsuario, nombreUsuario);
            default -> throw new BusinessException("Tipo de reporte no soportado: " + filtro.getTipoReporte());
        };
    }
    
    // ============================================================
    // 1. REPORTE DE STOCK CRÍTICO (RF-076)
    // ============================================================
    private ReporteResponse reporteStockCritico(ReporteFiltroRequest f, String rut, String nombre) {
        
        StringBuilder sql = new StringBuilder("""
            SELECT 
                a.sku,
                a.nombre_tecnico AS producto,
                c.codigo AS categoria,
                a.unidad_medida_base AS unidad,
                a.stock_minimo,
                a.stock_maximo,
                COALESCE(SUM(sb.stock_actual), 0) AS stock_actual,
                CASE 
                    WHEN COALESCE(SUM(sb.stock_actual), 0) = 0 THEN 'QUIEBRE'
                    WHEN COALESCE(SUM(sb.stock_actual), 0) <= a.stock_minimo THEN 'CRITICO'
                    ELSE 'BAJO'
                END AS estado
            FROM maestro_articulos a
            JOIN categorias c ON c.id = a.categoria_id
            LEFT JOIN stock_por_bodega sb ON sb.articulo_id = a.id AND sb.activo = TRUE
            WHERE a.activo = TRUE
            """);
        
        List<Object> params = new ArrayList<>();
        
        if (f.getCategoriaId() != null) {
            sql.append(" AND a.categoria_id = ?");
            params.add(f.getCategoriaId());
        }
        
        sql.append("""
            GROUP BY a.id, a.sku, a.nombre_tecnico, c.codigo, 
                     a.unidad_medida_base, a.stock_minimo, a.stock_maximo
            HAVING COALESCE(SUM(sb.stock_actual), 0) <= a.stock_minimo
            ORDER BY 
                CASE 
                    WHEN COALESCE(SUM(sb.stock_actual), 0) = 0 THEN 1
                    ELSE 2
                END,
                COALESCE(SUM(sb.stock_actual), 0) ASC
            """);
        
        List<Map<String, Object>> filas = jdbc.queryForList(sql.toString(), params.toArray());
        
        // Calcular resumen
        long criticos = filas.stream().filter(m -> "CRITICO".equals(m.get("estado"))).count();
        long quiebres = filas.stream().filter(m -> "QUIEBRE".equals(m.get("estado"))).count();
        long bajos = filas.stream().filter(m -> "BAJO".equals(m.get("estado"))).count();
        
        return ReporteResponse.builder()
                .tipoReporte("STOCK_CRITICO")
                .titulo("Reporte de Stock Crítico")
                .subtitulo("Artículos con stock por debajo del mínimo de seguridad")
                .filtrosAplicados(construirFiltrosDescripcion(f))
                .generadoPor(nombre)
                .fechaGeneracion(LocalDateTime.now().format(FMT_FECHA_HORA))
                .columnas(List.of("SKU", "Producto", "Categoría", "Unidad", "Stock Actual", "Mínimo", "Máximo", "Estado"))
                .filas(filas)
                .resumen(List.of(
                        ResumenItem.builder().label("Total productos").valor(filas.size()).build(),
                        ResumenItem.builder().label("Quiebres").valor(quiebres).build(),
                        ResumenItem.builder().label("Críticos").valor(criticos).build(),
                        ResumenItem.builder().label("Bajos").valor(bajos).build()
                ))
                .totalRegistros(filas.size())
                .build();
    }
    
    // ============================================================
    // 2. REPORTE DE VENCIMIENTOS (RF-075)
    // ============================================================
    private ReporteResponse reporteVencimientos(ReporteFiltroRequest f, String rut, String nombre) {
        
        StringBuilder sql = new StringBuilder("""
            SELECT 
                a.sku,
                a.nombre_tecnico AS producto,
                c.codigo AS categoria,
                l.numero_lote,
                l.fecha_vencimiento,
                (l.fecha_vencimiento - CURRENT_DATE) AS dias_para_vencer,
                CASE 
                    WHEN l.fecha_vencimiento < CURRENT_DATE THEN 'VENCIDO'
                    WHEN l.fecha_vencimiento <= CURRENT_DATE + INTERVAL '120 days' THEN 'CRITICO'
                    WHEN l.fecha_vencimiento <= CURRENT_DATE + INTERVAL '180 days' THEN 'PROXIMO'
                    WHEN l.fecha_vencimiento <= CURRENT_DATE + INTERVAL '365 days' THEN 'VIGILAR'
                    ELSE 'OK'
                END AS estado,
                COALESCE(SUM(sl.cantidad), 0) AS stock_total,
                l.bloqueado
            FROM lotes l
            JOIN maestro_articulos a ON a.id = l.articulo_id
            JOIN categorias c ON c.id = a.categoria_id
            LEFT JOIN stock_lotes sl ON sl.lote_id = l.id
            WHERE l.activo = TRUE 
              AND l.fecha_vencimiento IS NOT NULL
            """);
        
        List<Object> params = new ArrayList<>();
        
        if (f.getCategoriaId() != null) {
            sql.append(" AND a.categoria_id = ?");
            params.add(f.getCategoriaId());
        }
        
        if (f.getBodegaId() != null) {
            sql.append(" AND EXISTS (SELECT 1 FROM stock_lotes sl2 JOIN stock_por_bodega sb2 ON sb2.id = sl2.stock_id WHERE sl2.lote_id = l.id AND sb2.bodega_id = ?)");
            params.add(f.getBodegaId());
        }
        
        sql.append("""
            GROUP BY a.id, a.sku, a.nombre_tecnico, c.codigo, 
                     l.id, l.numero_lote, l.fecha_vencimiento, l.bloqueado
            ORDER BY l.fecha_vencimiento ASC
            """);
        
        List<Map<String, Object>> filas = jdbc.queryForList(sql.toString(), params.toArray());
        
        long vencidos = filas.stream().filter(m -> "VENCIDO".equals(m.get("estado"))).count();
        long criticos = filas.stream().filter(m -> "CRITICO".equals(m.get("estado"))).count();
        long proximos = filas.stream().filter(m -> "PROXIMO".equals(m.get("estado"))).count();
        
        return ReporteResponse.builder()
                .tipoReporte("VENCIMIENTOS")
                .titulo("Reporte de Alertas de Vencimiento")
                .subtitulo("Lotes ordenados por fecha de expiración (FEFO)")
                .filtrosAplicados(construirFiltrosDescripcion(f))
                .generadoPor(nombre)
                .fechaGeneracion(LocalDateTime.now().format(FMT_FECHA_HORA))
                .columnas(List.of("SKU", "Producto", "Categoría", "Lote", "Vencimiento", "Días", "Stock", "Estado"))
                .filas(filas)
                .resumen(List.of(
                        ResumenItem.builder().label("Total lotes").valor(filas.size()).build(),
                        ResumenItem.builder().label("Vencidos").valor(vencidos).build(),
                        ResumenItem.builder().label("Críticos (120d)").valor(criticos).build(),
                        ResumenItem.builder().label("Próximos (180d)").valor(proximos).build()
                ))
                .totalRegistros(filas.size())
                .build();
    }
    
    // ============================================================
    // 3. REPORTE DE MERMAS Y BAJAS (RF-077)
    // ============================================================
    private ReporteResponse reporteMermas(ReporteFiltroRequest f, String rut, String nombre) {
        
        StringBuilder sql = new StringBuilder("""
            SELECT 
                k.fecha_movimiento,
                a.sku,
                a.nombre_tecnico AS producto,
                c.codigo AS categoria,
                b.codigo AS bodega,
                k.tipo_movimiento,
                k.cantidad,
                k.valor_total,
                k.observaciones,
                k.usuario_nombre
            FROM kardex k
            JOIN maestro_articulos a ON a.id = k.articulo_id
            JOIN categorias c ON c.id = a.categoria_id
            JOIN bodegas b ON b.id = k.bodega_id
            WHERE k.tipo_movimiento IN ('MERMA_VENCIMIENTO', 'MERMA_DAÑO', 'MERMA_OBSOLESCENCIA')
            """);
        
        List<Object> params = new ArrayList<>();
        
        if (f.getFechaHoraInicio() != null) {
            sql.append(" AND k.fecha_movimiento >= ?");
            params.add(f.getFechaHoraInicio());
        }
        if (f.getFechaHoraFin() != null) {
            sql.append(" AND k.fecha_movimiento <= ?");
            params.add(f.getFechaHoraFin());
        }
        if (f.getBodegaId() != null) {
            sql.append(" AND k.bodega_id = ?");
            params.add(f.getBodegaId());
        }
        if (f.getCategoriaId() != null) {
            sql.append(" AND a.categoria_id = ?");
            params.add(f.getCategoriaId());
        }
        
        sql.append(" ORDER BY k.fecha_movimiento DESC");
        
        List<Map<String, Object>> filas = jdbc.queryForList(sql.toString(), params.toArray());
        
        BigDecimal valorTotal = filas.stream()
                .map(m -> m.get("valor_total") != null ? new BigDecimal(m.get("valor_total").toString()) : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        return ReporteResponse.builder()
                .tipoReporte("MERMAS_BAJAS")
                .titulo("Reporte de Mermas y Bajas Justificadas")
                .subtitulo("Productos vencidos o dañados dados de baja")
                .filtrosAplicados(construirFiltrosDescripcion(f))
                .generadoPor(nombre)
                .fechaGeneracion(LocalDateTime.now().format(FMT_FECHA_HORA))
                .columnas(List.of("Fecha", "SKU", "Producto", "Categoría", "Bodega", "Tipo", "Cantidad", "Valor", "Usuario"))
                .filas(filas)
                .resumen(List.of(
                        ResumenItem.builder().label("Total mermas").valor(filas.size()).build(),
                        ResumenItem.builder().label("Valor total").valor("$" + valorTotal.toPlainString()).build()
                ))
                .totalRegistros(filas.size())
                .build();
    }
    
    // ============================================================
    // 4. REPORTE DE CONSUMO POR CENTRO DE COSTO (RF-078)
    // ============================================================
    private ReporteResponse reporteConsumoCentro(ReporteFiltroRequest f, String rut, String nombre) {
        
        StringBuilder sql = new StringBuilder("""
            SELECT 
                cc.codigo AS centro_codigo,
                cc.nombre AS centro_nombre,
                a.sku,
                a.nombre_tecnico AS producto,
                c.codigo AS categoria,
                a.unidad_medida_base AS unidad,
                SUM(dd.cantidad) AS cantidad_total,
                SUM(dd.valor_total) AS valor_total,
                COUNT(DISTINCT d.id) AS total_despachos
            FROM despacho_detalle dd
            JOIN despachos d ON d.id = dd.despacho_id
            JOIN centros_costo cc ON cc.id = d.centro_costo_id
            JOIN maestro_articulos a ON a.id = dd.articulo_id
            JOIN categorias c ON c.id = a.categoria_id
            WHERE d.estado = 'CONFIRMADO'
            """);
        
        List<Object> params = new ArrayList<>();
        
        if (f.getFechaHoraInicio() != null) {
            sql.append(" AND d.fecha_despacho >= ?");
            params.add(f.getFechaHoraInicio());
        }
        if (f.getFechaHoraFin() != null) {
            sql.append(" AND d.fecha_despacho <= ?");
            params.add(f.getFechaHoraFin());
        }
        if (f.getCentroCostoId() != null) {
            sql.append(" AND d.centro_costo_id = ?");
            params.add(f.getCentroCostoId());
        }
        if (f.getCategoriaId() != null) {
            sql.append(" AND a.categoria_id = ?");
            params.add(f.getCategoriaId());
        }
        
        sql.append("""
            GROUP BY cc.id, cc.codigo, cc.nombre, a.id, a.sku, 
                     a.nombre_tecnico, c.codigo, a.unidad_medida_base
            ORDER BY cc.nombre, a.nombre_tecnico
            """);
        
        List<Map<String, Object>> filas = jdbc.queryForList(sql.toString(), params.toArray());
        
        BigDecimal valorTotal = filas.stream()
                .map(m -> m.get("valor_total") != null ? new BigDecimal(m.get("valor_total").toString()) : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        return ReporteResponse.builder()
                .tipoReporte("CONSUMO_CENTRO_COSTO")
                .titulo("Reporte de Consumo por Centro de Costo")
                .subtitulo("Insumos entregados a cada servicio del DSM")
                .filtrosAplicados(construirFiltrosDescripcion(f))
                .generadoPor(nombre)
                .fechaGeneracion(LocalDateTime.now().format(FMT_FECHA_HORA))
                .columnas(List.of("Centro", "Código Centro", "SKU", "Producto", "Categoría", "Unidad", "Cantidad", "Valor Total", "Despachos"))
                .filas(filas)
                .resumen(List.of(
                        ResumenItem.builder().label("Total líneas").valor(filas.size()).build(),
                        ResumenItem.builder().label("Valor total").valor("$" + valorTotal.toPlainString()).build()
                ))
                .totalRegistros(filas.size())
                .build();
    }
    
    // ============================================================
    // 5. REPORTE KARDEX (RF-079)
    // ============================================================
    private ReporteResponse reporteKardex(ReporteFiltroRequest f, String rut, String nombre) {
        
        StringBuilder sql = new StringBuilder("""
            SELECT 
                k.fecha_movimiento,
                a.sku,
                a.nombre_tecnico AS producto,
                c.codigo AS categoria,
                b.codigo AS bodega,
                l.numero_lote AS lote,
                k.tipo_movimiento,
                k.documento_numero,
                k.cantidad,
                k.stock_anterior,
                k.stock_nuevo,
                k.usuario_nombre
            FROM kardex k
            JOIN maestro_articulos a ON a.id = k.articulo_id
            JOIN categorias c ON c.id = a.categoria_id
            JOIN bodegas b ON b.id = k.bodega_id
            LEFT JOIN lotes l ON l.id = k.lote_id
            WHERE 1=1
            """);
        
        List<Object> params = new ArrayList<>();
        
        if (f.getArticuloId() != null) {
            sql.append(" AND k.articulo_id = ?");
            params.add(f.getArticuloId());
        }
        if (f.getBodegaId() != null) {
            sql.append(" AND k.bodega_id = ?");
            params.add(f.getBodegaId());
        }
        if (f.getTipoMovimiento() != null && !f.getTipoMovimiento().isBlank()) {
            sql.append(" AND k.tipo_movimiento = ?");
            params.add(f.getTipoMovimiento());
        }
        if (f.getFechaHoraInicio() != null) {
            sql.append(" AND k.fecha_movimiento >= ?");
            params.add(f.getFechaHoraInicio());
        }
        if (f.getFechaHoraFin() != null) {
            sql.append(" AND k.fecha_movimiento <= ?");
            params.add(f.getFechaHoraFin());
        }
        
        sql.append(" ORDER BY k.fecha_movimiento DESC LIMIT 1000");
        
        List<Map<String, Object>> filas = jdbc.queryForList(sql.toString(), params.toArray());
        
        return ReporteResponse.builder()
                .tipoReporte("KARDEX")
                .titulo("Reporte de Kardex de Movimientos")
                .subtitulo("Historial cronológico de movimientos de inventario")
                .filtrosAplicados(construirFiltrosDescripcion(f))
                .generadoPor(nombre)
                .fechaGeneracion(LocalDateTime.now().format(FMT_FECHA_HORA))
                .columnas(List.of("Fecha", "SKU", "Producto", "Categoría", "Bodega", "Lote", "Tipo", "Documento", "Cantidad", "Stock Ant.", "Stock Nuevo", "Usuario"))
                .filas(filas)
                .resumen(List.of(
                        ResumenItem.builder().label("Total movimientos").valor(filas.size()).build()
                ))
                .totalRegistros(filas.size())
                .build();
    }
    
    // ============================================================
    // 6. REPORTE LOG DE AUDITORÍA (RF-080)
    // ============================================================
    private ReporteResponse reporteLogAuditoria(ReporteFiltroRequest f, String rut, String nombre) {
        
        StringBuilder sql = new StringBuilder("""
            SELECT 
                la.timestamp,
                la.usuario_rut,
                la.usuario_nombre,
                la.accion,
                la.modulo,
                la.descripcion,
                la.ip_address
            FROM log_auditoria la
            WHERE 1=1
            """);
        
        List<Object> params = new ArrayList<>();
        
        if (f.getQ() != null && !f.getQ().isBlank()) {
            sql.append(" AND (LOWER(la.usuario_nombre) LIKE LOWER(?) OR LOWER(la.accion) LIKE LOWER(?) OR LOWER(la.descripcion) LIKE LOWER(?))");
            String like = "%" + f.getQ() + "%";
            params.add(like);
            params.add(like);
            params.add(like);
        }
        if (f.getFechaHoraInicio() != null) {
            sql.append(" AND la.timestamp >= ?");
            params.add(f.getFechaHoraInicio());
        }
        if (f.getFechaHoraFin() != null) {
            sql.append(" AND la.timestamp <= ?");
            params.add(f.getFechaHoraFin());
        }
        
        sql.append(" ORDER BY la.timestamp DESC LIMIT 500");
        
        List<Map<String, Object>> filas = jdbc.queryForList(sql.toString(), params.toArray());
        
        return ReporteResponse.builder()
                .tipoReporte("LOG_AUDITORIA")
                .titulo("Reporte de Log de Auditoría (Audit Trail)")
                .subtitulo("Registro inalterable de acciones de usuarios")
                .filtrosAplicados(construirFiltrosDescripcion(f))
                .generadoPor(nombre)
                .fechaGeneracion(LocalDateTime.now().format(FMT_FECHA_HORA))
                .columnas(List.of("Fecha", "RUT", "Usuario", "Acción", "Módulo", "Descripción", "IP"))
                .filas(filas)
                .resumen(List.of(
                        ResumenItem.builder().label("Total registros").valor(filas.size()).build()
                ))
                .totalRegistros(filas.size())
                .build();
    }
    
    // ============================================================
    // 7. REPORTE DE VALORIZACIÓN (RF-081)
    // ============================================================
    private ReporteResponse reporteValorizacion(ReporteFiltroRequest f, String rut, String nombre) {
        
        String sql = """
            SELECT 
                c.codigo AS categoria_codigo,
                c.nombre AS categoria_nombre,
                COUNT(DISTINCT a.id) AS total_articulos,
                COALESCE(SUM(sb.stock_actual), 0) AS stock_total,
                COALESCE(SUM(sb.stock_actual * COALESCE(a.precio_promedio_ponderado, 0)), 0) AS valor_total,
                COALESCE(AVG(a.precio_promedio_ponderado), 0) AS precio_promedio
            FROM categorias c
            LEFT JOIN maestro_articulos a ON a.categoria_id = c.id AND a.activo = TRUE
            LEFT JOIN stock_por_bodega sb ON sb.articulo_id = a.id AND sb.activo = TRUE
            WHERE c.activa = TRUE
            GROUP BY c.id, c.codigo, c.nombre
            ORDER BY valor_total DESC
            """;
        
        List<Map<String, Object>> filas = jdbc.queryForList(sql);
        
        BigDecimal valorTotal = filas.stream()
                .map(m -> m.get("valor_total") != null ? new BigDecimal(m.get("valor_total").toString()) : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        return ReporteResponse.builder()
                .tipoReporte("VALORIZACION")
                .titulo("Reporte de Valorización de Inventario")
                .subtitulo("Valorización por categoría (PPP - Precio Promedio Ponderado)")
                .filtrosAplicados(construirFiltrosDescripcion(f))
                .generadoPor(nombre)
                .fechaGeneracion(LocalDateTime.now().format(FMT_FECHA_HORA))
                .columnas(List.of("Código", "Categoría", "Artículos", "Stock Total", "Valor Total", "Precio Promedio"))
                .filas(filas)
                .resumen(List.of(
                        ResumenItem.builder().label("Total categorías").valor(filas.size()).build(),
                        ResumenItem.builder().label("Valor total inventario").valor("$" + valorTotal.toPlainString()).build()
                ))
                .totalRegistros(filas.size())
                .build();
    }
    
    // ============================================================
    // 8. REPORTE DE CUMPLIMIENTO DE OC (RF-083)
    // ============================================================
    private ReporteResponse reporteCumplimientoOC(ReporteFiltroRequest f, String rut, String nombre) {
        
        StringBuilder sql = new StringBuilder("""
            SELECT 
                r.numero_orden_compra,
                r.rut_proveedor,
                r.nombre_proveedor,
                r.tipo_proveedor,
                r.fecha_emision,
                r.fecha_recepcion,
                r.estado,
                COUNT(rd.id) AS total_lineas,
                SUM(rd.cantidad_recibida) AS unidades_recibidas,
                SUM(rd.cantidad_pendiente) AS unidades_pendientes,
                SUM(rd.monto_total) AS monto_total
            FROM recepciones r
            JOIN recepcion_detalle rd ON rd.recepcion_id = r.id
            WHERE r.numero_orden_compra IS NOT NULL
            """);
        
        List<Object> params = new ArrayList<>();
        
        if (f.getRutProveedor() != null && !f.getRutProveedor().isBlank()) {
            sql.append(" AND r.rut_proveedor = ?");
            params.add(f.getRutProveedor());
        }
        if (f.getFechaInicio() != null) {
            sql.append(" AND r.fecha_recepcion >= ?");
            params.add(f.getFechaInicio());
        }
        if (f.getFechaFin() != null) {
            sql.append(" AND r.fecha_recepcion <= ?");
            params.add(f.getFechaFin());
        }
        
        sql.append("""
            GROUP BY r.id, r.numero_orden_compra, r.rut_proveedor, r.nombre_proveedor, 
                     r.tipo_proveedor, r.fecha_emision, r.fecha_recepcion, r.estado
            ORDER BY r.fecha_recepcion DESC
            """);
        
        List<Map<String, Object>> filas = jdbc.queryForList(sql.toString(), params.toArray());
        
        return ReporteResponse.builder()
                .tipoReporte("CUMPLIMIENTO_OC")
                .titulo("Reporte de Cumplimiento de Órdenes de Compra")
                .subtitulo("Cruce de OC Mercado Público vs ingreso físico a bodega")
                .filtrosAplicados(construirFiltrosDescripcion(f))
                .generadoPor(nombre)
                .fechaGeneracion(LocalDateTime.now().format(FMT_FECHA_HORA))
                .columnas(List.of("N° OC", "RUT Proveedor", "Proveedor", "Tipo", "Fecha Emisión", "Fecha Recepción", "Estado", "Líneas", "Recibido", "Pendiente", "Monto"))
                .filas(filas)
                .resumen(List.of(
                        ResumenItem.builder().label("Total OCs").valor(filas.size()).build()
                ))
                .totalRegistros(filas.size())
                .build();
    }
    
    // ============================================================
    // HELPERS
    // ============================================================
    
    private String construirFiltrosDescripcion(ReporteFiltroRequest f) {
        List<String> filtros = new ArrayList<>();
        
        if (f.getFechaInicio() != null) filtros.add("Desde: " + f.getFechaInicio().format(FMT_FECHA));
        if (f.getFechaFin() != null) filtros.add("Hasta: " + f.getFechaFin().format(FMT_FECHA));
        if (f.getFechaHoraInicio() != null) filtros.add("Desde: " + f.getFechaHoraInicio().format(FMT_FECHA_HORA));
        if (f.getFechaHoraFin() != null) filtros.add("Hasta: " + f.getFechaHoraFin().format(FMT_FECHA_HORA));
        if (f.getBodegaId() != null) filtros.add("Bodega ID: " + f.getBodegaId());
        if (f.getCategoriaId() != null) filtros.add("Categoría ID: " + f.getCategoriaId());
        if (f.getCentroCostoId() != null) filtros.add("Centro de costo ID: " + f.getCentroCostoId());
        if (f.getQ() != null && !f.getQ().isBlank()) filtros.add("Búsqueda: " + f.getQ());
        
        return filtros.isEmpty() ? "Sin filtros adicionales" : String.join(" | ", filtros);
    }
}