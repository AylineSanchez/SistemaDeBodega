package cl.dsm.inventario.repository;

import cl.dsm.inventario.entity.RecepcionDetalle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RecepcionDetalleRepository extends JpaRepository<RecepcionDetalle, Long> {
    
    List<RecepcionDetalle> findByRecepcionId(Long recepcionId);
    
    List<RecepcionDetalle> findByArticuloIdAndRecepcionId(Long articuloId, Long recepcionId);
    
    @Query("SELECT COALESCE(SUM(rd.cantidadRecibida), 0) FROM RecepcionDetalle rd WHERE rd.recepcion.id = :recepcionId")
    Integer sumarCantidadPorRecepcion(@Param("recepcionId") Long recepcionId);
}