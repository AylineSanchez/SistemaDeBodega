package cl.dsm.auth.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "sesiones")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Sesion {
    
    @Id
    @GeneratedValue
    private UUID id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;
    
    @Column(name = "token_jwt", nullable = false, columnDefinition = "TEXT")
    private String tokenJwt;
    
    @Column(name = "ip_address", length = 45)
    private String ipAddress;
    
    @Column(name = "user_agent", columnDefinition = "TEXT")
    private String userAgent;
    
    @Column(name = "fecha_inicio", nullable = false)
    private LocalDateTime fechaInicio;
    
    @Column(name = "ultima_actividad", nullable = false)
    private LocalDateTime ultimaActividad;
    
    @Column(nullable = false)
    private Boolean activa = true;
    
    @Column(name = "fecha_cierre")
    private LocalDateTime fechaCierre;
    
    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        this.fechaInicio = now;
        this.ultimaActividad = now;
        if (this.activa == null) this.activa = true;
    }
}