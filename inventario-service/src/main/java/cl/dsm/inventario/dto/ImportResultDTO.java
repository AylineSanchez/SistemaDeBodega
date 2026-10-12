package cl.dsm.inventario.dto;

import lombok.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ImportResultDTO {
    
    private String nombreArchivo;
    private int totalFilas;
    private int importados;
    private int duplicados;
    private List<String> errores;
    private Map<String, SheetResult> detallesPorSheet;
    
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class SheetResult {
        private String sheet;
        private String categoria;
        private String bodegaDestino;
        private int totalFilas;
        private int importados;
        private int duplicados;
        private List<String> errores;
    }
}