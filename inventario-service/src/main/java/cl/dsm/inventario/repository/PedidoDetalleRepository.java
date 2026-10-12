package cl.dsm.inventario.repository;

import cl.dsm.inventario.entity.PedidoDetalle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PedidoDetalleRepository extends JpaRepository<PedidoDetalle, Long> {
    
    List<PedidoDetalle> findByPedidoId(Long pedidoId);
}