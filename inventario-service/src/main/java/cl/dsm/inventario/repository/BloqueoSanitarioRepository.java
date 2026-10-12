package cl.dsm.inventario.repository;

import cl.dsm.inventario.entity.BloqueoSanitario;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BloqueoSanitarioRepository extends JpaRepository<BloqueoSanitario, Long> {
    
    Optional<BloqueoSanitario> findByNumeroBloqueo(String numeroBloqueo);
    
    boolean existsByNumeroBloqueo(String numeroBloqueo);
    
    /**
     * Verifica si un lote tiene un bloqueo activo.
     */
    @Query("""
        SELECT COUNT(b) > 0 FROM BloqueoSanitario b
        WHERE b.lote.id = :loteId 
          AND b.estado = 'ACTIVO' 
          AND b.activo = TRUE
        """)
    boolean tieneBloqueoActivo(@Param("loteId") Long loteId);
    
    /**
     * Lista bloqueos activos.
     */
    List<BloqueoSanitario> findByEstadoAndActivoTrueOrderByFechaBloqueoDesc(String estado);
    
    /**
     * Lista bloqueos por artículo.
     */
    List<BloqueoSanitario> findByArticuloIdAndActivoTrue(Long articuloId);
    
    /**
     * Búsqueda con filtros.
     */
    @Query("""
        SELECT b FROM BloqueoSanitario b
        WHERE (:q IS NULL OR :q = '' 
               OR LOWER(b.numeroBloqueo) LIKE LOWER(CONCAT('%', :q, '%'))
               OR LOWER(b.numeroLote) LIKE LOWER(CONCAT('%', :q, '%'))
               OR LOWER(b.sku) LIKE LOWER(CONCAT('%', :q, '%'))
               OR LOWER(b.nombreProducto) LIKE LOWER(CONCAT('%', :q, '%'))
               OR LOWER(b.numeroOficioIsp) LIKE LOWER(CONCAT('%', :q, '%')))
          AND (:estado IS NULL OR :estado = '' OR b.estado = :estado)
          AND (:tipoAlerta IS NULL OR :tipoAlerta = '' OR b.tipoAlerta = :tipoAlerta)
        ORDER BY b.fechaBloqueo DESC
        """)
    Page<BloqueoSanitario> buscar(
            @Param("q") String q,
            @Param("estado") String estado,
            @Param("tipoAlerta") String tipoAlerta,
            Pageable pageable);
    
    @Query("SELECT COALESCE(MAX(CAST(SUBSTRING(b.numeroBloqueo, 10) AS integer)), 0) " +
           "FROM BloqueoSanitario b WHERE b.numeroBloqueo LIKE CONCAT('BLQ-', :year, '-%')")
    Integer findMaxNumeroBloqueoByYear(@Param("year") String year);
}