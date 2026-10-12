package cl.dsm.inventario.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.time.LocalDate;

@Data
public class BloqueoRequest {
    
    @NotNull(message = "El lote es obligatorio")
    private Long loteId;
    
    @NotBlank(message = "El tipo de alerta es obligatorio")
    @Pattern(regexp = "ALERTA_ISP|RETIRO_MERCADO|DEFECTO_FABRICACION|CONTAMINACION|OTRO",
             message = "Tipo de alerta inválido")
    private String tipoAlerta;
    
    @Size(max = 50)
    private String numeroOficioIsp;
    
    @NotNull(message = "La fecha de alerta es obligatoria")
    @PastOrPresent(message = "La fecha de alerta no puede ser futura")
    private LocalDate fechaAlerta;
    
    private String descripcionAlerta;
    
    @NotBlank(message = "La justificación es obligatoria")
    @Size(min = 10, max = 2000, message = "La justificación debe tener entre 10 y 2000 caracteres")
    private String justificacion;
}