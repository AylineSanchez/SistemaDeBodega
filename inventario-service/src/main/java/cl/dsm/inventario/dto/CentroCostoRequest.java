package cl.dsm.inventario.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class CentroCostoRequest {

    @NotBlank(message = "El código es obligatorio")
    @Size(max = 20, message = "El código no puede exceder 20 caracteres")
    private String codigo;

    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 150, message = "El nombre no puede exceder 150 caracteres")
    private String nombre;

    @Size(max = 255)
    private String descripcion;

    @Pattern(
        regexp = "FARMACIA|SAR|DENTAL|VACUNATORIO|LABORATORIO|BODEGA|OTRO|",
        message = "Tipo inválido"
    )
    private String tipo;

    @Size(max = 12)
    private String responsableRut;

    @Size(max = 150)
    private String responsableNombre;
}