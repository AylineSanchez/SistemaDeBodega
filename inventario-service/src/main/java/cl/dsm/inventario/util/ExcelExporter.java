package cl.dsm.inventario.util;

import cl.dsm.inventario.dto.ReporteResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.*;
import org.apache.poi.xssf.usermodel.extensions.XSSFCellBorder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Utilidad para exportar reportes a Excel (.xlsx).
 * 
 * Usa Apache POI. Genera un archivo con:
 *   - Título del reporte
 *   - Subtítulo y filtros aplicados
 *   - Fecha de generación y usuario
 *   - Encabezados con estilo DSM (naranja)
 *   - Datos formateados
 *   - Resumen al pie
 * 
 * @see cl.dsm.inventario.dto.ReporteResponse
 */
@Slf4j
public class ExcelExporter {
    
    // ============================================================
    // COLORES (formato RGB hexadecimal)
    // ============================================================
    private static final byte[] COLOR_HEADER_BG    = hexToRgb("D35400"); // Naranja oscuro DSM
    private static final byte[] COLOR_HEADER_TEXT  = hexToRgb("FFFFFF"); // Blanco
    private static final byte[] COLOR_TITLE_TEXT   = hexToRgb("D35400"); // Naranja oscuro
    private static final byte[] COLOR_SUBTITLE     = hexToRgb("7F8C8D"); // Gris
    private static final byte[] COLOR_META_TEXT    = hexToRgb("95A5A6"); // Gris claro
    private static final byte[] COLOR_RESUMEN_BG   = hexToRgb("F0EBE0"); // Beige claro
    private static final byte[] COLOR_RESUMEN_TEXT = hexToRgb("2C3E50"); // Azul oscuro
    
