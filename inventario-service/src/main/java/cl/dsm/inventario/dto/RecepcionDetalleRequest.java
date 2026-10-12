package cl.dsm.inventario.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class RecepcionDetalleRequest {
    
    @NotNull(message = "El artículo es obligatorio")
    private Long articuloId;
    
    @NotNull(message = "La cantidad es obligatoria")
    @Min(value = 1, message = "La cantidad debe ser al menos 1")
    private Integer cantidadRecibida;
    
    @Min(value = 0)
    private Integer cantidadPendiente = 0;
    
    @DecimalMin(value = "0.0")
    private BigDecimal valorUnitario = BigDecimal.ZERO;
    
    private BigDecimal montoTotal = BigDecimal.ZERO;
    
    @Size(max = 50)
    private String numeroLote;
    
    @Future(message = "La fecha de vencimiento debe ser futura")
    private LocalDate fechaVencimiento;
    
    @Size(max = 50)
    private String registroIsp;
    
    @Size(max = 150)
    private String ubicacionFisica;
    
    @Size(max = 50)
    private String estante;
    
    @Size(max = 50)
    private String nivel;
    
    private String observaciones;
}