package cl.dsm.inventario.repository;

import cl.dsm.inventario.entity.StockPorBodega;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StockPorBodegaRepository extends JpaRepository<StockPorBodega, Long> {
    
    Optional<StockPorBodega> findByArticuloIdAndBodegaId(Long articuloId, Integer bodegaId);
    
    List<StockPorBodega> findByArticuloIdAndActivoTrue(Long articuloId);
    
    List<StockPorBodega> findByBodegaIdAndActivoTrue(Integer bodegaId);
    
    boolean existsByArticuloIdAndBodegaId(Long articuloId, Integer bodegaId);
    
    // ============================================================
    // BÚSQUEDA POR CÓDIGO QR
    // ============================================================
    Optional<StockPorBodega> findByCodigoQr(String codigoQr);
    
    boolean existsByCodigoQr(String codigoQr);
    
    // ============================================================
    // STOCK TOTAL DE UN ARTÍCULO (sumando todas las bodegas)
    // ============================================================
    @Query("""
        SELECT COALESCE(SUM(s.stockActual), 0) 
        FROM StockPorBodega s
        WHERE s.articulo.id = :articuloId AND s.activo = TRUE
        """)
    Integer sumarStockPorArticulo(@Param("articuloId") Long articuloId);
    
    // ============================================================
    // ARTÍCULOS CON STOCK CRÍTICO
    // ============================================================
    @Query("""
        SELECT s FROM StockPorBodega s
        JOIN s.articulo a
        WHERE s.activo = TRUE 
          AND a.activo = TRUE
          AND s.stockActual <= a.stockMinimo
        """)
    List<StockPorBodega> findStockCritico();
}