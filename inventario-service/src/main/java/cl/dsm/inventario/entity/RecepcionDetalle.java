package cl.dsm.inventario.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "recepcion_detalle")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RecepcionDetalle {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recepcion_id", nullable = false)
    private Recepcion recepcion;
    
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "articulo_id", nullable = false)
    private Articulo articulo;
    
    @Column(nullable = false, length = 50)
    private String sku;
    
    @Column(name = "nombre_producto", nullable = false, length = 200)
    private String nombreProducto;
    
    // ============================================================
    // CANTIDADES
    // ============================================================
    @Column(name = "cantidad_recibida", nullable = false)
    @Builder.Default
    private Integer cantidadRecibida = 0;
    
    @Column(name = "cantidad_pendiente", nullable = false)
    @Builder.Default
    private Integer cantidadPendiente = 0;
    
    @Column(name = "valor_unitario", precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal valorUnitario = BigDecimal.ZERO;
    
    @Column(name = "monto_total", precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal montoTotal = BigDecimal.ZERO;
    
    // ============================================================
    // SANITARIOS
    // ============================================================
    @Column(name = "numero_lote", length = 50)
    private String numeroLote;
    
    @Column(name = "fecha_vencimiento")
    private LocalDate fechaVencimiento;
    
    @Column(name = "registro_isp", length = 50)
    private String registroIsp;
    
    // ============================================================
    // UBICACIÓN
    // ============================================================
    @Column(name = "ubicacion_fisica", length = 150)
    private String ubicacionFisica;
    
    @Column(length = 50)
    private String estante;
    
    @Column(length = 50)
    private String nivel;
    
    // ============================================================
    // ESTADO
    // ============================================================
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String estado = "PENDIENTE";   // PENDIENTE | RECIBIDA | PARCIAL | RECHAZADA
    
    @Column(columnDefinition = "TEXT")
    private String observaciones;
    
    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private LocalDateTime fechaCreacion;
    
    @Column(name = "fecha_actualizacion", nullable = false)
    private LocalDateTime fechaActualizacion;
    
    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        this.fechaCreacion = now;
        this.fechaActualizacion = now;
        if (this.cantidadRecibida == null) this.cantidadRecibida = 0;
        if (this.cantidadPendiente == null) this.cantidadPendiente = 0;
    }
    
    @PreUpdate
    public void preUpdate() {
        this.fechaActualizacion = LocalDateTime.now();
    }
}