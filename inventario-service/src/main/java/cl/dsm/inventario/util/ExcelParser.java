package cl.dsm.inventario.util;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.*;

/**
 * Parser del Excel de insumos del DSM Santa Cruz.
 * 
 * Maneja 2 estructuras distintas:
 *   - Estándar (5 sheets): A=nombre, B=unidad, C=código
 *   - Podología (1 sheet): A=nombre, B=código, C=nombre_código
 *                          (no tiene unidad de medida → usa "UNIDAD" por defecto)
 */
@Slf4j
@Component
public class ExcelParser {
    
    // Nombre exacto del sheet que tiene estructura especial
    private static final String SHEET_PODOLOGIA = "INSTRUMENTAL PODOLOGA CESFAM";
    
    // Palabras clave para detectar encabezados
    private static final Set<String> KEYWORDS_ENCABEZADO = Set.of(
        "CODIGO NORMA",
        "CÓDIGO NORMA",
        "UNIDAD DE MEDIDA",
        "NOMBRE CÓDIGO",
        "NOMBRE CODIGO",
        "RESPETAR MAYUSCULAS",
        "RESPETAR MAYÚSCULAS",
        "PODOLOGA",
        "PODÓLOGA",
        "INSTRUMENTAL CESFAM",
        "INSUMOS"
    );
    
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class FilaInsumo {
        private String nombreOriginal;
        private String nombreNormalizado;
        private String unidadMedida;
        private String codigoNorma;
        private String nombreCodigo;
        private String sheetOrigen;
        private int numeroFila;
    }
    
    /**
     * Parsea TODOS los sheets indicados en una sola apertura del Excel.
     */
    public Map<String, List<FilaInsumo>> parsearTodosLosSheets(InputStream is, 
                                                                Collection<String> nombresSheets) throws Exception {
        Map<String, List<FilaInsumo>> resultado = new LinkedHashMap<>();
        
        try (Workbook workbook = new XSSFWorkbook(is)) {
            for (String nombreSheet : nombresSheets) {
                Sheet sheet = workbook.getSheet(nombreSheet);
                if (sheet == null) {
                    log.warn("Sheet no encontrado: '{}'", nombreSheet);
                    resultado.put(nombreSheet, List.of());
                    continue;
                }
                
                List<FilaInsumo> filas = parsearSheet(sheet, nombreSheet);
                resultado.put(nombreSheet, filas);
                log.info("Sheet '{}': {} filas válidas", nombreSheet, filas.size());
            }
        }
        
        return resultado;
    }
    
    /**
     * Parsea un sheet, detectando automáticamente su estructura.
     */
    private List<FilaInsumo> parsearSheet(Sheet sheet, String nombreSheet) {
        boolean esPodologia = SHEET_PODOLOGIA.equals(nombreSheet);
        
        if (esPodologia) {
            return parsearSheetPodologia(sheet, nombreSheet);
        } else {
            return parsearSheetEstandar(sheet, nombreSheet);
        }
    }
    
    /**
     * ============================================================
     * SHEET ESTÁNDAR (5 sheets)
     * Estructura: A=nombre, B=unidad, C=código
     * ============================================================
     */
    private List<FilaInsumo> parsearSheetEstandar(Sheet sheet, String nombreSheet) {
        List<FilaInsumo> filas = new ArrayList<>();
        
        for (int i = 0; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;
            
            String colA = getCellString(row, 0).trim();
            String colB = getCellString(row, 1).trim();
            String colC = getCellString(row, 2).trim();
            
            // Descartar encabezados
            if (esEncabezado(colA) || esEncabezado(colB) || esEncabezado(colC)) {
                log.debug("Sheet '{}': fila {} descartada (encabezado)", nombreSheet, i);
                continue;
            }
            
            // Descartar col A inválida
            if (!esNombreValido(colA)) {
                log.debug("Sheet '{}': fila {} descartada (col A: '{}')", nombreSheet, i, colA);
                continue;
            }
            
            filas.add(FilaInsumo.builder()
                    .nombreOriginal(colA)
                    .nombreNormalizado(limpiarNombre(colA))
                    .unidadMedida(normalizarUnidad(colB))
                    .codigoNorma(normalizarCodigo(colC))
                    .sheetOrigen(nombreSheet)
                    .numeroFila(i)
                    .build());
        }
        
        return filas;
    }
    
    /**
     * ============================================================
     * SHEET PODOLOGÍA (estructura especial)
     * Estructura: A=nombre, B=código, C=nombre_código
     * ⚠️ NO tiene unidad de medida → se usa "UNIDAD" por defecto
     * ============================================================
     */
    private List<FilaInsumo> parsearSheetPodologia(Sheet sheet, String nombreSheet) {
        List<FilaInsumo> filas = new ArrayList<>();
        
        for (int i = 0; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;
            
            String colA = getCellString(row, 0).trim();
            String colB = getCellString(row, 1).trim();
            String colC = getCellString(row, 2).trim();
            
            // Descartar encabezados (PODOLOGA, INSTRUMENTAL CESFAM, etc.)
            if (esEncabezado(colA) || esEncabezado(colB) || esEncabezado(colC)) {
                log.debug("Sheet '{}': fila {} descartada (encabezado)", nombreSheet, i);
                continue;
            }
            
            // Descartar col A inválida
            if (!esNombreValido(colA)) {
                log.debug("Sheet '{}': fila {} descartada (col A: '{}')", nombreSheet, i, colA);
                continue;
            }
            
            // ⚠️ En podología:
            //   - colB = código (DM-03346, SIN CÓDIGO, etc.)
            //   - colC = nombre del código oficial
            //   - unidad = UNIDAD (por defecto, no hay columna)
            filas.add(FilaInsumo.builder()
                    .nombreOriginal(colA)
                    .nombreNormalizado(limpiarNombre(colA))
                    .unidadMedida("UNIDAD")                     // ← por defecto
                    .codigoNorma(normalizarCodigo(colB))        // ← colB = código
                    .nombreCodigo(colC)                          // ← colC = nombre código
                    .sheetOrigen(nombreSheet)
                    .numeroFila(i)
                    .build());
        }
        
        return filas;
    }
    
