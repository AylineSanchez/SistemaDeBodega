package cl.dsm.inventario.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "stock_por_bodega",
       uniqueConstraints = @UniqueConstraint(
           name = "uk_articulo_bodega",
           columnNames = {"articulo_id", "bodega_id"}
       ))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class StockPorBodega {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "articulo_id", nullable = false)
    private Articulo articulo;
    
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "bodega_id", nullable = false)
    private Bodega bodega;
    
    @Column(name = "stock_actual", nullable = false)
    @Builder.Default
    private Integer stockActual = 0;
    
    @Column(name = "ubicacion_fisica", length = 150)
    private String ubicacionFisica;
    
    @Column(length = 50)
    private String estante;
    
    @Column(length = 50)
    private String nivel;
    
    /**
     * Código QR único para identificar artículo+bodega.
     * Formato: DSM-{BODEGA}-{SKU}-{HASH6}
     * Ejemplo: DSM-B2-DM-03865-A1B2C3
     */
    @Column(name = "codigo_qr", length = 100, unique = true)
    private String codigoQr;
    
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
    
    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        this.fechaCreacion = now;
        this.fechaActualizacion = now;
        if (this.stockActual == null) this.stockActual = 0;
        if (this.activo == null) this.activo = true;
    }
    
    @PreUpdate
    public void preUpdate() {
        this.fechaActualizacion = LocalDateTime.now();
    }
}