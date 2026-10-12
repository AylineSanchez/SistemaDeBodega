package cl.dsm.inventario.repository;

import cl.dsm.inventario.entity.Categoria;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CategoriaRepository extends JpaRepository<Categoria, Integer> {
    
    Optional<Categoria> findByCodigo(String codigo);
    
    boolean existsByCodigo(String codigo);
    
    List<Categoria> findByActivaTrueOrderByOrdenAsc();
}