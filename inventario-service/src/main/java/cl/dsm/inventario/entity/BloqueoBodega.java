package cl.dsm.inventario.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "bloqueo_bodegas")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class BloqueoBodega {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bloqueo_id", nullable = false)
    private BloqueoSanitario bloqueo;
    
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "bodega_id", nullable = false)
    private Bodega bodega;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_id")
    private StockPorBodega stock;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_lote_id")
    private StockLote stockLote;
    
    @Column(name = "cantidad_bloqueada", nullable = false)
    @Builder.Default
    private Integer cantidadBloqueada = 0;
    
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String estado = "BLOQUEADO";   // BLOQUEADO | DESBLOQUEADO
    
    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private LocalDateTime fechaCreacion;
    
    @Column(name = "fecha_actualizacion", nullable = false)
    private LocalDateTime fechaActualizacion;
    
    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        this.fechaCreacion = now;
        this.fechaActualizacion = now;
        if (this.estado == null) this.estado = "BLOQUEADO";
    }
    
    @PreUpdate
    public void preUpdate() {
        this.fechaActualizacion = LocalDateTime.now();
    }
}