package cl.dsm.inventario.repository;

import cl.dsm.inventario.entity.CentroCosto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CentroCostoRepository extends JpaRepository<CentroCosto, Integer> {
    
    Optional<CentroCosto> findByCodigo(String codigo);
    
    List<CentroCosto> findByActivoTrueOrderByNombreAsc();
    
    List<CentroCosto> findByTipo(String tipo);
}