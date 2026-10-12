package cl.dsm.inventario.dto;

import cl.dsm.inventario.entity.TrazabilidadDM;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class TrazabilidadResponse {
    
    private Long id;
    
    private Long articuloId;
    private String sku;
    private String nombreArticulo;
    private String categoriaCodigo;
    
    private Long loteId;
    private String numeroLote;
    private LocalDate fechaVencimiento;
    
    private String nombreProducto;
    private String rutProveedor;
    private String nombreProveedor;
    private String numeroFactura;
    private String numeroGuia;
    private String modelo;
    private String numeroSerie;
    
    private String estadoDm;
    private String numeroRegistroSanitario;
    private String formaAdquisicion;
    private LocalDate fechaRecepcionConforme;
    private String numeroInventario;
    
    private String pacienteRun;
    private String pacienteNombre;
    private LocalDate fechaImplantacion;
    private String profesionalImplantador;
    private String procedimiento;
    
    private String estado;
    private String observaciones;
    private Boolean activo;
    
    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaActualizacion;
    private String creadoPor;
    private String actualizadoPor;
    
    public static TrazabilidadResponse from(TrazabilidadDM t) {
        return TrazabilidadResponse.builder()
                .id(t.getId())
                .articuloId(t.getArticulo().getId())
                .sku(t.getArticulo().getSku())
                .nombreArticulo(t.getArticulo().getNombreTecnico())
                .categoriaCodigo(t.getArticulo().getCategoria().getCodigo())
                .loteId(t.getLote() != null ? t.getLote().getId() : null)
                .numeroLote(t.getNumeroLote())
                .fechaVencimiento(t.getFechaVencimiento())
                .nombreProducto(t.getNombreProducto())
                .rutProveedor(t.getRutProveedor())
                .nombreProveedor(t.getNombreProveedor())
                .numeroFactura(t.getNumeroFactura())
                .numeroGuia(t.getNumeroGuia())
                .modelo(t.getModelo())
                .numeroSerie(t.getNumeroSerie())
                .estadoDm(t.getEstadoDm())
                .numeroRegistroSanitario(t.getNumeroRegistroSanitario())
                .formaAdquisicion(t.getFormaAdquisicion())
                .fechaRecepcionConforme(t.getFechaRecepcionConforme())
                .numeroInventario(t.getNumeroInventario())
                .pacienteRun(t.getPacienteRun())
                .pacienteNombre(t.getPacienteNombre())
                .fechaImplantacion(t.getFechaImplantacion())
                .profesionalImplantador(t.getProfesionalImplantador())
                .procedimiento(t.getProcedimiento())
                .estado(t.getEstado())
                .observaciones(t.getObservaciones())
                .activo(t.getActivo())
                .fechaCreacion(t.getFechaCreacion())
                .fechaActualizacion(t.getFechaActualizacion())
                .creadoPor(t.getCreadoPor())
                .actualizadoPor(t.getActualizadoPor())
                .build();
    }
}