package cl.dsm.auth.repository;

import cl.dsm.auth.entity.Sesion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface SesionRepository extends JpaRepository<Sesion, UUID> {
    
    List<Sesion> findByUsuarioIdAndActivaTrue(Long usuarioId);
    
    @Modifying
    @Query("UPDATE Sesion s SET s.activa = false, s.fechaCierre = :fecha WHERE s.id = :id")
    void cerrarSesion(@Param("id") UUID id, @Param("fecha") LocalDateTime fecha);
    
    @Modifying
    @Query("UPDATE Sesion s SET s.activa = false, s.fechaCierre = :fecha " +
           "WHERE s.usuario.id = :usuarioId AND s.activa = true")
    void cerrarTodasSesionesUsuario(@Param("usuarioId") Long usuarioId, 
                                    @Param("fecha") LocalDateTime fecha);
}