package cl.dsm.inventario.dto;

import cl.dsm.inventario.entity.Kardex;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class KardexDTO {
    
    private Long id;
    private LocalDateTime fechaMovimiento;
    private Long articuloId;
    private String sku;
    private String nombreArticulo;
    private String categoriaCodigo;
    private Integer bodegaId;
    private String bodegaCodigo;
    private String bodegaNombre;
    private String numeroLote;
    private String tipoMovimiento;
    private String documentoTipo;
    private String documentoNumero;
    private String documentoReferencia;
    private Integer cantidad;
    private Integer stockAnterior;
    private Integer stockNuevo;
    private BigDecimal valorUnitario;
    private BigDecimal valorTotal;
    private String usuarioRut;
    private String usuarioNombre;
    private String observaciones;
    
    public static KardexDTO from(Kardex k) {
        return KardexDTO.builder()
                .id(k.getId())
                .fechaMovimiento(k.getFechaMovimiento())
                .articuloId(k.getArticulo().getId())
                .sku(k.getArticulo().getSku())
                .nombreArticulo(k.getArticulo().getNombreTecnico())
                .categoriaCodigo(k.getArticulo().getCategoria().getCodigo())
                .bodegaId(k.getBodega().getId())
                .bodegaCodigo(k.getBodega().getCodigo())
                .bodegaNombre(k.getBodega().getNombre())
                .numeroLote(k.getLote() != null ? k.getLote().getNumeroLote() : null)
                .tipoMovimiento(k.getTipoMovimiento())
                .documentoTipo(k.getDocumentoTipo())
                .documentoNumero(k.getDocumentoNumero())
                .documentoReferencia(k.getDocumentoReferencia())
                .cantidad(k.getCantidad())
                .stockAnterior(k.getStockAnterior())
                .stockNuevo(k.getStockNuevo())
                .valorUnitario(k.getValorUnitario())
                .valorTotal(k.getValorTotal())
                .usuarioRut(k.getUsuarioRut())
                .usuarioNombre(k.getUsuarioNombre())
                .observaciones(k.getObservaciones())
                .build();
    }
}