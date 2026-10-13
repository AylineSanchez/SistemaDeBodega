package cl.dsm.inventario.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "configuracion")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Configuracion {

    @Id
    @Column(name = "clave", length = 100)
    private String clave;

    @Column(name = "valor", columnDefinition = "TEXT")
    private String valor;

    @Column(name = "descripcion", length = 255)
    private String descripcion;

    @Column(name = "tipo", length = 30)
    @Builder.Default
    private String tipo = "STRING";

    @Column(name = "fecha_actualizacion", nullable = false)
    private LocalDateTime fechaActualizacion;

    @Column(name = "actualizado_por", length = 12)
    private String actualizadoPor;

    @PrePersist
    @PreUpdate
    public void preUpdate() {
        this.fechaActualizacion = LocalDateTime.now();
    }
}
