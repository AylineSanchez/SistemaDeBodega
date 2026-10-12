package cl.dsm.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class MfaVerifyRequest {
    
    @NotBlank(message = "El token de sesión es obligatorio")
    private String sessionToken;
    
    @NotBlank(message = "El código MFA es obligatorio")
    @Pattern(regexp = "\\d{6}", message = "El código debe ser de 6 dígitos")
    private String codigo;
}