    /**
     * Detecta si un texto es un encabezado de sección.
     */
    private boolean esEncabezado(String texto) {
        if (texto == null || texto.isBlank()) return false;
        
        String upper = texto.toUpperCase().trim();
        
        // Coincidencia exacta
        if (KEYWORDS_ENCABEZADO.contains(upper)) return true;
        
        // Coincidencia parcial (contiene alguna palabra clave)
        for (String kw : KEYWORDS_ENCABEZADO) {
            if (upper.contains(kw)) return true;
        }
        
        // Encabezados que empiezan con "CÓDIGO" o "CODIGO"
        if (upper.equals("CÓDIGO") || upper.equals("CODIGO")) return true;
        
        return false;
    }
    
    /**
     * Valida que un nombre sea un artículo real.
     */
    private boolean esNombreValido(String nombre) {
        if (nombre == null || nombre.isBlank()) return false;
        if (nombre.matches("^[-*\\s\\.]+$")) return false;   // solo símbolos
        if (nombre.length() < 3) return false;                // muy corto
        if (nombre.matches("^\\d+$")) return false;           // solo números
        
        // No debe ser un encabezado
        if (esEncabezado(nombre)) return false;
        
        return true;
    }
    
    private String getCellString(Row row, int col) {
        Cell cell = row.getCell(col);
        if (cell == null) return "";
        
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> {
                double val = cell.getNumericCellValue();
                if (val == Math.floor(val)) yield String.valueOf((long) val);
                yield String.valueOf(val);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> cell.getCellFormula();
            default -> "";
        };
    }
    
    // ============================================================
    // NORMALIZADORES
    // ============================================================
    
    public String limpiarNombre(String raw) {
        if (raw == null) return null;
        
        String limpio = raw.trim()
                .replaceAll("\\*+$", "")
                .replaceAll("\\s+", " ")
                .replaceAll("\"", "")
                .trim();
        
        if (limpio.isBlank()) return null;
        
        // Siglas cortas en mayúsculas
        if (limpio.length() <= 5 && limpio.equals(limpio.toUpperCase())) {
            return limpio;
        }
        
        // Capitalizar primera letra de cada palabra
        StringBuilder sb = new StringBuilder();
        boolean capitalizarSiguiente = true;
        for (char c : limpio.toCharArray()) {
            if (Character.isWhitespace(c) || c == '/' || c == '-' || c == '.') {
                sb.append(c);
                capitalizarSiguiente = true;
            } else if (capitalizarSiguiente) {
                sb.append(Character.toUpperCase(c));
                capitalizarSiguiente = false;
            } else {
                sb.append(Character.toLowerCase(c));
            }
        }
        
        return sb.toString();
    }
    
    public String normalizarUnidad(String raw) {
        if (raw == null || raw.isBlank() || raw.equals("-")) return "UNIDAD";
        
        String u = raw.trim().toUpperCase();
        
        return switch (u) {
            case "UNIDAD", "UNIDADES" -> "UNIDAD";
            case "CAJA", "CAJAS", "CX" -> "CAJA";
            case "PAQUETE", "PAQUETES", "PQTE" -> "PAQUETE";
            case "BOLSA", "BOLSAS" -> "BOLSA";
            case "KILO", "KILOS", "KG" -> "KILO";
            case "SET" -> "SET";
            case "KIT" -> "KIT";
            case "BLISTER" -> "BLISTER";
            case "FRASCO" -> "FRASCO";
            case "UNIDAD (PAR)", "PAR" -> "PAR";
            default -> u;
        };
    }
    
    public String normalizarCodigo(String raw) {
        if (raw == null || raw.isBlank()) return "SIN CODIGO";
        
        String c = raw.trim().toUpperCase();
        if (c.contains("SIN CODIGO") 
                || c.contains("SIN CÓDIGO") 
                || c.equals("NO APLICA")
                || c.equals("-")
                || c.equals("CÓDIGO")
                || c.equals("CODIGO")) {
            return "SIN CODIGO";
        }
        return c;
    }
    
    public String extraerPresentacion(String nombre) {
        if (nombre == null) return null;
        
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("\\(([^)]+)\\)");
        java.util.regex.Matcher m = p.matcher(nombre);
        if (m.find()) {
            return m.group(1).trim();
        }
        
        p = java.util.regex.Pattern.compile(
            "(?i)\\bx\\s*\\d+\\s*(?:UNIDADES?|U|ML|GR?S?|LTS?|KILOS?|LITROS?)?");
        m = p.matcher(nombre);
        if (m.find()) {
            return m.group().toUpperCase();
        }
        
        return null;
    }
}