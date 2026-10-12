package cl.dsm.inventario.dto;

import cl.dsm.inventario.entity.BloqueoBodega;
import cl.dsm.inventario.entity.BloqueoSanitario;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class BloqueoResponse {
    
    private Long id;
    private String numeroBloqueo;
    
    private Long loteId;
    private Long articuloId;
    private String sku;
    private String numeroLote;
    private String nombreProducto;
    private LocalDate fechaVencimiento;
    private String registroIsp;
    
    private String tipoAlerta;
    private String numeroOficioIsp;
    private LocalDate fechaAlerta;
    private String descripcionAlerta;
    private String justificacion;
    
    private String bloqueadoPorRut;
    private String bloqueadoPorNombre;
    private LocalDateTime fechaBloqueo;
    
    private Integer stockAfectado;
    private Integer bodegasAfectadas;
    
    private String estado;
    
    private String levantadoPorRut;
    private String levantadoPorNombre;
    private LocalDateTime fechaLevantamiento;
    private String motivoLevantamiento;
    
    private List<BloqueoBodegaResponse> bodegas;
    
    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaActualizacion;
    
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class BloqueoBodegaResponse {
        private Long id;
        private Integer bodegaId;
        private String bodegaCodigo;
        private String bodegaNombre;
        private Integer cantidadBloqueada;
        private String estado;
        
        public static BloqueoBodegaResponse from(BloqueoBodega b) {
            return BloqueoBodegaResponse.builder()
                    .id(b.getId())
                    .bodegaId(b.getBodega().getId())
                    .bodegaCodigo(b.getBodega().getCodigo())
                    .bodegaNombre(b.getBodega().getNombre())
                    .cantidadBloqueada(b.getCantidadBloqueada())
                    .estado(b.getEstado())
                    .build();
        }
    }
    
    public static BloqueoResponse from(BloqueoSanitario b, List<BloqueoBodega> bodegas) {
        return BloqueoResponse.builder()
                .id(b.getId())
                .numeroBloqueo(b.getNumeroBloqueo())
                .loteId(b.getLote().getId())
                .articuloId(b.getArticulo().getId())
                .sku(b.getSku())
                .numeroLote(b.getNumeroLote())
                .nombreProducto(b.getNombreProducto())
                .fechaVencimiento(b.getFechaVencimiento())
                .registroIsp(b.getRegistroIsp())
                .tipoAlerta(b.getTipoAlerta())
                .numeroOficioIsp(b.getNumeroOficioIsp())
                .fechaAlerta(b.getFechaAlerta())
                .descripcionAlerta(b.getDescripcionAlerta())
                .justificacion(b.getJustificacion())
                .bloqueadoPorRut(b.getBloqueadoPorRut())
                .bloqueadoPorNombre(b.getBloqueadoPorNombre())
                .fechaBloqueo(b.getFechaBloqueo())
                .stockAfectado(b.getStockAfectado())
                .bodegasAfectadas(b.getBodegasAfectadas())
                .estado(b.getEstado())
                .levantadoPorRut(b.getLevantadoPorRut())
                .levantadoPorNombre(b.getLevantadoPorNombre())
                .fechaLevantamiento(b.getFechaLevantamiento())
                .motivoLevantamiento(b.getMotivoLevantamiento())
                .bodegas(bodegas != null ? bodegas.stream().map(BloqueoBodegaResponse::from).toList() : List.of())
                .fechaCreacion(b.getFechaCreacion())
                .fechaActualizacion(b.getFechaActualizacion())
                .build();
    }
}