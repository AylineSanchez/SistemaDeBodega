package cl.dsm.inventario.dto;

import cl.dsm.inventario.entity.Categoria;
import lombok.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class CategoriaDTO {
    private Integer id;
    private String codigo;
    private String nombre;
    private String descripcion;
    private String color;
    private Integer orden;
    private Boolean activa;
    private Long totalArticulos;
    
    public static CategoriaDTO from(Categoria c) {
        return CategoriaDTO.builder()
                .id(c.getId())
                .codigo(c.getCodigo())
                .nombre(c.getNombre())
                .descripcion(c.getDescripcion())
                .color(c.getColor())
                .orden(c.getOrden())
                .activa(c.getActiva())
                .build();
    }
}