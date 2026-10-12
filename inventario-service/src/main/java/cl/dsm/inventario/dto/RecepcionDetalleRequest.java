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

    private Integer cantidadPendiente;

    private BigDecimal valorUnitario;

    private BigDecimal montoTotal;

    private String numeroLote;

    private LocalDate fechaVencimiento;

    private String registroIsp;

    private String ubicacionFisica;

    private String estante;

    private String nivel;

    private String observaciones;

    // ⚡ NUEVO: bodega destino de este detalle
    private Integer bodegaId;
}