package cl.dsm.inventario.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PedidoDetalleRequest {
    
    @NotNull(message = "El artículo es obligatorio")
    private Long articuloId;
    
    @NotNull(message = "La cantidad es obligatoria")
    @Min(value = 1, message = "La cantidad debe ser al menos 1")
    private Integer cantidadSolicitada;
    
    private String observaciones;
}