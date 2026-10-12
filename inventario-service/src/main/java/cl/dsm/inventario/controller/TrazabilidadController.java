package cl.dsm.inventario.controller;

import cl.dsm.inventario.dto.*;
import cl.dsm.inventario.service.TrazabilidadService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controller del Módulo de Trazabilidad Sanitaria NT 226/247.
 * 
 * ⚠️ Solo JEFATURA y AUDITOR (RF-069).
 */
@Slf4j
@RestController
@RequestMapping("/api/trazabilidad")
@RequiredArgsConstructor
@CrossOrigin(origins = "${app.cors.allowed-origins}")
@PreAuthorize("hasAnyRole('JEFATURA', 'AUDITOR')")
public class TrazabilidadController {
    
    private final TrazabilidadService trazabilidadService;
    
    // ============================================================
    // BLOQUEOS SANITARIOS
    // ============================================================
    
    @GetMapping("/bloqueos")
    public ResponseEntity<ApiResponse<PageResponse<BloqueoResponse>>> listarBloqueos(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) String tipoAlerta,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        return ResponseEntity.ok(ApiResponse.ok(
                trazabilidadService.listarBloqueos(q, estado, tipoAlerta, page, size)
        ));
    }
    
    @GetMapping("/bloqueos/{id}")
    public ResponseEntity<ApiResponse<BloqueoResponse>> obtenerBloqueo(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(trazabilidadService.obtenerBloqueoPorId(id)));
    }
    
    @PostMapping("/bloqueos")
    public ResponseEntity<ApiResponse<BloqueoResponse>> crearBloqueo(
            @Valid @RequestBody BloqueoRequest req,
            Authentication auth) {
        
        String rutUsuario = auth != null ? auth.getName() : "SYSTEM";
        String nombreUsuario = auth != null && auth.getDetails() != null
                ? auth.getDetails().toString() : "Usuario";
        
        BloqueoResponse creado = trazabilidadService.crearBloqueo(req, rutUsuario, nombreUsuario);
        
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(
                        "Bloqueo sanitario creado. El lote no podrá ser despachado.",
                        creado
                ));
    }
    
    @PostMapping("/bloqueos/{id}/levantar")
    public ResponseEntity<ApiResponse<BloqueoResponse>> levantarBloqueo(
            @PathVariable Long id,
            @RequestParam String motivo,
            Authentication auth) {
        
        String rutUsuario = auth != null ? auth.getName() : "SYSTEM";
        String nombreUsuario = auth != null && auth.getDetails() != null
                ? auth.getDetails().toString() : "Usuario";
        
        BloqueoResponse levantado = trazabilidadService.levantarBloqueo(
                id, motivo, rutUsuario, nombreUsuario);
        
        return ResponseEntity.ok(ApiResponse.ok(
                "Bloqueo levantado exitosamente. El lote puede volver a despacharse.",
                levantado
        ));
    }
    
    // ============================================================
    // TRAZABILIDAD DM
    // ============================================================
    
    @GetMapping("/dm")
    public ResponseEntity<ApiResponse<PageResponse<TrazabilidadResponse>>> listarTrazabilidad(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) String estadoDm,
            @RequestParam(required = false) Long articuloId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        return ResponseEntity.ok(ApiResponse.ok(
                trazabilidadService.listarTrazabilidad(
                        q, estado, estadoDm, articuloId, page, size)
        ));
    }
    
    @GetMapping("/dm/{id}")
    public ResponseEntity<ApiResponse<TrazabilidadResponse>> obtenerTrazabilidad(
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(
                trazabilidadService.obtenerTrazabilidadPorId(id)));
    }
    
    @PostMapping("/dm")
    public ResponseEntity<ApiResponse<TrazabilidadResponse>> crearTrazabilidad(
            @Valid @RequestBody TrazabilidadRequest req,
            Authentication auth) {
        
        String rutUsuario = auth != null ? auth.getName() : "SYSTEM";
        TrazabilidadResponse creado = trazabilidadService.crearTrazabilidad(req, rutUsuario);
        
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Trazabilidad registrada", creado));
    }
    
    @GetMapping("/dm/exportar")
    public ResponseEntity<ApiResponse<List<TrazabilidadResponse>>> exportarTrazabilidad(
            @RequestParam(required = false) String filtro) {
        
        return ResponseEntity.ok(ApiResponse.ok(
                trazabilidadService.exportarTrazabilidad(filtro)));
    }
}