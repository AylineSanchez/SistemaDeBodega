package cl.dsm.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class LoginRequest {
    
    @NotBlank(message = "El RUT es obligatorio")
    @Size(max = 12, message = "El RUT no puede exceder 12 caracteres")
    private String rut;
    
    @NotBlank(message = "La contraseña es obligatoria")
    @Size(min = 1, max = 100, message = "La contraseña debe tener entre 1 y 100 caracteres")
    private String password;
}