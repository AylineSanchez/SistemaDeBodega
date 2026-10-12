package cl.dsm.inventario.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "pedidos_internos")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PedidoInterno {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "numero_pedido", nullable = false, unique = true, length = 30)
    private String numeroPedido;
    
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "centro_costo_id", nullable = false)
    private CentroCosto centroCosto;
    
    @Column(name = "solicitante_rut", nullable = false, length = 12)
    private String solicitanteRut;
    
    @Column(name = "solicitante_nombre", nullable = false, length = 150)
    private String solicitanteNombre;
    
    @Column(name = "solicitante_cargo", length = 100)
    private String solicitanteCargo;
    
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String prioridad = "NORMAL";
    
    @Column(name = "fecha_solicitud", nullable = false, updatable = false)
    private LocalDateTime fechaSolicitud;
    
    @Column(name = "fecha_requerida")
    private LocalDate fechaRequerida;
    
    @Column(name = "fecha_despacho")
    private LocalDateTime fechaDespacho;
    
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String estado = "PENDIENTE";
    
    @Column(name = "jefe_servicio_rut", length = 12)
    private String jefeServicioRut;
    
    @Column(name = "jefe_servicio_nombre", length = 150)
    private String jefeServicioNombre;
    
    @Column(name = "jefe_bodega_rut", length = 12)
    private String jefeBodegaRut;
    
    @Column(name = "jefe_bodega_nombre", length = 150)
    private String jefeBodegaNombre;
    
    @Column(name = "receptor_rut", length = 12)
    private String receptorRut;
    
    @Column(name = "receptor_nombre", length = 150)
    private String receptorNombre;
    
    @Column(name = "firma_receptor_fecha")
    private LocalDateTime firmaReceptorFecha;
    
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
    
    @OneToMany(mappedBy = "pedido", fetch = FetchType.LAZY,
               cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<PedidoDetalle> detalles = new ArrayList<>();
    
    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        this.fechaCreacion = now;
        this.fechaActualizacion = now;
        this.fechaSolicitud = now;
        if (this.activo == null) this.activo = true;
        if (this.estado == null) this.estado = "PENDIENTE";
        if (this.prioridad == null) this.prioridad = "NORMAL";
    }
    
    @PreUpdate
    public void preUpdate() {
        this.fechaActualizacion = LocalDateTime.now();
    }
}