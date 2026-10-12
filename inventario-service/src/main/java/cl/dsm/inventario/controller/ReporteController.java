package cl.dsm.inventario.controller;

import cl.dsm.inventario.dto.ApiResponse;
import cl.dsm.inventario.dto.ReporteFiltroRequest;
import cl.dsm.inventario.dto.ReporteResponse;
import cl.dsm.inventario.service.ReporteService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Controller del Módulo de Reportes (RF-075 a RF-085).
 * 
 * Permisos:
 *   - GET /api/reportes/** → JEFATURA + OPERATIVO + AUDITOR
 */
@Slf4j
@RestController
@RequestMapping("/api/reportes")
@RequiredArgsConstructor
@CrossOrigin(origins = "${app.cors.allowed-origins}")
@PreAuthorize("hasAnyRole('JEFATURA', 'OPERATIVO', 'AUDITOR')")
public class ReporteController {
    
    private final ReporteService reporteService;
    
    // ============================================================
    // GENERAR REPORTE (JSON)
    // ============================================================
    @PostMapping("/generar")
    public ResponseEntity<ApiResponse<ReporteResponse>> generar(
            @RequestBody ReporteFiltroRequest filtro,
            Authentication auth) {
        
        String rutUsuario = auth != null ? auth.getName() : "SYSTEM";
        String nombreUsuario = auth != null && auth.getDetails() != null
                ? auth.getDetails().toString() : "Usuario";
        
        ReporteResponse reporte = reporteService.generar(filtro, rutUsuario, nombreUsuario);
        
        return ResponseEntity.ok(ApiResponse.ok(reporte));
    }
    
    // ============================================================
    // EXPORTAR A EXCEL
    // ============================================================
    @PostMapping("/exportar/excel")
    public void exportarExcel(
            @RequestBody ReporteFiltroRequest filtro,
            Authentication auth,
            HttpServletResponse response) throws IOException {
        
        String rutUsuario = auth != null ? auth.getName() : "SYSTEM";
        String nombreUsuario = auth != null && auth.getDetails() != null
                ? auth.getDetails().toString() : "Usuario";
        
        ReporteResponse reporte = reporteService.generar(filtro, rutUsuario, nombreUsuario);
        
        // Generar Excel con POI
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet(reporte.getTitulo());
            
            // Estilos
            CellStyle headerStyle = crearHeaderStyle(workbook);
            CellStyle titleStyle = crearTitleStyle(workbook);
            CellStyle cellStyle = crearCellStyle(workbook);
            
            int rowIdx = 0;
            
            // Título
            Row titleRow = sheet.createRow(rowIdx++);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue(reporte.getTitulo());
            titleCell.setCellStyle(titleStyle);
            
            // Subtítulo
            Row subtitleRow = sheet.createRow(rowIdx++);
            subtitleRow.createCell(0).setCellValue(
                    reporte.getSubtitulo() != null ? reporte.getSubtitulo() : "");
            
            // Filtros
            Row filtrosRow = sheet.createRow(rowIdx++);
            filtrosRow.createCell(0).setCellValue("Filtros: " + reporte.getFiltrosAplicados());
            
            // Fecha generación
            Row fechaRow = sheet.createRow(rowIdx++);
            fechaRow.createCell(0).setCellValue(
                    "Generado por: " + reporte.getGeneradoPor() + 
                    " el " + reporte.getFechaGeneracion());
            
            rowIdx++; // línea vacía
            
            // Encabezados
            Row headerRow = sheet.createRow(rowIdx++);
            List<String> columnas = reporte.getColumnas();
            for (int i = 0; i < columnas.size(); i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(columnas.get(i));
                cell.setCellStyle(headerStyle);
            }
            
            // Datos
            for (Map<String, Object> fila : reporte.getFilas()) {
                Row dataRow = sheet.createRow(rowIdx++);
                int colIdx = 0;
                for (Object valor : fila.values()) {
                    Cell cell = dataRow.createCell(colIdx++);
                    cell.setCellStyle(cellStyle);
                    if (valor != null) {
                        if (valor instanceof Number) {
                            cell.setCellValue(((Number) valor).doubleValue());
                        } else {
                            cell.setCellValue(valor.toString());
                        }
                    }
                }
            }
            
            // Auto-size
            for (int i = 0; i < columnas.size(); i++) {
                sheet.autoSizeColumn(i);
            }
            
            // Configurar response
            String filename = reporte.getTipoReporte() + "_" + System.currentTimeMillis() + ".xlsx";
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", 
                    "attachment; filename=\"" + filename + "\"");
            
            workbook.write(response.getOutputStream());
            response.getOutputStream().flush();
            
            log.info("Reporte {} exportado a Excel por {}", 
                    reporte.getTipoReporte(), rutUsuario);
        }
    }
    
    // ============================================================
    // HELPERS DE ESTILO
    // ============================================================
    
    private CellStyle crearHeaderStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        // ⚡ CORREGIDO: ORANGE en lugar de DARK_ORANGE
        style.setFillForegroundColor(IndexedColors.ORANGE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }
    
    private CellStyle crearTitleStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 14);
        // ⚡ CORREGIDO: ORANGE en lugar de DARK_ORANGE
        font.setColor(IndexedColors.ORANGE.getIndex());
        style.setFont(font);
        return style;
    }
    
    private CellStyle crearCellStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }
}