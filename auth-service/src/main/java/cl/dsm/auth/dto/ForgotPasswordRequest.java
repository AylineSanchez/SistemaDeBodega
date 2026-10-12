package cl.dsm.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ForgotPasswordRequest {
    
    @NotBlank(message = "Debe ingresar su RUT o email")
    @Size(max = 150, message = "El identificador es demasiado largo")
    private String identificador;
}