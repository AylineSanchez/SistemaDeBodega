package cl.dsm.inventario.repository;

import cl.dsm.inventario.entity.Bodega;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BodegaRepository extends JpaRepository<Bodega, Integer> {
    
    Optional<Bodega> findByCodigo(String codigo);
    
    boolean existsByCodigo(String codigo);
    
    List<Bodega> findByActivaTrueOrderByCodigoAsc();
    
    List<Bodega> findByDependencia(String dependencia);
}