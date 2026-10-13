package cl.dsm.inventario.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ConfiguracionUpdateRequest {

    @NotBlank(message = "La clave es obligatoria")
    private String clave;

    @NotNull(message = "El valor es obligatorio")
    private String valor;
}