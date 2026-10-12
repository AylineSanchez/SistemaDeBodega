package cl.dsm.auth.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "log_auditoria")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class LogAuditoria {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "usuario_rut", length = 12)
    private String usuarioRut;
    
    @Column(name = "usuario_nombre", length = 150)
    private String usuarioNombre;
    
    @Column(nullable = false, length = 50)
    private String accion;
    
    @Column(length = 50)
    private String modulo;
    
    @Column(columnDefinition = "TEXT")
    private String descripcion;
    
    @Column(name = "ip_address", length = 45)
    private String ipAddress;
    
    @Column(name = "user_agent", columnDefinition = "TEXT")
    private String userAgent;
    
    @Column(nullable = false, updatable = false)
    private LocalDateTime timestamp;
    
    @PrePersist
    public void prePersist() {
        this.timestamp = LocalDateTime.now();
    }
}