    private static final DateTimeFormatter FMT_FECHA_HORA = 
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    
    /**
     * Genera un archivo Excel (.xlsx) a partir de un ReporteResponse.
     */
    public static byte[] exportar(ReporteResponse reporte) throws IOException {
        
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            
            String nombreHoja = limpiarNombreHoja(reporte.getTitulo());
            Sheet sheet = workbook.createSheet(nombreHoja);
            
            // Estilos
            XSSFCellStyle tituloStyle    = crearTituloStyle(workbook);
            XSSFCellStyle subtituloStyle = crearSubtituloStyle(workbook);
            XSSFCellStyle metaStyle      = crearMetaStyle(workbook);
            XSSFCellStyle headerStyle    = crearHeaderStyle(workbook);
            XSSFCellStyle celdaStyle     = crearCeldaStyle(workbook);
            XSSFCellStyle celdaNumStyle  = crearCeldaNumeroStyle(workbook);
            XSSFCellStyle celdaMoneyStyle = crearCeldaMoneyStyle(workbook);
            XSSFCellStyle resumenLabelStyle = crearResumenLabelStyle(workbook);
            
            int rowIdx = 0;
            
            // ============================================================
            // 1. TÍTULO
            // ============================================================
            Row tituloRow = sheet.createRow(rowIdx++);
            tituloRow.setHeightInPoints(28);
            Cell tituloCell = tituloRow.createCell(0);
            tituloCell.setCellValue(reporte.getTitulo());
            tituloCell.setCellStyle(tituloStyle);
            
            // ============================================================
            // 2. SUBTÍTULO
            // ============================================================
            if (reporte.getSubtitulo() != null && !reporte.getSubtitulo().isBlank()) {
                Row subtituloRow = sheet.createRow(rowIdx++);
                Cell subtituloCell = subtituloRow.createCell(0);
                subtituloCell.setCellValue(reporte.getSubtitulo());
                subtituloCell.setCellStyle(subtituloStyle);
            }
            
            // ============================================================
            // 3. FILTROS APLICADOS
            // ============================================================
            if (reporte.getFiltrosAplicados() != null && !reporte.getFiltrosAplicados().isBlank()) {
                Row filtrosRow = sheet.createRow(rowIdx++);
                Cell filtrosCell = filtrosRow.createCell(0);
                filtrosCell.setCellValue("Filtros: " + reporte.getFiltrosAplicados());
                filtrosCell.setCellStyle(metaStyle);
            }
            
            // ============================================================
            // 4. FECHA Y USUARIO
            // ============================================================
            Row metaRow = sheet.createRow(rowIdx++);
            Cell metaCell = metaRow.createCell(0);
            String metaTexto = String.format(
                    "Generado por: %s    |    Fecha: %s    |    Total registros: %d",
                    reporte.getGeneradoPor() != null ? reporte.getGeneradoPor() : "Sistema",
                    reporte.getFechaGeneracion() != null 
                        ? reporte.getFechaGeneracion() 
                        : LocalDateTime.now().format(FMT_FECHA_HORA),
                    reporte.getTotalRegistros() != null ? reporte.getTotalRegistros() : 0
            );
            metaCell.setCellValue(metaTexto);
            metaCell.setCellStyle(metaStyle);
            
            // ============================================================
            // 5. LÍNEA VACÍA
            // ============================================================
            rowIdx++;
            
            // ============================================================
            // 6. ENCABEZADOS DE LA TABLA
            // ============================================================
            Row headerRow = sheet.createRow(rowIdx++);
            headerRow.setHeightInPoints(22);
            
            List<String> columnas = reporte.getColumnas();
            if (columnas == null || columnas.isEmpty()) {
                throw new IllegalStateException("El reporte no tiene columnas definidas");
            }
            
            for (int i = 0; i < columnas.size(); i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(columnas.get(i));
                cell.setCellStyle(headerStyle);
            }
            
            // ============================================================
            // 7. DATOS
            // ============================================================
            List<Map<String, Object>> filas = reporte.getFilas();
            if (filas != null && !filas.isEmpty()) {
                for (Map<String, Object> fila : filas) {
                    Row dataRow = sheet.createRow(rowIdx++);
                    int colIdx = 0;
                    
                    for (Object valor : fila.values()) {
                        Cell cell = dataRow.createCell(colIdx++);
                        escribirValor(cell, valor, celdaStyle, celdaNumStyle, celdaMoneyStyle);
                    }
                }
            } else {
                Row emptyRow = sheet.createRow(rowIdx++);
                Cell emptyCell = emptyRow.createCell(0);
                emptyCell.setCellValue("(No hay datos para mostrar)");
                emptyCell.setCellStyle(metaStyle);
            }
            
            // ============================================================
            // 8. RESUMEN
            // ============================================================
            if (reporte.getResumen() != null && !reporte.getResumen().isEmpty()) {
                rowIdx++;
                
                Row resumenTituloRow = sheet.createRow(rowIdx++);
                Cell resumenTituloCell = resumenTituloRow.createCell(0);
                resumenTituloCell.setCellValue("RESUMEN");
                resumenTituloCell.setCellStyle(resumenLabelStyle);
                
                for (ReporteResponse.ResumenItem item : reporte.getResumen()) {
                    Row resumenRow = sheet.createRow(rowIdx++);
                    
                    Cell labelCell = resumenRow.createCell(0);
                    labelCell.setCellValue(item.getLabel() + ":");
                    labelCell.setCellStyle(resumenLabelStyle);
                    
                    Cell valorCell = resumenRow.createCell(1);
                    escribirValor(valorCell, item.getValor(), 
                                  celdaStyle, celdaNumStyle, celdaMoneyStyle);
                }
            }
            
            // ============================================================
            // 9. AUTO-AJUSTAR COLUMNAS
            // ============================================================
            if (!columnas.isEmpty()) {
                for (int i = 0; i < columnas.size(); i++) {
                    sheet.autoSizeColumn(i);
                    int anchoActual = sheet.getColumnWidth(i);
                    sheet.setColumnWidth(i, Math.min(anchoActual + 512, 15000));
                }
            }
            
            // ============================================================
            // 10. FREEZE PANE
            // ============================================================
            sheet.createFreezePane(0, 7);
            
            workbook.write(baos);
            
            log.info("Excel generado: {} filas x {} columnas ({} bytes)",
                    filas != null ? filas.size() : 0, columnas.size(), baos.size());
            
            return baos.toByteArray();
        }
    }
    
    // ============================================================
    // ESCRITURA DE VALORES
    // ============================================================
    
    private static void escribirValor(Cell cell, Object valor,
                                       CellStyle celdaStyle,
                                       CellStyle celdaNumStyle,
                                       CellStyle celdaMoneyStyle) {
        if (valor == null) {
            cell.setCellValue("");
            cell.setCellStyle(celdaStyle);
            return;
        }
        
        if (valor instanceof BigDecimal) {
            cell.setCellValue(((BigDecimal) valor).doubleValue());
            cell.setCellStyle(celdaMoneyStyle);
        } else if (valor instanceof Integer || valor instanceof Long) {
            cell.setCellValue(((Number) valor).doubleValue());
            cell.setCellStyle(celdaNumStyle);
        } else if (valor instanceof Double || valor instanceof Float) {
            cell.setCellValue(((Number) valor).doubleValue());
            cell.setCellStyle(celdaNumStyle);
        } else if (valor instanceof Boolean) {
            cell.setCellValue((Boolean) valor ? "Sí" : "No");
            cell.setCellStyle(celdaStyle);
        } else {
            cell.setCellValue(valor.toString());
            cell.setCellStyle(celdaStyle);
        }
    }
    
    // ============================================================
    // ESTILOS (usan XSSFCellStyle y XSSFFont)
    // ============================================================
    
    private static XSSFCellStyle crearTituloStyle(XSSFWorkbook wb) {
        XSSFCellStyle style = wb.createCellStyle();
        XSSFFont font = wb.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 16);
        font.setColor(new XSSFColor(COLOR_TITLE_TEXT, null));
        style.setFont(font);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }
    
    private static XSSFCellStyle crearSubtituloStyle(XSSFWorkbook wb) {
        XSSFCellStyle style = wb.createCellStyle();
        XSSFFont font = wb.createFont();
        font.setItalic(true);
        font.setFontHeightInPoints((short) 12);
        font.setColor(new XSSFColor(COLOR_SUBTITLE, null));
        style.setFont(font);
        return style;
    }
    
    private static XSSFCellStyle crearMetaStyle(XSSFWorkbook wb) {
        XSSFCellStyle style = wb.createCellStyle();
        XSSFFont font = wb.createFont();
        font.setFontHeightInPoints((short) 10);
        font.setColor(new XSSFColor(COLOR_META_TEXT, null));
        style.setFont(font);
        return style;
    }
    
    private static XSSFCellStyle crearHeaderStyle(XSSFWorkbook wb) {
        XSSFCellStyle style = wb.createCellStyle();
        XSSFFont font = wb.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 11);
        font.setColor(new XSSFColor(COLOR_HEADER_TEXT, null));
        style.setFont(font);
        style.setFillForegroundColor(new XSSFColor(COLOR_HEADER_BG, null));
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        aplicarBordes(style);
        return style;
    }
    
    private static XSSFCellStyle crearCeldaStyle(XSSFWorkbook wb) {
        XSSFCellStyle style = wb.createCellStyle();
        XSSFFont font = wb.createFont();
        font.setFontHeightInPoints((short) 10);
        style.setFont(font);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        aplicarBordes(style);
        return style;
    }
    
    private static XSSFCellStyle crearCeldaNumeroStyle(XSSFWorkbook wb) {
        XSSFCellStyle style = crearCeldaStyle(wb);
        style.setAlignment(HorizontalAlignment.RIGHT);
        return style;
    }
    
    private static XSSFCellStyle crearCeldaMoneyStyle(XSSFWorkbook wb) {
        XSSFCellStyle style = crearCeldaStyle(wb);
        style.setAlignment(HorizontalAlignment.RIGHT);
        DataFormat format = wb.createDataFormat();
        style.setDataFormat(format.getFormat("$#,##0"));
        return style;
    }
    
    private static XSSFCellStyle crearResumenLabelStyle(XSSFWorkbook wb) {
        XSSFCellStyle style = wb.createCellStyle();
        XSSFFont font = wb.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 11);
        font.setColor(new XSSFColor(COLOR_RESUMEN_TEXT, null));
        style.setFont(font);
        style.setFillForegroundColor(new XSSFColor(COLOR_RESUMEN_BG, null));
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }
    
    private static void aplicarBordes(XSSFCellStyle style) {
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
    }
    
    // ============================================================
    // HELPERS
    // ============================================================
    
    private static byte[] hexToRgb(String hex) {
        return new byte[] {
            (byte) Integer.parseInt(hex.substring(0, 2), 16),
            (byte) Integer.parseInt(hex.substring(2, 4), 16),
            (byte) Integer.parseInt(hex.substring(4, 6), 16)
        };
    }
    
    private static String limpiarNombreHoja(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            return "Reporte";
        }
        
        String limpio = nombre.replaceAll("[\\\\/*?\\[\\]:]", "");
        
        if (limpio.length() > 31) {
            limpio = limpio.substring(0, 31);
        }
        
        return limpio.isBlank() ? "Reporte" : limpio;
    }
}