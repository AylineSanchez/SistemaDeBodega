package cl.dsm.inventario.controller;

import cl.dsm.inventario.dto.ApiResponse;
import cl.dsm.inventario.dto.CategoriaDTO;
import cl.dsm.inventario.service.CategoriaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/categorias")
@RequiredArgsConstructor
@CrossOrigin(origins = "${app.cors.allowed-origins}")
public class CategoriaController {
    
    private final CategoriaService categoriaService;
    
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<CategoriaDTO>>> listar() {
        return ResponseEntity.ok(ApiResponse.ok(categoriaService.listarTodas()));
    }
    
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<CategoriaDTO>> obtener(@PathVariable Integer id) {
        return ResponseEntity.ok(ApiResponse.ok(categoriaService.obtenerPorId(id)));
    }
}