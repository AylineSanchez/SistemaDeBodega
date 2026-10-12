package cl.dsm.inventario.repository;

import cl.dsm.inventario.entity.Recepcion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface RecepcionRepository extends JpaRepository<Recepcion, Long> {
    
    Optional<Recepcion> findByNumeroRecepcion(String numeroRecepcion);
    
    boolean existsByNumeroRecepcion(String numeroRecepcion);
    
    boolean existsByTipoDocumentoAndNumeroDocumento(String tipo, String numero);
    
    // ============================================================
    // BÚSQUEDA CON FILTROS
    // ============================================================
    @Query("""
        SELECT r FROM Recepcion r
        WHERE (:q IS NULL OR :q = '' 
               OR LOWER(r.numeroRecepcion) LIKE LOWER(CONCAT('%', :q, '%'))
               OR LOWER(r.numeroDocumento) LIKE LOWER(CONCAT('%', :q, '%'))
               OR LOWER(r.rutProveedor) LIKE LOWER(CONCAT('%', :q, '%'))
               OR LOWER(r.nombreProveedor) LIKE LOWER(CONCAT('%', :q, '%')))
          AND (:estado IS NULL OR :estado = '' OR r.estado = :estado)
          AND (:bodegaId IS NULL OR r.bodega.id = :bodegaId)
          AND (:fechaDesde IS NULL OR r.fechaRecepcion >= :fechaDesde)
          AND (:fechaHasta IS NULL OR r.fechaRecepcion <= :fechaHasta)
        """)
    Page<Recepcion> buscar(
            @Param("q") String q,
            @Param("estado") String estado,
            @Param("bodegaId") Integer bodegaId,
            @Param("fechaDesde") LocalDate fechaDesde,
            @Param("fechaHasta") LocalDate fechaHasta,
            Pageable pageable);
    
    // ============================================================
    // GENERAR SIGUIENTE NÚMERO DE RECEPCIÓN
    // ============================================================
    @Query("SELECT COALESCE(MAX(CAST(SUBSTRING(r.numeroRecepcion, 10) AS integer)), 0) " +
           "FROM Recepcion r WHERE r.numeroRecepcion LIKE CONCAT('REC-', :year, '-%')")
    Integer findMaxNumeroRecepcionByYear(@Param("year") String year);
}