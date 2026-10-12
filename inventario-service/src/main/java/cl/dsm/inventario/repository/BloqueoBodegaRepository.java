package cl.dsm.inventario.repository;

import cl.dsm.inventario.entity.BloqueoBodega;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BloqueoBodegaRepository extends JpaRepository<BloqueoBodega, Long> {
    
    List<BloqueoBodega> findByBloqueoId(Long bloqueoId);
    
    List<BloqueoBodega> findByBloqueoIdAndEstado(Long bloqueoId, String estado);
}