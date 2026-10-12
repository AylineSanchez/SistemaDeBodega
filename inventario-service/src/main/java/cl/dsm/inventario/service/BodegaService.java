package cl.dsm.inventario.service;

import cl.dsm.inventario.dto.BodegaDTO;
import cl.dsm.inventario.entity.Bodega;
import cl.dsm.inventario.exception.ResourceNotFoundException;
import cl.dsm.inventario.repository.BodegaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class BodegaService {
    
    private final BodegaRepository bodegaRepo;
    
    @Transactional(readOnly = true)
    public List<BodegaDTO> listarTodas() {
        return bodegaRepo.findByActivaTrueOrderByCodigoAsc().stream()
                .map(BodegaDTO::from)
                .toList();
    }
    
    @Transactional(readOnly = true)
    public BodegaDTO obtenerPorId(Integer id) {
        Bodega b = bodegaRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Bodega no encontrada: " + id));
        return BodegaDTO.from(b);
    }
}