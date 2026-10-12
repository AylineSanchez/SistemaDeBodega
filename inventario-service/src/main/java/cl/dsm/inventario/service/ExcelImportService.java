package cl.dsm.inventario.service;

import cl.dsm.inventario.dto.ImportResultDTO;
import cl.dsm.inventario.entity.Articulo;
import cl.dsm.inventario.entity.Bodega;
import cl.dsm.inventario.entity.Categoria;
import cl.dsm.inventario.entity.StockPorBodega;
import cl.dsm.inventario.exception.BusinessException;
import cl.dsm.inventario.repository.ArticuloRepository;
import cl.dsm.inventario.repository.BodegaRepository;
import cl.dsm.inventario.repository.CategoriaRepository;
import cl.dsm.inventario.repository.StockPorBodegaRepository;
import cl.dsm.inventario.util.ExcelParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.*;

/**
 * Servicio para importar artículos desde un Excel.
 *
 * Mapeo Sheet → Categoría → Bodega por defecto:
 * - INSUMOS ASEO                  → ASEO        → B3
 * - INSUMOS LIBRERIA              → LIBRERIA    → B1
 * - INSUMOS DENTALES              → DENTAL      → B1
 * - IMPLEMENTACION DENTAL         → IMPL-DENTAL → B1
 * - INSUMOS MEDICOS               → MEDICOS     → B2
 * - INSTRUMENTAL PODOLOGA CESFAM  → PODOLOGIA   → B6
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExcelImportService {
    
    private final ExcelParser excelParser;
    private final ArticuloRepository articuloRepo;
    private final CategoriaRepository categoriaRepo;
    private final BodegaRepository bodegaRepo;
    private final StockPorBodegaRepository stockRepo;
    
    // Mapeo sheet → categoría → bodega por defecto
    private static final Map<String, MapeoSheet> MAPEO_SHEETS = new LinkedHashMap<>();
    
    static {
        MAPEO_SHEETS.put("INSUMOS ASEO",                 new MapeoSheet("ASEO",        "B3", "Estante Aseo"));
        MAPEO_SHEETS.put("INSUMOS LIBRERIA",             new MapeoSheet("LIBRERIA",    "B1", "Estante Librería"));
        MAPEO_SHEETS.put("INSUMOS DENTALES",             new MapeoSheet("DENTAL",      "B1", "Estante Dental"));
        MAPEO_SHEETS.put("IMPLEMENTACION DENTAL",        new MapeoSheet("IMPL-DENTAL", "B1", "Estante Implementación"));
        MAPEO_SHEETS.put("INSUMOS MEDICOS",              new MapeoSheet("MEDICOS",     "B2", "Estante Insumos"));
        MAPEO_SHEETS.put("INSTRUMENTAL PODOLOGA CESFAM", new MapeoSheet("PODOLOGIA",   "B6", "Estante Podología"));
    }
    
    private record MapeoSheet(String categoriaCodigo, String bodegaCodigo, String ubicacionDefecto) {}
    
    /**
     * Importa todos los sheets del Excel.
     */
    @Transactional
    public ImportResultDTO importarDesdeExcel(MultipartFile archivo, String rutUsuario) {
        if (archivo == null || archivo.isEmpty()) {
            throw new BusinessException("Debe subir un archivo Excel");
        }
        
        String nombreArchivo = archivo.getOriginalFilename();
        if (nombreArchivo == null || !nombreArchivo.toLowerCase().endsWith(".xlsx")) {
            throw new BusinessException("El archivo debe ser .xlsx");
        }
        
        ImportResultDTO resultado = ImportResultDTO.builder()
                .nombreArchivo(nombreArchivo)
                .totalFilas(0)
                .importados(0)
                .duplicados(0)
                .errores(new ArrayList<>())
                .detallesPorSheet(new LinkedHashMap<>())
                .build();
        
        try (InputStream is = archivo.getInputStream()) {
            
            // ⚠️ UNA SOLA pasada del Excel, extrae TODOS los sheets
            Map<String, List<ExcelParser.FilaInsumo>> filasPorSheet = 
                    excelParser.parsearTodosLosSheets(is, MAPEO_SHEETS.keySet());
            
            // Procesar cada sheet
            for (Map.Entry<String, MapeoSheet> entry : MAPEO_SHEETS.entrySet()) {
                String nombreSheet = entry.getKey();
                MapeoSheet mapeo = entry.getValue();
                List<ExcelParser.FilaInsumo> filas = filasPorSheet.getOrDefault(nombreSheet, List.of());
                
                ImportResultDTO.SheetResult sheetResult = procesarSheet(
                        nombreSheet, mapeo, filas, rutUsuario);
                
                resultado.getDetallesPorSheet().put(nombreSheet, sheetResult);
                resultado.setTotalFilas(resultado.getTotalFilas() + sheetResult.getTotalFilas());
                resultado.setImportados(resultado.getImportados() + sheetResult.getImportados());
                resultado.setDuplicados(resultado.getDuplicados() + sheetResult.getDuplicados());
                resultado.getErrores().addAll(sheetResult.getErrores());
            }
            
            log.info("Importación completada: {} importados, {} duplicados, {} errores",
                     resultado.getImportados(), resultado.getDuplicados(), 
                     resultado.getErrores().size());
            
            return resultado;
            
        } catch (Exception e) {
            log.error("Error al procesar Excel", e);
            throw new BusinessException("Error al procesar Excel: " + e.getMessage());
        }
    }
    
    private ImportResultDTO.SheetResult procesarSheet(String nombreSheet,
                                                       MapeoSheet mapeo,
                                                       List<ExcelParser.FilaInsumo> filas,
                                                       String rutUsuario) {
        
        Categoria categoria = categoriaRepo.findByCodigo(mapeo.categoriaCodigo())
                .orElseThrow(() -> new BusinessException(
                        "Categoría no encontrada: " + mapeo.categoriaCodigo()));
        
        Bodega bodega = bodegaRepo.findByCodigo(mapeo.bodegaCodigo())
                .orElseThrow(() -> new BusinessException(
                        "Bodega no encontrada: " + mapeo.bodegaCodigo()));
        
        ImportResultDTO.SheetResult result = ImportResultDTO.SheetResult.builder()
                .sheet(nombreSheet)
                .categoria(categoria.getNombre())
                .bodegaDestino(bodega.getNombre())
                .totalFilas(filas.size())
                .importados(0)
                .duplicados(0)
                .errores(new ArrayList<>())
                .build();
        
        for (ExcelParser.FilaInsumo fila : filas) {
            try {
                // Determinar SKU
                String sku;
                if ("SIN CODIGO".equals(fila.getCodigoNorma())) {
                    sku = generarSkuInterno(categoria.getCodigo());
                } else {
                    sku = fila.getCodigoNorma();
                }
                
                // Verificar duplicado
                if (articuloRepo.existsBySku(sku)) {
                    result.setDuplicados(result.getDuplicados() + 1);
                    continue;
                }
                
                // Extraer presentación
                String presentacion = excelParser.extraerPresentacion(fila.getNombreNormalizado());
                
                boolean requiereLote = esCategoriaSanitaria(categoria.getCodigo());
                
                Articulo articulo = Articulo.builder()
                        .sku(sku)
                        .nombreTecnico(fila.getNombreNormalizado())
                        .presentacion(presentacion)
                        .categoria(categoria)
                        .unidadMedidaBase(fila.getUnidadMedida())
                        .stockMinimo(calcularStockMinimo(categoria.getCodigo()))
                        .stockMaximo(calcularStockMaximo(categoria.getCodigo()))
                        .stockSeguridad(calcularStockMinimo(categoria.getCodigo()))
                        .requiereLote(requiereLote)
                        .requiereVencimiento(requiereLote)
                        .esDispositivoMedico(requiereLote)
                        .esInsumoCritico(false)
                        .activo(true)
                        .origenImportacion("EXCEL_" + nombreSheet.replace(" ", "_"))
                        .creadoPor(rutUsuario)
                        .actualizadoPor(rutUsuario)
                        .build();
                
                articulo = articuloRepo.save(articulo);
                
                // Crear stock inicial con 0 unidades en la bodega por defecto
                StockPorBodega stock = StockPorBodega.builder()
                        .articulo(articulo)
                        .bodega(bodega)
                        .stockActual(0)
                        .ubicacionFisica(mapeo.ubicacionDefecto())
                        .creadoPor(rutUsuario)
                        .actualizadoPor(rutUsuario)
                        .build();
                
                stockRepo.save(stock);
                
                result.setImportados(result.getImportados() + 1);
                
            } catch (Exception e) {
                log.warn("Error procesando fila {}: {}", fila.getNumeroFila(), e.getMessage());
                result.getErrores().add(
                        "Fila " + fila.getNumeroFila() + " (" + fila.getNombreNormalizado() + "): " 
                        + e.getMessage());
            }
        }
        
        log.info("Sheet '{}': {} importados, {} duplicados, {} errores",
                 nombreSheet, result.getImportados(), result.getDuplicados(),
                 result.getErrores().size());
        
        return result;
    }
    
    private String generarSkuInterno(String categoriaCodigo) {
        String prefijo = "GEN-" + categoriaCodigo + "-";
        long count = articuloRepo.count() + 1;
        String candidato = prefijo + String.format("%05d", count);
        
        while (articuloRepo.existsBySku(candidato)) {
            count++;
            candidato = prefijo + String.format("%05d", count);
        }
        
        return candidato;
    }
    
    private boolean esCategoriaSanitaria(String codigo) {
        return "MEDICOS".equals(codigo) || "DENTAL".equals(codigo);
    }
    
    private int calcularStockMinimo(String categoriaCodigo) {
        return switch (categoriaCodigo) {
            case "MEDICOS" -> 30;
            case "DENTAL" -> 20;
            case "IMPL-DENTAL" -> 5;
            case "ASEO" -> 10;
            case "LIBRERIA" -> 10;
            case "PODOLOGIA" -> 5;
            default -> 10;
        };
    }
    
    private int calcularStockMaximo(String categoriaCodigo) {
        return switch (categoriaCodigo) {
            case "MEDICOS" -> 500;
            case "DENTAL" -> 300;
            case "IMPL-DENTAL" -> 100;
            case "ASEO" -> 200;
            case "LIBRERIA" -> 200;
            case "PODOLOGIA" -> 100;
            default -> 200;
        };
    }
}