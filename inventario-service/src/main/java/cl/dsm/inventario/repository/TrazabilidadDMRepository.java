package cl.dsm.inventario.repository;

import cl.dsm.inventario.entity.TrazabilidadDM;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TrazabilidadDMRepository extends JpaRepository<TrazabilidadDM, Long> {
    
    Optional<TrazabilidadDM> findByLoteId(Long loteId);
    
    List<TrazabilidadDM> findByArticuloIdAndActivoTrue(Long articuloId);
    
    @Query("""
        SELECT t FROM TrazabilidadDM t
        WHERE (:q IS NULL OR :q = '' 
               OR LOWER(t.nombreProducto) LIKE LOWER(CONCAT('%', :q, '%'))
               OR LOWER(t.numeroLote) LIKE LOWER(CONCAT('%', :q, '%'))
               OR LOWER(t.numeroRegistroSanitario) LIKE LOWER(CONCAT('%', :q, '%')))
          AND (:estado IS NULL OR :estado = '' OR t.estado = :estado)
          AND (:estadoDm IS NULL OR :estadoDm = '' OR t.estadoDm = :estadoDm)
          AND (:articuloId IS NULL OR t.articulo.id = :articuloId)
        ORDER BY t.fechaCreacion DESC
        """)
    Page<TrazabilidadDM> buscar(
            @Param("q") String q,
            @Param("estado") String estado,
            @Param("estadoDm") String estadoDm,
            @Param("articuloId") Long articuloId,
            Pageable pageable);
}