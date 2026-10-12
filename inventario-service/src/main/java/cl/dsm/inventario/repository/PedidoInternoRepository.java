package cl.dsm.inventario.repository;

import cl.dsm.inventario.entity.PedidoInterno;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PedidoInternoRepository extends JpaRepository<PedidoInterno, Long> {
    
    Optional<PedidoInterno> findByNumeroPedido(String numeroPedido);
    
    boolean existsByNumeroPedido(String numeroPedido);
    
    List<PedidoInterno> findByEstadoAndActivoTrueOrderByPrioridadAscFechaSolicitudAsc(String estado);
    
    List<PedidoInterno> findByCentroCostoIdAndActivoTrue(Integer centroCostoId);
    
    @Query("""
        SELECT p FROM PedidoInterno p
        WHERE (:q IS NULL OR :q = '' 
               OR LOWER(p.numeroPedido) LIKE LOWER(CONCAT('%', :q, '%'))
               OR LOWER(p.solicitanteNombre) LIKE LOWER(CONCAT('%', :q, '%')))
          AND (:estado IS NULL OR :estado = '' OR p.estado = :estado)
          AND (:centroCostoId IS NULL OR p.centroCosto.id = :centroCostoId)
          AND (:prioridad IS NULL OR :prioridad = '' OR p.prioridad = :prioridad)
        ORDER BY 
            CASE p.prioridad WHEN 'URGENTE' THEN 1 ELSE 2 END,
            p.fechaSolicitud ASC
        """)
    Page<PedidoInterno> buscar(
            @Param("q") String q,
            @Param("estado") String estado,
            @Param("centroCostoId") Integer centroCostoId,
            @Param("prioridad") String prioridad,
            Pageable pageable);
    
    @Query("SELECT COALESCE(MAX(CAST(SUBSTRING(p.numeroPedido, 15) AS integer)), 0) " +
           "FROM PedidoInterno p WHERE p.numeroPedido LIKE CONCAT('PED-', :codigo, '-', :year, '-%')")
    Integer findMaxNumeroByCentroAndYear(
            @Param("codigo") String codigo,
            @Param("year") String year);
}