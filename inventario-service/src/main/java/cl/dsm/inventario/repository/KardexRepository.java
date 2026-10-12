package cl.dsm.inventario.repository;

import cl.dsm.inventario.entity.Kardex;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface KardexRepository extends JpaRepository<Kardex, Long> {
    
    // ============================================================
    // BÚSQUEDA CON FILTROS
    // ============================================================
    @Query("""
        SELECT k FROM Kardex k
        WHERE (:articuloId IS NULL OR k.articulo.id = :articuloId)
          AND (:bodegaId IS NULL OR k.bodega.id = :bodegaId)
          AND (:tipoMovimiento IS NULL OR :tipoMovimiento = '' OR k.tipoMovimiento = :tipoMovimiento)
          AND (:fechaDesde IS NULL OR k.fechaMovimiento >= :fechaDesde)
          AND (:fechaHasta IS NULL OR k.fechaMovimiento <= :fechaHasta)
        ORDER BY k.fechaMovimiento DESC, k.id DESC
        """)
    Page<Kardex> buscar(
            @Param("articuloId") Long articuloId,
            @Param("bodegaId") Integer bodegaId,
            @Param("tipoMovimiento") String tipoMovimiento,
            @Param("fechaDesde") LocalDateTime fechaDesde,
            @Param("fechaHasta") LocalDateTime fechaHasta,
            Pageable pageable);
    
    // ============================================================
    // ÚLTIMO MOVIMIENTO DE UN ARTÍCULO EN UNA BODEGA
    // ============================================================
    @Query("""
        SELECT k FROM Kardex k
        WHERE k.articulo.id = :articuloId 
          AND k.bodega.id = :bodegaId
        ORDER BY k.fechaMovimiento DESC, k.id DESC
        LIMIT 1
        """)
    Kardex findUltimoMovimiento(
            @Param("articuloId") Long articuloId,
            @Param("bodegaId") Integer bodegaId);
    
    // ============================================================
    // RECONSTITUCIÓN: stock a una fecha pasada (RF-056)
    // ============================================================
    @Query("""
        SELECT k.stockNuevo FROM Kardex k
        WHERE k.articulo.id = :articuloId 
          AND k.bodega.id = :bodegaId
          AND k.fechaMovimiento <= :fecha
        ORDER BY k.fechaMovimiento DESC, k.id DESC
        LIMIT 1
        """)
    Integer findStockAFecha(
            @Param("articuloId") Long articuloId,
            @Param("bodegaId") Integer bodegaId,
            @Param("fecha") LocalDateTime fecha);
    
    // ============================================================
    // MOVIMIENTOS POR DOCUMENTO
    // ============================================================
    List<Kardex> findByDocumentoTipoAndDocumentoNumero(String documentoTipo, String documentoNumero);
}