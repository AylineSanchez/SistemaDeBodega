package cl.dsm.inventario.dto;

import cl.dsm.inventario.entity.Bodega;
import lombok.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class BodegaDTO {
    private Integer id;
    private String codigo;
    private String nombre;
    private String descripcion;
    private String dependencia;
    private String ubicacion;
    private Boolean esPrincipal;
    private Boolean activa;
    
    public static BodegaDTO from(Bodega b) {
        return BodegaDTO.builder()
                .id(b.getId())
                .codigo(b.getCodigo())
                .nombre(b.getNombre())
                .descripcion(b.getDescripcion())
                .dependencia(b.getDependencia())
                .ubicacion(b.getUbicacion())
                .esPrincipal(b.getEsPrincipal())
                .activa(b.getActiva())
                .build();
    }
}