package cl.dsm.inventario.dto;

import cl.dsm.inventario.entity.PedidoDetalle;
import cl.dsm.inventario.entity.PedidoInterno;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class PedidoResponse {
    
    private Long id;
    private String numeroPedido;
    
    private Integer centroCostoId;
    private String centroCodigo;
    private String centroNombre;
    private String centroTipo;
    
    private String solicitanteRut;
    private String solicitanteNombre;
    private String solicitanteCargo;
    
    private String prioridad;
    private LocalDateTime fechaSolicitud;
    private LocalDate fechaRequerida;
    private LocalDateTime fechaDespacho;
    
    private String estado;
    private String observaciones;
    
    private String jefeServicioNombre;
    private String jefeBodegaNombre;
    private String receptorNombre;
    private LocalDateTime firmaReceptorFecha;
    
    private List<PedidoDetalleResponse> detalles;
    private Integer totalLineas;
    private Integer totalUnidadesSolicitadas;
    private Integer totalUnidadesDespachadas;
    
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class PedidoDetalleResponse {
        private Long id;
        private Long articuloId;
        private String sku;
        private String nombreProducto;
        private String categoriaCodigo;
        private String categoriaColor;
        private String unidadMedidaBase;
        private Integer cantidadSolicitada;
        private Integer cantidadDespachada;
        private String estado;
        private String observaciones;
        
        public static PedidoDetalleResponse from(PedidoDetalle d) {
            return PedidoDetalleResponse.builder()
                    .id(d.getId())
                    .articuloId(d.getArticulo().getId())
                    .sku(d.getSku())
                    .nombreProducto(d.getNombreProducto())
                    .categoriaCodigo(d.getArticulo().getCategoria().getCodigo())
                    .categoriaColor(d.getArticulo().getCategoria().getColor())
                    .unidadMedidaBase(d.getArticulo().getUnidadMedidaBase())
                    .cantidadSolicitada(d.getCantidadSolicitada())
                    .cantidadDespachada(d.getCantidadDespachada())
                    .estado(d.getEstado())
                    .observaciones(d.getObservaciones())
                    .build();
        }
    }
    
    public static PedidoResponse from(PedidoInterno p, List<PedidoDetalle> detalles) {
        List<PedidoDetalleResponse> detallesDTO = detalles.stream()
                .map(PedidoDetalleResponse::from)
                .toList();
        
        return PedidoResponse.builder()
                .id(p.getId())
                .numeroPedido(p.getNumeroPedido())
                .centroCostoId(p.getCentroCosto().getId())
                .centroCodigo(p.getCentroCosto().getCodigo())
                .centroNombre(p.getCentroCosto().getNombre())
                .centroTipo(p.getCentroCosto().getTipo())
                .solicitanteRut(p.getSolicitanteRut())
                .solicitanteNombre(p.getSolicitanteNombre())
                .solicitanteCargo(p.getSolicitanteCargo())
                .prioridad(p.getPrioridad())
                .fechaSolicitud(p.getFechaSolicitud())
                .fechaRequerida(p.getFechaRequerida())
                .fechaDespacho(p.getFechaDespacho())
                .estado(p.getEstado())
                .observaciones(p.getObservaciones())
                .jefeServicioNombre(p.getJefeServicioNombre())
                .jefeBodegaNombre(p.getJefeBodegaNombre())
                .receptorNombre(p.getReceptorNombre())
                .firmaReceptorFecha(p.getFirmaReceptorFecha())
                .detalles(detallesDTO)
                .totalLineas(detallesDTO.size())
                .totalUnidadesSolicitadas(detallesDTO.stream()
                        .mapToInt(PedidoDetalleResponse::getCantidadSolicitada).sum())
                .totalUnidadesDespachadas(detallesDTO.stream()
                        .mapToInt(PedidoDetalleResponse::getCantidadDespachada).sum())
                .build();
    }
}