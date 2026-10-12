package cl.dsm.inventario.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "recepciones")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Recepcion {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "numero_recepcion", nullable = false, unique = true, length = 30)
    private String numeroRecepcion;
    
    // ============================================================
    // DOCUMENTO
    // ============================================================
    @Column(name = "tipo_documento", nullable = false, length = 20)
    private String tipoDocumento;   // FACTURA | GUIA_DESPACHO
    
    @Column(name = "numero_documento", nullable = false, length = 50)
    private String numeroDocumento;
    
    @Column(name = "numero_orden_compra", length = 50)
    private String numeroOrdenCompra;
    
    @Column(name = "numero_guia_despacho", length = 50)
    private String numeroGuiaDespacho;
    
    // ============================================================
    // PROVEEDOR
    // ============================================================
    @Column(name = "rut_proveedor", nullable = false, length = 12)
    private String rutProveedor;
    
    @Column(name = "nombre_proveedor", nullable = false, length = 200)
    private String nombreProveedor;
    
    @Column(name = "telefono_proveedor", length = 30)
    private String telefonoProveedor;
    
    @Column(name = "direccion_proveedor", length = 255)
    private String direccionProveedor;
    
    @Column(name = "tipo_proveedor", length = 50)
    private String tipoProveedor;
    
    // ============================================================
    // FECHAS
    // ============================================================
    @Column(name = "fecha_emision", nullable = false)
    private LocalDate fechaEmision;
    
    @Column(name = "fecha_recepcion", nullable = false)
    private LocalDate fechaRecepcion;
    
    @Column(name = "fecha_hora_ingreso", nullable = false, updatable = false)
    private LocalDateTime fechaHoraIngreso;
    
    // ============================================================
    // BODEGA DESTINO
    // ============================================================
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "bodega_id", nullable = false)
    private Bodega bodega;
    
    // ============================================================
    // RECEPTOR
    // ============================================================
    @Column(name = "receptor_rut", length = 12)
    private String receptorRut;
    
    @Column(name = "receptor_nombre", length = 150)
    private String receptorNombre;
    
    @Column(name = "receptor_fecha")
    private LocalDateTime receptorFecha;
    
    // ============================================================
    // TOTALES
    // ============================================================
    @Column(name = "monto_neto", precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal montoNeto = BigDecimal.ZERO;
    
    @Column(name = "monto_iva", precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal montoIva = BigDecimal.ZERO;
    
    @Column(name = "monto_total", precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal montoTotal = BigDecimal.ZERO;
    
    // ============================================================
    // ESTADO
    // ============================================================
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String estado = "BORRADOR";   // BORRADOR | CONFIRMADA | ANULADA | PARCIAL
    
    @Column(columnDefinition = "TEXT")
    private String observaciones;
    
    @Column(nullable = false)
    @Builder.Default
    private Boolean activo = true;
    
    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private LocalDateTime fechaCreacion;
    
    @Column(name = "fecha_actualizacion", nullable = false)
    private LocalDateTime fechaActualizacion;
    
    @Column(name = "creado_por", length = 12)
    private String creadoPor;
    
    @Column(name = "actualizado_por", length = 12)
    private String actualizadoPor;
    
    // ============================================================
    // RELACIONES
    // ============================================================
    @OneToMany(mappedBy = "recepcion", fetch = FetchType.LAZY,
               cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<RecepcionDetalle> detalles = new ArrayList<>();
    
    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        this.fechaCreacion = now;
        this.fechaActualizacion = now;
        this.fechaHoraIngreso = now;
        if (this.activo == null) this.activo = true;
        if (this.estado == null) this.estado = "BORRADOR";
    }
    
    @PreUpdate
    public void preUpdate() {
        this.fechaActualizacion = LocalDateTime.now();
    }
}