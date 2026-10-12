package cl.dsm.inventario.dto;

import cl.dsm.inventario.entity.Recepcion;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class RecepcionResumenDTO {
    
    private Long id;
    private String numeroRecepcion;
    private String tipoDocumento;
    private String numeroDocumento;
    private String rutProveedor;
    private String nombreProveedor;
    private String tipoProveedor;
    private LocalDate fechaEmision;
    private LocalDate fechaRecepcion;
    private String bodegaCodigo;
    private String bodegaNombre;
    private String bodegaDependencia;
    private String receptorNombre;
    private BigDecimal montoTotal;
    private String estado;
    private Integer totalLineas;
    private Integer totalUnidades;
    
    public static RecepcionResumenDTO from(Recepcion r) {
        return RecepcionResumenDTO.builder()
                .id(r.getId())
                .numeroRecepcion(r.getNumeroRecepcion())
                .tipoDocumento(r.getTipoDocumento())
                .numeroDocumento(r.getNumeroDocumento())
                .rutProveedor(r.getRutProveedor())
                .nombreProveedor(r.getNombreProveedor())
                .tipoProveedor(r.getTipoProveedor())
                .fechaEmision(r.getFechaEmision())
                .fechaRecepcion(r.getFechaRecepcion())
                .bodegaCodigo(r.getBodega().getCodigo())
                .bodegaNombre(r.getBodega().getNombre())
                .bodegaDependencia(r.getBodega().getDependencia())
                .receptorNombre(r.getReceptorNombre())
                .montoTotal(r.getMontoTotal())
                .estado(r.getEstado())
                .build();
    }
}