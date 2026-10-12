package cl.dsm.inventario.dto;

import cl.dsm.inventario.entity.Despacho;
import cl.dsm.inventario.entity.DespachoDetalle;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class DespachoResponse {
    
    private Long id;
    private String numeroDespacho;
    private Long pedidoId;
    private String numeroPedido;
    
    private Integer centroCostoId;
    private String centroCodigo;
    private String centroNombre;
    private String centroTipo;
    
    private Integer bodegaId;
    private String bodegaCodigo;
    private String bodegaNombre;
    
    private String receptorRut;
    private String receptorNombre;
    private String receptorCargo;
    
    private LocalDateTime fechaDespacho;
    private String estado;
    private String guiaInterna;
    private String observaciones;
    
    private List<DespachoDetalleResponse> detalles;
    private Integer totalLineas;
    private Integer totalUnidades;
    private BigDecimal valorTotal;
    
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class DespachoDetalleResponse {
        private Long id;
        private Long articuloId;
        private String sku;
        private String nombreProducto;
        private String categoriaCodigo;
        private String categoriaColor;
        private String unidadMedidaBase;
        private Long loteId;
        private String numeroLote;
        private LocalDate fechaVencimiento;
        private Integer cantidad;
        private BigDecimal valorUnitario;
        private BigDecimal valorTotal;
        private String observaciones;
        
        public static DespachoDetalleResponse from(DespachoDetalle d) {
            return DespachoDetalleResponse.builder()
                    .id(d.getId())
                    .articuloId(d.getArticulo().getId())
                    .sku(d.getSku())
                    .nombreProducto(d.getNombreProducto())
                    .categoriaCodigo(d.getArticulo().getCategoria().getCodigo())
                    .categoriaColor(d.getArticulo().getCategoria().getColor())
                    .unidadMedidaBase(d.getArticulo().getUnidadMedidaBase())
                    .loteId(d.getLote() != null ? d.getLote().getId() : null)
                    .numeroLote(d.getNumeroLote())
                    .fechaVencimiento(d.getFechaVencimiento())
                    .cantidad(d.getCantidad())
                    .valorUnitario(d.getValorUnitario())
                    .valorTotal(d.getValorTotal())
                    .observaciones(d.getObservaciones())
                    .build();
        }
    }
    
    public static DespachoResponse from(Despacho d, List<DespachoDetalle> detalles) {
        List<DespachoDetalleResponse> detallesDTO = detalles.stream()
                .map(DespachoDetalleResponse::from)
                .toList();
        
        BigDecimal valorTotal = detallesDTO.stream()
                .map(x -> x.getValorTotal() != null ? x.getValorTotal() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        return DespachoResponse.builder()
                .id(d.getId())
                .numeroDespacho(d.getNumeroDespacho())
                .pedidoId(d.getPedido() != null ? d.getPedido().getId() : null)
                .numeroPedido(d.getPedido() != null ? d.getPedido().getNumeroPedido() : null)
                .centroCostoId(d.getCentroCosto().getId())
                .centroCodigo(d.getCentroCosto().getCodigo())
                .centroNombre(d.getCentroCosto().getNombre())
                .centroTipo(d.getCentroCosto().getTipo())
                .bodegaId(d.getBodega().getId())
                .bodegaCodigo(d.getBodega().getCodigo())
                .bodegaNombre(d.getBodega().getNombre())
                .receptorRut(d.getReceptorRut())
                .receptorNombre(d.getReceptorNombre())
                .receptorCargo(d.getReceptorCargo())
                .fechaDespacho(d.getFechaDespacho())
                .estado(d.getEstado())
                .guiaInterna(d.getGuiaInterna())
                .observaciones(d.getObservaciones())
                .detalles(detallesDTO)
                .totalLineas(detallesDTO.size())
                .totalUnidades(detallesDTO.stream().mapToInt(DespachoDetalleResponse::getCantidad).sum())
                .valorTotal(valorTotal)
                .build();
    }
}