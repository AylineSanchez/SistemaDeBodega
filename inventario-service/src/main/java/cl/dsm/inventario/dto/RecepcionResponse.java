package cl.dsm.inventario.dto;

import cl.dsm.inventario.entity.Recepcion;
import cl.dsm.inventario.entity.RecepcionDetalle;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class RecepcionResponse {
    
    private Long id;
    private String numeroRecepcion;
    
    // Documento
    private String tipoDocumento;
    private String numeroDocumento;
    private String numeroOrdenCompra;
    private String numeroGuiaDespacho;
    
    // Proveedor
    private String rutProveedor;
    private String nombreProveedor;
    private String telefonoProveedor;
    private String direccionProveedor;
    private String tipoProveedor;
    
    // Fechas
    private LocalDate fechaEmision;
    private LocalDate fechaRecepcion;
    private LocalDateTime fechaHoraIngreso;
    
    // Bodega
    private Integer bodegaId;
    private String bodegaCodigo;
    private String bodegaNombre;
    private String bodegaDependencia;
    
    // Receptor
    private String receptorRut;
    private String receptorNombre;
    private LocalDateTime receptorFecha;
    
    // Totales
    private BigDecimal montoNeto;
    private BigDecimal montoIva;
    private BigDecimal montoTotal;
    
    // Estado
    private String estado;
    private String observaciones;
    private Boolean activo;
    
    // Control
    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaActualizacion;
    private String creadoPor;
    private String actualizadoPor;
    
    // Detalles
    private List<RecepcionDetalleResponse> detalles;
    private Integer totalLineas;
    private Integer totalUnidades;
    
    public static RecepcionResponse from(Recepcion r, List<RecepcionDetalle> detalles) {
        return RecepcionResponse.builder()
                .id(r.getId())
                .numeroRecepcion(r.getNumeroRecepcion())
                .tipoDocumento(r.getTipoDocumento())
                .numeroDocumento(r.getNumeroDocumento())
                .numeroOrdenCompra(r.getNumeroOrdenCompra())
                .numeroGuiaDespacho(r.getNumeroGuiaDespacho())
                .rutProveedor(r.getRutProveedor())
                .nombreProveedor(r.getNombreProveedor())
                .telefonoProveedor(r.getTelefonoProveedor())
                .direccionProveedor(r.getDireccionProveedor())
                .tipoProveedor(r.getTipoProveedor())
                .fechaEmision(r.getFechaEmision())
                .fechaRecepcion(r.getFechaRecepcion())
                .fechaHoraIngreso(r.getFechaHoraIngreso())
                .bodegaId(r.getBodega().getId())
                .bodegaCodigo(r.getBodega().getCodigo())
                .bodegaNombre(r.getBodega().getNombre())
                .bodegaDependencia(r.getBodega().getDependencia())
                .receptorRut(r.getReceptorRut())
                .receptorNombre(r.getReceptorNombre())
                .receptorFecha(r.getReceptorFecha())
                .montoNeto(r.getMontoNeto())
                .montoIva(r.getMontoIva())
                .montoTotal(r.getMontoTotal())
                .estado(r.getEstado())
                .observaciones(r.getObservaciones())
                .activo(r.getActivo())
                .fechaCreacion(r.getFechaCreacion())
                .fechaActualizacion(r.getFechaActualizacion())
                .creadoPor(r.getCreadoPor())
                .actualizadoPor(r.getActualizadoPor())
                .detalles(detalles.stream().map(RecepcionDetalleResponse::from).toList())
                .totalLineas(detalles.size())
                .totalUnidades(detalles.stream()
                        .mapToInt(RecepcionDetalle::getCantidadRecibida)
                        .sum())
                .build();
    }
}