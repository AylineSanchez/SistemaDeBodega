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
 * Estructuras detectadas:
 *   - ASEO / LIBRERIA: A=nombre, B=unidad, C=nombre_normalizado (NO es código)
 *   - DENTALES / IMPL. DENTAL / MEDICOS: A=nombre, B=unidad, C=registro_isp (DM-XXXXX), D=nombre_codigo
 *   - PODOLOGIA: A=nombre, B=registro_isp (o "SIN CÓDIGO"), C=nombre_codigo, unidad="UNIDAD" por defecto
 */
@Slf4j
@Component
public class ExcelParser {

    private static final String SHEET_PODOLOGIA = "INSTRUMENTAL PODOLOGA CESFAM";

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

    /**
     * Fila parseada del Excel.
     */
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class FilaInsumo {
        private String nombreOriginal;
        private String nombreNormalizado;
        private String unidadMedida;
        /** Registro ISP (solo si empieza con DM-XXXXX). Puede ser null. */
        private String registroIsp;
        /** Nombre oficial del código (columna D o columna C según el sheet). */
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
        return esPodologia
                ? parsearSheetPodologia(sheet, nombreSheet)
                : parsearSheetEstandar(sheet, nombreSheet);
    }

    /**
     * ============================================================
     * SHEET ESTÁNDAR (ASEO, LIBRERIA, DENTALES, IMPL. DENTAL, MEDICOS)
     * ============================================================
     * Estructura:
     *   - A = nombre del insumo
     *   - B = unidad de medida
     *   - C = registro ISP (si empieza con "DM-") O nombre normalizado
     *   - D = nombre del código (si existe)
     */
    private List<FilaInsumo> parsearSheetEstandar(Sheet sheet, String nombreSheet) {
        List<FilaInsumo> filas = new ArrayList<>();

        for (int i = 0; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;

            String colA = getCellString(row, 0).trim();
            String colB = getCellString(row, 1).trim();
            String colC = getCellString(row, 2).trim();
            String colD = getCellString(row, 3).trim();

            // Descartar encabezados
            if (esEncabezado(colA) || esEncabezado(colB) || esEncabezado(colC) || esEncabezado(colD)) {
                continue;
            }

            // Descartar col A inválida
            if (!esNombreValido(colA)) {
                continue;
            }

            // Determinar si colC es Registro ISP (empieza con "DM-")
            String registroIsp = null;
            String nombreCodigo = null;

            if (colC.toUpperCase().startsWith("DM-")) {
                // Caso: DENTALES, IMPL. DENTAL, MEDICOS
                registroIsp = colC.toUpperCase();
                nombreCodigo = !colD.isBlank() ? colD : null;
            } else {
                // Caso: ASEO, LIBRERIA → colC es el nombre normalizado
                nombreCodigo = !colC.isBlank() ? colC : null;
            }

            filas.add(FilaInsumo.builder()
                    .nombreOriginal(colA)
                    .nombreNormalizado(limpiarNombre(colA))
                    .unidadMedida(normalizarUnidad(colB))
                    .registroIsp(registroIsp)
                    .nombreCodigo(nombreCodigo)
                    .sheetOrigen(nombreSheet)
                    .numeroFila(i)
                    .build());
        }

        return filas;
    }

    /**
     * ============================================================
     * SHEET PODOLOGÍA (estructura especial)
     * ============================================================
     * Estructura:
     *   - A = nombre
     *   - B = registro ISP (DM-XXXXX o "SIN CÓDIGO")
     *   - C = nombre del código
     *   - unidad = UNIDAD (por defecto)
     */
    private List<FilaInsumo> parsearSheetPodologia(Sheet sheet, String nombreSheet) {
        List<FilaInsumo> filas = new ArrayList<>();

        for (int i = 0; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;

            String colA = getCellString(row, 0).trim();
            String colB = getCellString(row, 1).trim();
            String colC = getCellString(row, 2).trim();

            if (esEncabezado(colA) || esEncabezado(colB) || esEncabezado(colC)) {
                continue;
            }

            if (!esNombreValido(colA)) {
                continue;
            }

            // En podología, colB es el registro ISP (o "SIN CÓDIGO")
            String registroIsp = null;
            if (colB.toUpperCase().startsWith("DM-")) {
                registroIsp = colB.toUpperCase();
            }

            filas.add(FilaInsumo.builder()
                    .nombreOriginal(colA)
                    .nombreNormalizado(limpiarNombre(colA))
                    .unidadMedida("UNIDAD")
                    .registroIsp(registroIsp)
                    .nombreCodigo(!colC.isBlank() ? colC : null)
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

        if (KEYWORDS_ENCABEZADO.contains(upper)) return true;

        for (String kw : KEYWORDS_ENCABEZADO) {
            if (upper.contains(kw)) return true;
        }

        if (upper.equals("CÓDIGO") || upper.equals("CODIGO")) return true;

        return false;
    }

    /**
     * Valida que un nombre sea un artículo real.
     */
    private boolean esNombreValido(String nombre) {
        if (nombre == null || nombre.isBlank()) return false;
        if (nombre.matches("^[-*\\s\\.]+$")) return false;
        if (nombre.length() < 3) return false;
        if (nombre.matches("^\\d+$")) return false;
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

        if (limpio.length() <= 5 && limpio.equals(limpio.toUpperCase())) {
            return limpio;
        }

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