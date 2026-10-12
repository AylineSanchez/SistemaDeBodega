package cl.dsm.inventario.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "lotes",
       uniqueConstraints = @UniqueConstraint(
           name = "uk_lote_articulo",
           columnNames = {"articulo_id", "numero_lote"}
       ))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Lote {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "articulo_id", nullable = false)
    private Articulo articulo;
    
    @Column(name = "numero_lote", nullable = false, length = 50)
    private String numeroLote;
    
    @Column(name = "fecha_vencimiento")
    private LocalDate fechaVencimiento;
    
    @Column(name = "fecha_fabricacion")
    private LocalDate fechaFabricacion;
    
    @Column(name = "registro_isp", length = 50)
    private String registroIsp;
    
    @Column(length = 150)
    private String fabricante;
    
    @Column(name = "pais_origen", length = 80)
    private String paisOrigen;
    
    @Column(nullable = false)
    @Builder.Default
    private Boolean activo = true;
    
    @Column(nullable = false)
    @Builder.Default
    private Boolean bloqueado = false;
    
    @Column(name = "motivo_bloqueo", length = 255)
    private String motivoBloqueo;
    
    @Column(name = "fecha_bloqueo")
    private LocalDateTime fechaBloqueo;
    
    @Column(name = "bloqueado_por", length = 12)
    private String bloqueadoPor;
    
    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private LocalDateTime fechaCreacion;
    
    @Column(name = "fecha_actualizacion", nullable = false)
    private LocalDateTime fechaActualizacion;
    
    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        this.fechaCreacion = now;
        this.fechaActualizacion = now;
        if (this.activo == null) this.activo = true;
        if (this.bloqueado == null) this.bloqueado = false;
    }
    
    @PreUpdate
    public void preUpdate() {
        this.fechaActualizacion = LocalDateTime.now();
    }
    
    /**
     * Calcula los días hasta el vencimiento.
     * Negativo si ya venció.
     */
    public long getDiasParaVencer() {
        if (fechaVencimiento == null) return Long.MAX_VALUE;
        return java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), fechaVencimiento);
    }
}