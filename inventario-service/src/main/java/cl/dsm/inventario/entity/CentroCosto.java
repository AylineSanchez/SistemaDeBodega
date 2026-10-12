package cl.dsm.inventario.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "centros_costo")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CentroCosto {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    
    @Column(nullable = false, unique = true, length = 20)
    private String codigo;
    
    @Column(nullable = false, length = 150)
    private String nombre;
    
    @Column(length = 255)
    private String descripcion;
    
    @Column(length = 30)
    private String tipo;
    
    @Column(name = "responsable_rut", length = 12)
    private String responsableRut;
    
    @Column(name = "responsable_nombre", length = 150)
    private String responsableNombre;
    
    @Column(nullable = false)
    @Builder.Default
    private Boolean activo = true;
    
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
    }
    
    @PreUpdate
    public void preUpdate() {
        this.fechaActualizacion = LocalDateTime.now();
    }
}