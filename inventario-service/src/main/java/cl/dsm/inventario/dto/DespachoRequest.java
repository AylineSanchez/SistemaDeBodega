package cl.dsm.inventario.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class DespachoRequest {
    
    // Opcional: si viene de un pedido existente
    private Long pedidoId;
    
    @NotNull(message = "El centro de costo es obligatorio")
    private Integer centroCostoId;
    
    @NotNull(message = "La bodega de origen es obligatoria")
    private Integer bodegaId;
    
    @NotBlank(message = "El nombre del receptor es obligatorio")
    @Size(max = 150)
    private String receptorNombre;
    
    @NotBlank(message = "El RUT del receptor es obligatorio")
    @Size(max = 12)
    private String receptorRut;
    
    @Size(max = 100)
    private String receptorCargo;
    
    @Size(max = 50)
    private String guiaInterna;
    
    private String observaciones;
    
    @NotEmpty(message = "Debe agregar al menos un producto")
    @Valid
    private List<DespachoDetalleRequest> detalles = new ArrayList<>();
}