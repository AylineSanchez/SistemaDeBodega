package cl.dsm.inventario.dto;

import cl.dsm.inventario.entity.StockPorBodega;
import lombok.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class QrInfoDTO {
    
    private Long stockId;
    private String codigoQr;
    private Long articuloId;
    private String sku;
    private String nombreArticulo;
    private String unidadMedida;
    private Boolean requiereLote;
    private Boolean requiereVencimiento;
    private Integer bodegaId;
    private String bodegaCodigo;
    private String bodegaNombre;
    private String dependencia;
    private Integer stockActual;
    private String ubicacionFisica;
    private String estante;
    private String nivel;
    
    // URL para la imagen QR
    private String qrImageUrl;
    
    public static QrInfoDTO from(StockPorBodega s) {
        return QrInfoDTO.builder()
                .stockId(s.getId())
                .codigoQr(s.getCodigoQr())
                .articuloId(s.getArticulo().getId())
                .sku(s.getArticulo().getSku())
                .nombreArticulo(s.getArticulo().getNombreTecnico())
                .unidadMedida(s.getArticulo().getUnidadMedidaBase())
                .requiereLote(s.getArticulo().getRequiereLote())
                .requiereVencimiento(s.getArticulo().getRequiereVencimiento())
                .bodegaId(s.getBodega().getId())
                .bodegaCodigo(s.getBodega().getCodigo())
                .bodegaNombre(s.getBodega().getNombre())
                .dependencia(s.getBodega().getDependencia())
                .stockActual(s.getStockActual())
                .ubicacionFisica(s.getUbicacionFisica())
                .estante(s.getEstante())
                .nivel(s.getNivel())
                .qrImageUrl("/api/qr/" + s.getCodigoQr())
                .build();
    }
}