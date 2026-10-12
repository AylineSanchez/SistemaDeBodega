package cl.dsm.auth.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "usuarios")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Usuario {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, unique = true, length = 12)
    private String rut;
    
    @Column(name = "nombre_completo", nullable = false, length = 150)
    private String nombreCompleto;
    
    @Column(nullable = false, unique = true, length = 150)
    private String email;
    
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;
    
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "rol_id", nullable = false)
    private Rol rol;
    
    /**
     * Solo aplica para rol ENCARGADO_DEPENDENCIA.
     * Ej: "Farmacia Central", "SAR Santa Cruz", "Dental CESFAM"
     */
    @Column(length = 100)
    private String dependencia;
    
    @Column(nullable = false)
    private Boolean activo = true;
    
    @Column(nullable = false)
    private Boolean bloqueado = false;
    
    @Column(name = "intentos_fallidos", nullable = false)
    private Integer intentosFallidos = 0;
    
    @Column(name = "mfa_enabled", nullable = false)
    private Boolean mfaEnabled = true;
    
    @Column(name = "mfa_secret", length = 64)
    private String mfaSecret;
    
    @Column(name = "ultimo_codigo_mfa", length = 10)
    private String ultimoCodigoMfa;
    
    @Column(name = "ultimo_codigo_mfa_expira")
    private LocalDateTime ultimoCodigoMfaExpira;
    
    @Column(name = "ultimo_acceso")
    private LocalDateTime ultimoAcceso;
    
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
        if (this.intentosFallidos == null) this.intentosFallidos = 0;
        if (this.mfaEnabled == null) this.mfaEnabled = true;
    }
    
    @PreUpdate
    public void preUpdate() {
        this.fechaActualizacion = LocalDateTime.now();
    }
}