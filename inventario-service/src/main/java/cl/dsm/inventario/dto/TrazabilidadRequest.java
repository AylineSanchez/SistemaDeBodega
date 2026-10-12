package cl.dsm.inventario.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.time.LocalDate;

@Data
public class TrazabilidadRequest {
    
    // ============================================================
    // NT 226 (RF-067)
    // ============================================================
    @NotNull(message = "El artículo es obligatorio")
    private Long articuloId;
    
    private Long loteId;
    
    @NotBlank(message = "El nombre del producto es obligatorio")
    @Size(max = 200)
    private String nombreProducto;
    
    @Size(max = 12)
    private String rutProveedor;
    
    @Size(max = 200)
    private String nombreProveedor;
    
    @Size(max = 50)
    private String numeroFactura;
    
    @Size(max = 50)
    private String numeroGuia;
    
    @Size(max = 100)
    private String modelo;
    
    @Size(max = 50)
    private String numeroLote;
    
    @Size(max = 50)
    private String numeroSerie;
    
    private LocalDate fechaVencimiento;
    
    // ============================================================
    // NT 247 (RF-068)
    // ============================================================
    @Pattern(regexp = "NUEVO|USADO|VENCIDO|DAÑADO|", message = "Estado del DM inválido")
    private String estadoDm;
    
    @Size(max = 50)
    private String numeroRegistroSanitario;
    
    @Size(max = 50)
    private String formaAdquisicion;
    
    private LocalDate fechaRecepcionConforme;
    
    @Size(max = 50)
    private String numeroInventario;
    
    // ============================================================
    // PACIENTE (implantables)
    // ============================================================
    @Size(max = 12)
    private String pacienteRun;
    
    @Size(max = 150)
    private String pacienteNombre;
    
    private LocalDate fechaImplantacion;
    
    @Size(max = 150)
    private String profesionalImplantador;
    
    @Size(max = 255)
    private String procedimiento;
    
    // ============================================================
    // ESTADO
    // ============================================================
    @Pattern(regexp = "EN_BODEGA|DESPACHADO|IMPLANTADO|VENCIDO|MERMA|BLOQUEADO|",
             message = "Estado inválido")
    private String estado;
    
    private String observaciones;
}