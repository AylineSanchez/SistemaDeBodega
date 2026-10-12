package cl.dsm.inventario.controller;

import cl.dsm.inventario.dto.ApiResponse;
import cl.dsm.inventario.dto.CentroCostoDTO;
import cl.dsm.inventario.entity.CentroCosto;
import cl.dsm.inventario.repository.CentroCostoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/centros-costo")
@RequiredArgsConstructor
@CrossOrigin(origins = "${app.cors.allowed-origins}")
public class CentroCostoController {
    
    private final CentroCostoRepository centroRepo;
    
    @GetMapping
    @PreAuthorize("hasAnyRole('JEFATURA', 'OPERATIVO', 'ENCARGADO_DEPENDENCIA')")
    public ResponseEntity<ApiResponse<List<CentroCostoDTO>>> listar() {
        List<CentroCostoDTO> lista = centroRepo.findByActivoTrueOrderByNombreAsc()
                .stream()
                .map(CentroCostoDTO::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(lista));
    }
}