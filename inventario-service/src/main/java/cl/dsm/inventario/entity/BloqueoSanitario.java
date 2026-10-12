package cl.dsm.inventario.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "bloqueos_sanitarios")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class BloqueoSanitario {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "numero_bloqueo", nullable = false, unique = true, length = 30)
    private String numeroBloqueo;
    
    // ============================================================
    // LOTE AFECTADO
    // ============================================================
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "lote_id", nullable = false)
    private Lote lote;
    
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "articulo_id", nullable = false)
    private Articulo articulo;
    
    @Column(name = "numero_lote", nullable = false, length = 50)
    private String numeroLote;
    
    @Column(name = "nombre_producto", nullable = false, length = 200)
    private String nombreProducto;
    
    @Column(nullable = false, length = 50)
    private String sku;
    
    @Column(name = "fecha_vencimiento")
    private LocalDate fechaVencimiento;
    
    @Column(name = "registro_isp", length = 50)
    private String registroIsp;
    
    // ============================================================
    // ALERTA ISP
    // ============================================================
    @Column(name = "tipo_alerta", nullable = false, length = 30)
    private String tipoAlerta;
    // ALERTA_ISP | RETIRO_MERCADO | DEFECTO_FABRICACION | CONTAMINACION | OTRO
    
    @Column(name = "numero_oficio_isp", length = 50)
    private String numeroOficioIsp;
    
    @Column(name = "fecha_alerta", nullable = false)
    private LocalDate fechaAlerta;
    
    @Column(name = "descripcion_alerta", columnDefinition = "TEXT")
    private String descripcionAlerta;
    
    @Column(nullable = false, columnDefinition = "TEXT")
    private String justificacion;
    
    // ============================================================
    // QUIÉN BLOQUEÓ
    // ============================================================
    @Column(name = "bloqueado_por_rut", nullable = false, length = 12)
    private String bloqueadoPorRut;
    
    @Column(name = "bloqueado_por_nombre", nullable = false, length = 150)
    private String bloqueadoPorNombre;
    
    @Column(name = "fecha_bloqueo", nullable = false, updatable = false)
    private LocalDateTime fechaBloqueo;
    
    // ============================================================
    // IMPACTO
    // ============================================================
    @Column(name = "stock_afectado", nullable = false)
    @Builder.Default
    private Integer stockAfectado = 0;
    
    @Column(name = "bodegas_afectadas", nullable = false)
    @Builder.Default
    private Integer bodegasAfectadas = 0;
    
    // ============================================================
    // ESTADO
    // ============================================================
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String estado = "ACTIVO";   // ACTIVO | LEVANTADO | PARCIAL
    
    // ============================================================
    // LEVANTAMIENTO
    // ============================================================
    @Column(name = "levantado_por_rut", length = 12)
    private String levantadoPorRut;
    
    @Column(name = "levantado_por_nombre", length = 150)
    private String levantadoPorNombre;
    
    @Column(name = "fecha_levantamiento")
    private LocalDateTime fechaLevantamiento;
    
    @Column(name = "motivo_levantamiento", columnDefinition = "TEXT")
    private String motivoLevantamiento;
    
    // ============================================================
    // CONTROL
    // ============================================================
    @Column(nullable = false)
    @Builder.Default
    private Boolean activo = true;
    
    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private LocalDateTime fechaCreacion;
    
    @Column(name = "fecha_actualizacion", nullable = false)
    private LocalDateTime fechaActualizacion;
    
    @OneToMany(mappedBy = "bloqueo", fetch = FetchType.LAZY,
               cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<BloqueoBodega> bodegas = new ArrayList<>();
    
    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        this.fechaCreacion = now;
        this.fechaActualizacion = now;
        this.fechaBloqueo = now;
        if (this.activo == null) this.activo = true;
        if (this.estado == null) this.estado = "ACTIVO";
    }
    
    @PreUpdate
    public void preUpdate() {
        this.fechaActualizacion = LocalDateTime.now();
    }
}