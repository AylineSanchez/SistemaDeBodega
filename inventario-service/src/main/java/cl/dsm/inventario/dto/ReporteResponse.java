package cl.dsm.inventario.dto;

import lombok.*;

import java.util.List;
import java.util.Map;

/**
 * Respuesta genérica de reporte con columnas dinámicas.
 */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ReporteResponse {
    
    private String tipoReporte;
    private String titulo;
    private String subtitulo;
    private String filtrosAplicados;
    private String generadoPor;
    private String fechaGeneracion;
    
    private List<String> columnas;
    private List<Map<String, Object>> filas;
    private List<ResumenItem> resumen;
    
    private Integer totalRegistros;
    
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ResumenItem {
        private String label;
        private Object valor;
    }
}