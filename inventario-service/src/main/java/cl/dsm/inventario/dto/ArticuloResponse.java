package cl.dsm.inventario.dto;

import cl.dsm.inventario.entity.Articulo;
import cl.dsm.inventario.entity.StockPorBodega;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ArticuloResponse {

    private Long id;
    private String sku;
    private String nombreTecnico;
    private String presentacion;
    private String concentracion;

    private Integer categoriaId;
    private String categoriaCodigo;
    private String categoriaNombre;
    private String categoriaColor;

    private String subcategoria;
    private String descripcion;

    private String unidadMedidaBase;
    private String unidadCompra;
    private Integer factorConversion;
    private Integer stockMinimo;
    private Integer stockMaximo;
    private Integer stockSeguridad;
    private Integer puntoReorden;

    private Boolean requiereLote;
    private Boolean requiereVencimiento;
    private String registroIsp;
    private Boolean esDispositivoMedico;
    private Boolean esInsumoCritico;
    private String condicionesAlmacenamiento;

    private String codigoUnspsc;
    private String tipoProveedorHabitual;
    private String codigoCenabast;
    private String fabricante;
    private String paisOrigen;

    private BigDecimal precioPromedioPonderado;
    private Boolean activo;
    private String observaciones;
    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaActualizacion;
    private String creadoPor;
    private String actualizadoPor;

    private Integer stockTotal;
    private String estadoStock;
    private Integer bodegasCount;
    private List<StockBodegaDTO> stocks;

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class StockBodegaDTO {
        private Long id;
        private Integer bodegaId;
        private String bodegaCodigo;
        private String bodegaNombre;
        private String dependencia;
        private Integer stockActual;
        private String ubicacionFisica;
        private String estante;
        private String nivel;
        private String codigoQr;
        private Boolean activo;

        private Long loteId;
        private String numeroLote;
        private LocalDate fechaVencimiento;
        private Boolean bloqueado;
    }

    public static ArticuloResponse from(Articulo a, List<StockPorBodega> stocks, Integer stockTotal) {
        String estado = calcularEstado(stockTotal, a.getStockMinimo());

        List<StockBodegaDTO> stocksDTO = stocks == null ? List.of() : stocks.stream()
                .map(s -> StockBodegaDTO.builder()
                        .id(s.getId())
                        .bodegaId(s.getBodega().getId())
                        .bodegaCodigo(s.getBodega().getCodigo())
                        .bodegaNombre(s.getBodega().getNombre())
                        .dependencia(s.getBodega().getDependencia())
                        .stockActual(s.getStockActual())
                        .ubicacionFisica(s.getUbicacionFisica())
                        .estante(s.getEstante())
                        .nivel(s.getNivel())
                        .codigoQr(s.getCodigoQr())
                        .activo(s.getActivo())
                        .build())
                .toList();

        return ArticuloResponse.builder()
                .id(a.getId())
                .sku(a.getSku())
                .nombreTecnico(a.getNombreTecnico())
                .presentacion(a.getPresentacion())
                .concentracion(a.getConcentracion())
                .categoriaId(a.getCategoria().getId())
                .categoriaCodigo(a.getCategoria().getCodigo())
                .categoriaNombre(a.getCategoria().getNombre())
                .categoriaColor(a.getCategoria().getColor())
                .subcategoria(a.getSubcategoria())
                .descripcion(a.getDescripcion())
                .unidadMedidaBase(a.getUnidadMedidaBase())
                .unidadCompra(a.getUnidadCompra())
                .factorConversion(a.getFactorConversion())
                .stockMinimo(a.getStockMinimo())
                .stockMaximo(a.getStockMaximo())
                .stockSeguridad(a.getStockSeguridad())
                .puntoReorden(a.getPuntoReorden())
                .requiereLote(a.getRequiereLote())
                .requiereVencimiento(a.getRequiereVencimiento())
                .registroIsp(a.getRegistroIsp())
                .esDispositivoMedico(a.getEsDispositivoMedico())
                .esInsumoCritico(a.getEsInsumoCritico())
                .condicionesAlmacenamiento(a.getCondicionesAlmacenamiento())
                .codigoUnspsc(a.getCodigoUnspsc())
                .tipoProveedorHabitual(a.getTipoProveedorHabitual())
                .codigoCenabast(a.getCodigoCenabast())
                .fabricante(a.getFabricante())
                .paisOrigen(a.getPaisOrigen())
                .precioPromedioPonderado(a.getPrecioPromedioPonderado())
                .activo(a.getActivo())
                .observaciones(a.getObservaciones())
                .fechaCreacion(a.getFechaCreacion())
                .fechaActualizacion(a.getFechaActualizacion())
                .creadoPor(a.getCreadoPor())
                .actualizadoPor(a.getActualizadoPor())
                .stockTotal(stockTotal)
                .estadoStock(estado)
                .stocks(stocksDTO)
                .bodegasCount(stocksDTO.size())
                .build();
    }

    private static String calcularEstado(Integer stock, Integer minimo) {
        if (stock == null) stock = 0;
        if (minimo == null) minimo = 0;
        if (stock == 0) return "QUIEBRE";
        if (stock <= minimo) return "CRITICO";
        if (stock <= minimo * 1.5) return "BAJO";
        return "NORMAL";
    }
}