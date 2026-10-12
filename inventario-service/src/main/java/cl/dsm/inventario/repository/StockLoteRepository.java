package cl.dsm.inventario.repository;

import cl.dsm.inventario.entity.StockLote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StockLoteRepository extends JpaRepository<StockLote, Long> {
    
    // ============================================================
    // BÚSQUEDAS BÁSICAS
    // ============================================================
    
    /**
     * Busca un StockLote por stock_id y lote_id.
     * Usado en RecepcionService para acumular cantidad al mismo lote.
     */
    Optional<StockLote> findByStockIdAndLoteId(Long stockId, Long loteId);
    
    /**
     * Lista todos los StockLote de un stock específico.
     */
    List<StockLote> findByStockId(Long stockId);
    
    /**
     * Lista todos los StockLote de un lote específico.
     */
    List<StockLote> findByLoteId(Long loteId);
    
    // ============================================================
    // STOCK DISPONIBLE POR ARTÍCULO-BODEGA (ordenado FEFO)
    // ============================================================
    
    /**
     * FEFO: First Expire, First Out.
     * Devuelve los lotes disponibles de un artículo en una bodega,
     * ordenados por fecha de vencimiento más próxima.
     * 
     * Excluye:
     *   - Lotes bloqueados por ISP
     *   - Lotes inactivos
     *   - StockLotes con cantidad 0
     */
    @Query("""
        SELECT sl FROM StockLote sl
        JOIN FETCH sl.lote l
        JOIN FETCH sl.stock s
        WHERE s.articulo.id = :articuloId
          AND s.bodega.id = :bodegaId
          AND s.activo = TRUE
          AND sl.cantidad > 0
          AND l.bloqueado = FALSE
          AND l.activo = TRUE
        ORDER BY l.fechaVencimiento ASC NULLS LAST, l.id ASC
        """)
    List<StockLote> findFefoPorArticuloBodega(
            @Param("articuloId") Long articuloId,
            @Param("bodegaId") Integer bodegaId);
    
    // ============================================================
    // STOCK TOTAL POR LOTE (sumando todas las bodegas)
    // ============================================================
    
    /**
     * Suma la cantidad total de un lote en todas las bodegas.
     * Útil para saber cuánto queda de un lote tras varias salidas.
     */
    @Query("SELECT COALESCE(SUM(sl.cantidad), 0) FROM StockLote sl WHERE sl.lote.id = :loteId")
    Integer sumarCantidadPorLote(@Param("loteId") Long loteId);
    
    // ============================================================
    // STOCK TOTAL POR ARTÍCULO (sumando todas las bodegas y lotes)
    // ============================================================
    
    @Query("""
        SELECT COALESCE(SUM(sl.cantidad), 0) 
        FROM StockLote sl
        WHERE sl.stock.articulo.id = :articuloId 
          AND sl.lote.bloqueado = FALSE
        """)
    Integer sumarStockDisponiblePorArticulo(@Param("articuloId") Long articuloId);
    
    // ============================================================
    // LOTES DISPONIBLES DE UN ARTÍCULO (cualquier bodega)
    // ============================================================
    
    @Query("""
        SELECT sl FROM StockLote sl
        JOIN FETCH sl.lote l
        JOIN FETCH sl.stock s
        WHERE s.articulo.id = :articuloId
          AND s.activo = TRUE
          AND sl.cantidad > 0
          AND l.bloqueado = FALSE
          AND l.activo = TRUE
        ORDER BY l.fechaVencimiento ASC NULLS LAST
        """)
    List<StockLote> findDisponiblesPorArticulo(@Param("articuloId") Long articuloId);
}