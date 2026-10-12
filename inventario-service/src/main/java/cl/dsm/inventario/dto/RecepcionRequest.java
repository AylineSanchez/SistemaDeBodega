package cl.dsm.inventario.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Data
public class RecepcionRequest {
    
    // ============================================================
    // DOCUMENTO
    // ============================================================
    @NotBlank(message = "El tipo de documento es obligatorio")
    @Pattern(regexp = "FACTURA|GUIA_DESPACHO", message = "Tipo de documento inválido")
    private String tipoDocumento;
    
    @NotBlank(message = "El número de documento es obligatorio")
    @Size(max = 50)
    private String numeroDocumento;
    
    @Size(max = 50)
    private String numeroOrdenCompra;
    
    @Size(max = 50)
    private String numeroGuiaDespacho;
    
    // ============================================================
    // PROVEEDOR
    // ============================================================
    @NotBlank(message = "El RUT del proveedor es obligatorio")
    @Size(max = 12)
    private String rutProveedor;
    
    @NotBlank(message = "El nombre del proveedor es obligatorio")
    @Size(max = 200)
    private String nombreProveedor;
    
    @Size(max = 30)
    private String telefonoProveedor;
    
    @Size(max = 255)
    private String direccionProveedor;
    
    @Size(max = 50)
    private String tipoProveedor;
    
    // ============================================================
    // FECHAS
    // ============================================================
    @NotNull(message = "La fecha de emisión es obligatoria")
    @PastOrPresent(message = "La fecha de emisión no puede ser futura")
    private LocalDate fechaEmision;
    
    @NotNull(message = "La fecha de recepción es obligatoria")
    @PastOrPresent(message = "La fecha de recepción no puede ser futura")
    private LocalDate fechaRecepcion;
    
    // ============================================================
    // BODEGA DESTINO
    // ============================================================
    @NotNull(message = "La bodega es obligatoria")
    private Integer bodegaId;
    
    // ============================================================
    // RECEPTOR
    // ============================================================
    @Size(max = 12)
    private String receptorRut;
    
    @Size(max = 150)
    private String receptorNombre;
    
    // ============================================================
    // TOTALES
    // ============================================================
    @DecimalMin(value = "0.0", message = "El monto no puede ser negativo")
    private java.math.BigDecimal montoNeto = java.math.BigDecimal.ZERO;
    
    @DecimalMin(value = "0.0", message = "El IVA no puede ser negativo")
    private java.math.BigDecimal montoIva = java.math.BigDecimal.ZERO;
    
    @DecimalMin(value = "0.0", message = "El total no puede ser negativo")
    private java.math.BigDecimal montoTotal = java.math.BigDecimal.ZERO;
    
    private String observaciones;
    
    // ============================================================
    // DETALLES
    // ============================================================
    @NotEmpty(message = "Debe agregar al menos un producto")
    @Valid
    private List<RecepcionDetalleRequest> detalles = new ArrayList<>();
}