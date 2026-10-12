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
    // ============================================================
    @Query("""
        SELECT DISTINCT a FROM Articulo a
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
        """)
    Page<Articulo> buscar(
            @Param("q") String q,
            @Param("categoriaId") Integer categoriaId,
            @Param("bodegaId") Integer bodegaId,
            @Param("incluirInactivos") boolean incluirInactivos,
            Pageable pageable);

    // ============================================================
    // ARTÍCULOS POR BODEGA
    // ============================================================
    @Query("""
        SELECT DISTINCT a FROM Articulo a
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