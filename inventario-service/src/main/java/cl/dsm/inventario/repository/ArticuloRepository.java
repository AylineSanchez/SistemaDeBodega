package cl.dsm.inventario.repository;

import cl.dsm.inventario.entity.Articulo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ArticuloRepository extends JpaRepository<Articulo, Long> {

    Optional<Articulo> findBySku(String sku);

    boolean existsBySku(String sku);

    // ============================================================
    // BÚSQUEDA GENERAL CON FILTROS (RF-016)
    //
    // ⚡ NO usar DISTINCT porque:
    //    1. La relación articulo → categoria es ManyToOne (no duplica).
    //    2. PostgreSQL no permite ORDER BY con subconsultas + DISTINCT
    //       a menos que esas expresiones estén en el SELECT.
    // ============================================================
    @Query("""
        SELECT a FROM Articulo a
        JOIN FETCH a.categoria c
        WHERE (:incluirInactivos = TRUE OR a.activo = TRUE)
          AND (:categoriaId IS NULL OR c.id = :categoriaId)
          AND (:bodegaId IS NULL OR EXISTS (
                SELECT 1 FROM StockPorBodega spb
                WHERE spb.articulo.id = a.id
                  AND spb.bodega.id = :bodegaId
                  AND spb.activo = TRUE
          ))
          AND (:q IS NULL OR :q = ''
               OR LOWER(a.sku) LIKE LOWER(CONCAT('%', :q, '%'))
               OR LOWER(a.nombreTecnico) LIKE LOWER(CONCAT('%', :q, '%')))
          AND (
              :estadoStock IS NULL OR :estadoStock = ''
              OR (:estadoStock = 'QUIEBRE' AND
                  COALESCE((SELECT SUM(spb2.stockActual) FROM StockPorBodega spb2
                            WHERE spb2.articulo.id = a.id AND spb2.activo = TRUE), 0) = 0)
              OR (:estadoStock = 'CRITICO' AND
                  COALESCE((SELECT SUM(spb3.stockActual) FROM StockPorBodega spb3
                            WHERE spb3.articulo.id = a.id AND spb3.activo = TRUE), 0) > 0
                  AND COALESCE((SELECT SUM(spb4.stockActual) FROM StockPorBodega spb4
                            WHERE spb4.articulo.id = a.id AND spb4.activo = TRUE), 0) <= a.stockMinimo)
              OR (:estadoStock = 'BAJO' AND
                  COALESCE((SELECT SUM(spb5.stockActual) FROM StockPorBodega spb5
                            WHERE spb5.articulo.id = a.id AND spb5.activo = TRUE), 0) > a.stockMinimo
                  AND COALESCE((SELECT SUM(spb6.stockActual) FROM StockPorBodega spb6
                            WHERE spb6.articulo.id = a.id AND spb6.activo = TRUE), 0) <= a.stockMinimo * 1.5)
              OR (:estadoStock = 'NORMAL' AND
                  COALESCE((SELECT SUM(spb7.stockActual) FROM StockPorBodega spb7
                            WHERE spb7.articulo.id = a.id AND spb7.activo = TRUE), 0) > a.stockMinimo * 1.5)
          )
        ORDER BY
          CASE WHEN :ordenStock = 'ASC' THEN
              COALESCE((SELECT SUM(spb8.stockActual) FROM StockPorBodega spb8
                        WHERE spb8.articulo.id = a.id AND spb8.activo = TRUE), 0)
          END ASC,
          CASE WHEN :ordenStock = 'DESC' THEN
              COALESCE((SELECT SUM(spb9.stockActual) FROM StockPorBodega spb9
                        WHERE spb9.articulo.id = a.id AND spb9.activo = TRUE), 0)
          END DESC,
          a.nombreTecnico ASC
        """)
    Page<Articulo> buscar(
            @Param("q") String q,
            @Param("categoriaId") Integer categoriaId,
            @Param("bodegaId") Integer bodegaId,
            @Param("incluirInactivos") boolean incluirInactivos,
            @Param("estadoStock") String estadoStock,
            @Param("ordenStock") String ordenStock,
            Pageable pageable);

    // ============================================================
    // ARTÍCULOS POR BODEGA
    // ============================================================
    @Query("""
        SELECT a FROM Articulo a
        JOIN FETCH a.categoria c
        JOIN a.stocks s
        WHERE s.bodega.id = :bodegaId
          AND s.activo = TRUE
          AND a.activo = TRUE
        """)
    List<Articulo> findByBodega(@Param("bodegaId") Integer bodegaId);

    // ============================================================
    // ARTÍCULOS DE UNA CATEGORÍA
    // ============================================================
    List<Articulo> findByCategoriaIdAndActivoTrue(Integer categoriaId);

    // ============================================================
    // CONTAR POR CATEGORÍA
    // ============================================================
    long countByCategoriaIdAndActivoTrue(Integer categoriaId);
}