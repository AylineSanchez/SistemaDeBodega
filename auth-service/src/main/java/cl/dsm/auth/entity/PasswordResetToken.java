package cl.dsm.auth.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "password_reset_tokens")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PasswordResetToken {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;
    
    @Column(nullable = false, unique = true, length = 64)
    private String token;
    
    @Column(name = "expira_en", nullable = false)
    private LocalDateTime expiraEn;
    
    @Column(nullable = false)
    private Boolean usado = false;
    
    @Column(name = "fecha_uso")
    private LocalDateTime fechaUso;
    
    @Column(name = "ip_solicitud", length = 45)
    private String ipSolicitud;
    
    @Column(name = "user_agent", columnDefinition = "TEXT")
    private String userAgent;
    
    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private LocalDateTime fechaCreacion;
    
    @PrePersist
    public void prePersist() {
        this.fechaCreacion = LocalDateTime.now();
        if (this.usado == null) this.usado = false;
    }
    
    public boolean isExpirado() {
        return LocalDateTime.now().isAfter(this.expiraEn);
    }
    
    public boolean isValido() {
        return !this.usado && !isExpirado();
    }
}