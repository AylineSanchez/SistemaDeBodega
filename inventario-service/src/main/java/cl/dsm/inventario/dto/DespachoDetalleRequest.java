package cl.dsm.inventario.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class DespachoDetalleRequest {
    
    @NotNull(message = "El artículo es obligatorio")
    private Long articuloId;
    
    @NotNull(message = "La cantidad es obligatoria")
    @Min(value = 1, message = "La cantidad debe ser al menos 1")
    private Integer cantidad;
    
    /**
     * Opcional: si se especifica, se usará ese lote.
     * Si no, el sistema aplica FEFO automáticamente.
     * 
     * ⚠️ Si el lote especificado no es el más próximo a vencer, el
     * sistema bloqueará el despacho (RN-001).
     */
    private Long loteId;
    
    private String observaciones;
}