package cl.dsm.inventario.dto;

import lombok.*;

import java.time.LocalDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class UsuarioDTO {
    private Long id;
    private String rut;
    private String nombreCompleto;
    private String email;
    private String rol;
    private String dependencia;
    private Boolean activo;
    private Boolean bloqueado;
    private Integer intentosFallidos;
    private Boolean mfaEnabled;
    private LocalDateTime ultimoAcceso;
    private LocalDateTime fechaCreacion;
}