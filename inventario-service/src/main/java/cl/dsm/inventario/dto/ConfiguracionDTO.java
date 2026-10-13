package cl.dsm.inventario.dto;

import cl.dsm.inventario.entity.Configuracion;
import lombok.*;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfiguracionDTO {
    private String clave;
    private String valor;
    private String descripcion;
    private String tipo;
    private LocalDateTime fechaActualizacion;
    private String actualizadoPor;

    public static ConfiguracionDTO from(Configuracion c) {
        return ConfiguracionDTO.builder()
                .clave(c.getClave())
                .valor(c.getValor())
                .descripcion(c.getDescripcion())
                .tipo(c.getTipo())
                .fechaActualizacion(c.getFechaActualizacion())
                .actualizadoPor(c.getActualizadoPor())
                .build();
    }
}