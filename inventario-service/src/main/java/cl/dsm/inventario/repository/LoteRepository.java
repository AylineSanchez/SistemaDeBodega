package cl.dsm.inventario.repository;

import cl.dsm.inventario.entity.Lote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface LoteRepository extends JpaRepository<Lote, Long> {

    Optional<Lote> findByArticuloIdAndNumeroLote(Long articuloId, String numeroLote);

    List<Lote> findByArticuloIdAndActivoTrue(Long articuloId);

    List<Lote> findByBloqueadoTrueAndActivoTrue();

    // ============================================================
    // LOTES POR VENCER (para reportes de alertas)
    // ============================================================
    @Query("""
        SELECT l FROM Lote l
        WHERE l.activo = TRUE 
          AND l.bloqueado = FALSE
          AND l.fechaVencimiento IS NOT NULL
          AND l.fechaVencimiento <= :fechaLimite
        ORDER BY l.fechaVencimiento ASC
        """)
    List<Lote> findLotesPorVencer(@Param("fechaLimite") LocalDate fechaLimite);

    // ============================================================
    // LOTES VENCIDOS
    // ============================================================
    @Query("""
        SELECT l FROM Lote l
        WHERE l.activo = TRUE 
          AND l.fechaVencimiento IS NOT NULL
          AND l.fechaVencimiento < CURRENT_DATE
        ORDER BY l.fechaVencimiento ASC
        """)
    List<Lote> findLotesVencidos();

    // ============================================================
    // ⚡ NUEVO: Stock total de un lote (suma de todas las bodegas)
    // ============================================================
    @Query("""
        SELECT COALESCE(SUM(sl.cantidad), 0) 
        FROM StockLote sl 
        WHERE sl.lote.id = :loteId
        """)
    Integer getStockTotalLote(@Param("loteId") Long loteId);
}