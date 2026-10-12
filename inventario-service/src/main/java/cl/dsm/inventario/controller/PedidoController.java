package cl.dsm.inventario.controller;

import cl.dsm.inventario.dto.*;
import cl.dsm.inventario.service.PedidoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * Controller de Pedidos Internos.
 * 
 * Permisos:
 *   - GET: JEFATURA + OPERATIVO + ENCARGADO_DEPENDENCIA
 *   - POST: JEFATURA + OPERATIVO + ENCARGADO_DEPENDENCIA (crear pedido)
 *   - DELETE/CANCELAR: JEFATURA + OPERATIVO
 */
@Slf4j
@RestController
@RequestMapping("/api/pedidos")
@RequiredArgsConstructor
@CrossOrigin(origins = "${app.cors.allowed-origins}")
public class PedidoController {
    
    private final PedidoService pedidoService;
    
    @GetMapping
    @PreAuthorize("hasAnyRole('JEFATURA', 'OPERATIVO', 'ENCARGADO_DEPENDENCIA')")
    public ResponseEntity<ApiResponse<PageResponse<PedidoResponse>>> listar(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) Integer centroCostoId,
            @RequestParam(required = false) String prioridad,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        return ResponseEntity.ok(ApiResponse.ok(
                pedidoService.listar(q, estado, centroCostoId, prioridad, page, size)
        ));
    }
    
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('JEFATURA', 'OPERATIVO', 'ENCARGADO_DEPENDENCIA')")
    public ResponseEntity<ApiResponse<PedidoResponse>> obtener(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(pedidoService.obtenerPorId(id)));
    }
    
    @PostMapping
    @PreAuthorize("hasAnyRole('JEFATURA', 'OPERATIVO', 'ENCARGADO_DEPENDENCIA')")
    public ResponseEntity<ApiResponse<PedidoResponse>> crear(
            @Valid @RequestBody PedidoRequest req,
            Authentication auth) {
        
        String rutUsuario = auth != null ? auth.getName() : "SYSTEM";
        String nombreUsuario = auth != null && auth.getDetails() != null
                ? auth.getDetails().toString() : "Usuario";
        
        PedidoResponse creado = pedidoService.crear(req, rutUsuario, nombreUsuario);
        
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Pedido creado exitosamente", creado));
    }
    
    @PostMapping("/{id}/cancelar")
    @PreAuthorize("hasAnyRole('JEFATURA', 'OPERATIVO')")
    public ResponseEntity<ApiResponse<Void>> cancelar(
            @PathVariable Long id,
            @RequestParam(required = false, defaultValue = "Sin motivo") String motivo,
            Authentication auth) {
        
        String rutUsuario = auth != null ? auth.getName() : "SYSTEM";
        pedidoService.cancelar(id, motivo, rutUsuario);
        
        return ResponseEntity.ok(ApiResponse.ok("Pedido cancelado", null));
    }
}