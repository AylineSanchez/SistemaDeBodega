package cl.dsm.inventario.dto;

import cl.dsm.inventario.entity.CentroCosto;
import lombok.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class CentroCostoDTO {
    
    private Integer id;
    private String codigo;
    private String nombre;
    private String descripcion;
    private String tipo;
    private String responsableRut;
    private String responsableNombre;
    private Boolean activo;
    
    public static CentroCostoDTO from(CentroCosto c) {
        return CentroCostoDTO.builder()
                .id(c.getId())
                .codigo(c.getCodigo())
                .nombre(c.getNombre())
                .descripcion(c.getDescripcion())
                .tipo(c.getTipo())
                .responsableRut(c.getResponsableRut())
                .responsableNombre(c.getResponsableNombre())
                .activo(c.getActivo())
                .build();
    }
}