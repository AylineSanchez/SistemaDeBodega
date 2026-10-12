package cl.dsm.inventario.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ArticuloRequest {
    
    // Identificación
    @Size(max = 50, message = "El SKU no puede exceder 50 caracteres")
    private String sku;  // opcional: se autogenera si viene vacío
    
    @NotBlank(message = "El nombre técnico es obligatorio")
    @Size(max = 200, message = "El nombre no puede exceder 200 caracteres")
    private String nombreTecnico;
    
    @Size(max = 150)
    private String presentacion;
    
    @Size(max = 100)
    private String concentracion;
    
    @NotNull(message = "La categoría es obligatoria")
    private Integer categoriaId;
    
    @Size(max = 100)
    private String subcategoria;
    
    private String descripcion;
    
    // Logísticos
    @NotBlank(message = "La unidad de medida es obligatoria")
    private String unidadMedidaBase;
    
    @Size(max = 30)
    private String unidadCompra;
    
    @Min(value = 1, message = "El factor de conversión debe ser al menos 1")
    private Integer factorConversion = 1;
    
    @Min(value = 0, message = "El stock mínimo no puede ser negativo")
    private Integer stockMinimo = 0;
    
    @Min(value = 0, message = "El stock máximo no puede ser negativo")
    private Integer stockMaximo;
    
    @Min(value = 0, message = "El stock de seguridad no puede ser negativo")
    private Integer stockSeguridad = 0;
    
    @Min(value = 0)
    private Integer puntoReorden;
    
    // Sanitarios
    private Boolean requiereLote = false;
    private Boolean requiereVencimiento = false;
    private String registroIsp;
    private Boolean esDispositivoMedico = false;
    private Boolean esInsumoCritico = false;
    private String condicionesAlmacenamiento;
    
    // Compras públicas
    @Size(max = 20)
    private String codigoUnspsc;
    
    @Size(max = 50)
    private String tipoProveedorHabitual;
    
    @Size(max = 50)
    private String codigoCenabast;
    
    @Size(max = 150)
    private String fabricante;
    
    @Size(max = 80)
    private String paisOrigen;
    
    // Control
    @DecimalMin(value = "0.0", message = "El precio no puede ser negativo")
    private BigDecimal precioPromedioPonderado = BigDecimal.ZERO;
    
    private String observaciones;
    
    // Stock inicial opcional (se crea en la bodega indicada)
    private Long stockInicial;
    private Integer bodegaId;
    private String ubicacionFisica;
}