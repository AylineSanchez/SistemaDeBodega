package cl.dsm.inventario.dto;

import cl.dsm.inventario.entity.RecepcionDetalle;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class RecepcionDetalleResponse {
    
    private Long id;
    private Long articuloId;
    private String sku;
    private String nombreProducto;
    private String categoriaCodigo;
    private String categoriaNombre;
    private String categoriaColor;
    private String unidadMedidaBase;
    
    private Integer cantidadRecibida;
    private Integer cantidadPendiente;
    private BigDecimal valorUnitario;
    private BigDecimal montoTotal;
    
    private String numeroLote;
    private LocalDate fechaVencimiento;
    private String registroIsp;
    
    private String ubicacionFisica;
    private String estante;
    private String nivel;
    
    private String estado;
    private String observaciones;
    
    public static RecepcionDetalleResponse from(RecepcionDetalle d) {
        return RecepcionDetalleResponse.builder()
                .id(d.getId())
                .articuloId(d.getArticulo().getId())
                .sku(d.getSku())
                .nombreProducto(d.getNombreProducto())
                .categoriaCodigo(d.getArticulo().getCategoria().getCodigo())
                .categoriaNombre(d.getArticulo().getCategoria().getNombre())
                .categoriaColor(d.getArticulo().getCategoria().getColor())
                .unidadMedidaBase(d.getArticulo().getUnidadMedidaBase())
                .cantidadRecibida(d.getCantidadRecibida())
                .cantidadPendiente(d.getCantidadPendiente())
                .valorUnitario(d.getValorUnitario())
                .montoTotal(d.getMontoTotal())
                .numeroLote(d.getNumeroLote())
                .fechaVencimiento(d.getFechaVencimiento())
                .registroIsp(d.getRegistroIsp())
                .ubicacionFisica(d.getUbicacionFisica())
                .estante(d.getEstante())
                .nivel(d.getNivel())
                .estado(d.getEstado())
                .observaciones(d.getObservaciones())
                .build();
    }
}