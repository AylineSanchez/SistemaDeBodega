package cl.dsm.inventario.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "despachos")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Despacho {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "numero_despacho", nullable = false, unique = true, length = 30)
    private String numeroDespacho;
    
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "pedido_id")
    private PedidoInterno pedido;
    
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "centro_costo_id", nullable = false)
    private CentroCosto centroCosto;
    
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "bodega_id", nullable = false)
    private Bodega bodega;
    
    @Column(name = "receptor_rut", nullable = false, length = 12)
    private String receptorRut;
    
    @Column(name = "receptor_nombre", nullable = false, length = 150)
    private String receptorNombre;
    
    @Column(name = "receptor_cargo", length = 100)
    private String receptorCargo;
    
    @Column(name = "fecha_despacho", nullable = false)
    private LocalDateTime fechaDespacho;
    
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String estado = "BORRADOR";
    
    @Column(name = "guia_interna", length = 50)
    private String guiaInterna;
    
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
    
    @OneToMany(mappedBy = "despacho", fetch = FetchType.LAZY,
               cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<DespachoDetalle> detalles = new ArrayList<>();
    
    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        this.fechaCreacion = now;
        this.fechaActualizacion = now;
        if (this.fechaDespacho == null) this.fechaDespacho = now;
        if (this.activo == null) this.activo = true;
        if (this.estado == null) this.estado = "BORRADOR";
    }
    
    @PreUpdate
    public void preUpdate() {
        this.fechaActualizacion = LocalDateTime.now();
    }
}