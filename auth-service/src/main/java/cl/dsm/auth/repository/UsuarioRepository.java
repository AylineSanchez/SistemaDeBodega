package cl.dsm.auth.repository;

import cl.dsm.auth.entity.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
    
    Optional<Usuario> findByRut(String rut);
    
    Optional<Usuario> findByEmail(String email);
    
    boolean existsByRut(String rut);
    
    @Modifying
    @Query("UPDATE Usuario u SET u.intentosFallidos = u.intentosFallidos + 1 WHERE u.id = :id")
    void incrementarIntentosFallidos(@Param("id") Long id);
    
    @Modifying
    @Query("UPDATE Usuario u SET u.intentosFallidos = 0, u.bloqueado = false WHERE u.id = :id")
    void resetearIntentosFallidos(@Param("id") Long id);
    
    @Modifying
    @Query("UPDATE Usuario u SET u.bloqueado = true WHERE u.id = :id")
    void bloquearUsuario(@Param("id") Long id);
}