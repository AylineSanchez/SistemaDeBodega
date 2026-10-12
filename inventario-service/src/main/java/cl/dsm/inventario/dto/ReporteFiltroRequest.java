package cl.dsm.inventario.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Filtros para generación de reportes (RF-084).
 */
@Data
public class ReporteFiltroRequest {
    
    private String tipoReporte;
    // STOCK_CRITICO | VENCIMIENTOS | MERMAS_BAJAS | CONSUMO_CENTRO_COSTO
    // KARDEX | LOG_AUDITORIA | VALORIZACION | CUMPLIMIENTO_OC
    
    private LocalDate fechaInicio;
    private LocalDate fechaFin;
    private LocalDateTime fechaHoraInicio;
    private LocalDateTime fechaHoraFin;
    
    private Integer bodegaId;
    private Integer categoriaId;
    private Integer centroCostoId;
    private Long articuloId;
    private String tipoMovimiento;
    private String estado;
    
    private String q;
    private String proveedor;
    private String rutProveedor;
    
    private Integer page = 0;
    private Integer size = 100;
}