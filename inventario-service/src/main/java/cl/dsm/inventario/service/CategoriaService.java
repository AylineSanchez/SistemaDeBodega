package cl.dsm.inventario.service;

import cl.dsm.inventario.dto.CategoriaDTO;
import cl.dsm.inventario.entity.Categoria;
import cl.dsm.inventario.exception.ResourceNotFoundException;
import cl.dsm.inventario.repository.ArticuloRepository;
import cl.dsm.inventario.repository.CategoriaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CategoriaService {
    
    private final CategoriaRepository categoriaRepo;
    private final ArticuloRepository articuloRepo;
    
    @Transactional(readOnly = true)
    public List<CategoriaDTO> listarTodas() {
        return categoriaRepo.findByActivaTrueOrderByOrdenAsc().stream()
                .map(c -> {
                    CategoriaDTO dto = CategoriaDTO.from(c);
                    dto.setTotalArticulos(articuloRepo.countByCategoriaIdAndActivoTrue(c.getId()));
                    return dto;
                })
                .toList();
    }
    
    @Transactional(readOnly = true)
    public CategoriaDTO obtenerPorId(Integer id) {
        Categoria c = categoriaRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Categoría no encontrada: " + id));
        return CategoriaDTO.from(c);
    }
}