package cl.dsm.inventario.controller;

import cl.dsm.inventario.dto.ApiResponse;
import cl.dsm.inventario.dto.BodegaDTO;
import cl.dsm.inventario.service.BodegaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/bodegas")
@RequiredArgsConstructor
@CrossOrigin(origins = "${app.cors.allowed-origins}")
public class BodegaController {
    
    private final BodegaService bodegaService;
    
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<BodegaDTO>>> listar() {
        return ResponseEntity.ok(ApiResponse.ok(bodegaService.listarTodas()));
    }
    
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<BodegaDTO>> obtener(@PathVariable Integer id) {
        return ResponseEntity.ok(ApiResponse.ok(bodegaService.obtenerPorId(id)));
    }
}