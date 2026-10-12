package cl.dsm.inventario.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class UsuarioRequest {
    
    @NotBlank(message = "El RUT es obligatorio")
    @Size(max = 12, message = "El RUT no puede exceder 12 caracteres")
    private String rut;
    
    @NotBlank(message = "El nombre completo es obligatorio")
    @Size(max = 150, message = "El nombre no puede exceder 150 caracteres")
    private String nombreCompleto;
    
    @NotBlank(message = "El email es obligatorio")
    @Email(message = "El email debe ser válido")
    @Size(max = 150, message = "El email no puede exceder 150 caracteres")
    private String email;
    
    @NotBlank(message = "El rol es obligatorio")
    @Pattern(
        regexp = "JEFATURA|OPERATIVO|AUDITOR|ENCARGADO_DEPENDENCIA",
        message = "Rol no válido"
    )
    private String rol;
    
    @Size(max = 100, message = "La dependencia no puede exceder 100 caracteres")
    private String dependencia;  // Solo aplica para ENCARGADO_DEPENDENCIA
}