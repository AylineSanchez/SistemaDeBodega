package cl.dsm.auth.repository;

import cl.dsm.auth.entity.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {
    
    Optional<PasswordResetToken> findByToken(String token);
    
    @Query("SELECT COUNT(t) FROM PasswordResetToken t " +
           "WHERE t.usuario.id = :usuarioId AND t.fechaCreacion > :desde")
    long contarSolicitudesRecientes(@Param("usuarioId") Long usuarioId,
                                     @Param("desde") LocalDateTime desde);
    
    @Modifying
    @Query("UPDATE PasswordResetToken t SET t.usado = true, t.fechaUso = :ahora " +
           "WHERE t.usuario.id = :usuarioId AND t.usado = false")
    void invalidarTokensAnteriores(@Param("usuarioId") Long usuarioId,
                                    @Param("ahora") LocalDateTime ahora);
    
    @Modifying
    @Query("DELETE FROM PasswordResetToken t WHERE t.expiraEn < :limite")
    int eliminarExpirados(@Param("limite") LocalDateTime limite);
}