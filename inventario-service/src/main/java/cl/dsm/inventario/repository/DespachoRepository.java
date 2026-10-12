package cl.dsm.inventario.repository;

import cl.dsm.inventario.entity.Despacho;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface DespachoRepository extends JpaRepository<Despacho, Long> {
    
    Optional<Despacho> findByNumeroDespacho(String numeroDespacho);
    
    boolean existsByNumeroDespacho(String numeroDespacho);
    
    List<Despacho> findByCentroCostoIdAndActivoTrue(Integer centroCostoId);
    
    @Query("""
        SELECT d FROM Despacho d
        WHERE (:q IS NULL OR :q = '' 
               OR LOWER(d.numeroDespacho) LIKE LOWER(CONCAT('%', :q, '%'))
               OR LOWER(d.receptorNombre) LIKE LOWER(CONCAT('%', :q, '%')))
          AND (:estado IS NULL OR :estado = '' OR d.estado = :estado)
          AND (:centroCostoId IS NULL OR d.centroCosto.id = :centroCostoId)
          AND (:bodegaId IS NULL OR d.bodega.id = :bodegaId)
          AND (:fechaDesde IS NULL OR d.fechaDespacho >= :fechaDesde)
          AND (:fechaHasta IS NULL OR d.fechaDespacho <= :fechaHasta)
        ORDER BY d.fechaDespacho DESC, d.id DESC
        """)
    Page<Despacho> buscar(
            @Param("q") String q,
            @Param("estado") String estado,
            @Param("centroCostoId") Integer centroCostoId,
            @Param("bodegaId") Integer bodegaId,
            @Param("fechaDesde") LocalDateTime fechaDesde,
            @Param("fechaHasta") LocalDateTime fechaHasta,
            Pageable pageable);
    
    @Query("SELECT COALESCE(MAX(CAST(SUBSTRING(d.numeroDespacho, 10) AS integer)), 0) " +
           "FROM Despacho d WHERE d.numeroDespacho LIKE CONCAT('DES-', :year, '-%')")
    Integer findMaxNumeroDespachoByYear(@Param("year") String year);
}