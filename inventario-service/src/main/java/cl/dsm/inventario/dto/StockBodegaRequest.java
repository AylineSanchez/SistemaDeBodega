package cl.dsm.inventario.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class StockBodegaRequest {
    
    @NotNull(message = "La bodega es obligatoria")
    private Integer bodegaId;
    
    @NotNull(message = "El stock inicial es obligatorio")
    @Min(value = 0, message = "El stock no puede ser negativo")
    private Integer stockActual = 0;
    
    private String ubicacionFisica;
    private String estante;
    private String nivel;
}