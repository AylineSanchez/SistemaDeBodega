package cl.dsm.inventario.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Data
public class PedidoRequest {
    
    @NotNull(message = "El centro de costo es obligatorio")
    private Integer centroCostoId;
    
    @NotBlank(message = "El nombre del solicitante es obligatorio")
    @Size(max = 150)
    private String solicitanteNombre;
    
    @NotBlank(message = "El RUT del solicitante es obligatorio")
    @Size(max = 12)
    private String solicitanteRut;
    
    @Size(max = 100)
    private String solicitanteCargo;
    
    @Pattern(regexp = "URGENTE|NORMAL", message = "Prioridad debe ser URGENTE o NORMAL")
    private String prioridad = "NORMAL";
    
    private LocalDate fechaRequerida;
    
    @Size(max = 100)
    private String jefeServicioNombre;
    
    @Size(max = 12)
    private String jefeServicioRut;
    
    private String observaciones;
    
    @NotEmpty(message = "Debe agregar al menos un producto al pedido")
    @Valid
    private List<PedidoDetalleRequest> detalles = new ArrayList<>();
}