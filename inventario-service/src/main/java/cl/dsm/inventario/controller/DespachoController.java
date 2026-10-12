package cl.dsm.inventario.controller;

import cl.dsm.inventario.dto.*;
import cl.dsm.inventario.service.DespachoService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Controller de Despachos Internos.
 * 
 * Permisos:
 *   - GET: JEFATURA + OPERATIVO + AUDITOR + ENCARGADO_DEPENDENCIA
 *   - POST/PUT: JEFATURA + OPERATIVO
 */
@Slf4j
@RestController
@RequestMapping("/api/despachos")
@RequiredArgsConstructor
@CrossOrigin(origins = "${app.cors.allowed-origins}")
public class DespachoController {
    
    private final DespachoService despachoService;
    
    @GetMapping
    @PreAuthorize("hasAnyRole('JEFATURA', 'OPERATIVO', 'AUDITOR', 'ENCARGADO_DEPENDENCIA')")
    public ResponseEntity<ApiResponse<PageResponse<DespachoResponse>>> listar(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) Integer centroCostoId,
            @RequestParam(required = false) Integer bodegaId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fechaDesde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fechaHasta,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        return ResponseEntity.ok(ApiResponse.ok(
                despachoService.listar(q, estado, centroCostoId, bodegaId,
                        fechaDesde, fechaHasta, page, size)
        ));
    }
    
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('JEFATURA', 'OPERATIVO', 'AUDITOR', 'ENCARGADO_DEPENDENCIA')")
    public ResponseEntity<ApiResponse<DespachoResponse>> obtener(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(despachoService.obtenerPorId(id)));
    }
    
    /**
     * Sugerir lotes FEFO para un artículo en una bodega.
     * Útil para autocompletar en el frontend.
     */
    @GetMapping("/sugerir-lotes")
    @PreAuthorize("hasAnyRole('JEFATURA', 'OPERATIVO')")
    public ResponseEntity<ApiResponse<List<DespachoService.FefoSuggestionDTO>>> sugerirLotes(
            @RequestParam Long articuloId,
            @RequestParam Integer bodegaId,
            @RequestParam Integer cantidad) {
        
        return ResponseEntity.ok(ApiResponse.ok(
                despachoService.sugerirLotes(articuloId, bodegaId, cantidad)
        ));
    }
    
    @PostMapping
    @PreAuthorize("hasAnyRole('JEFATURA', 'OPERATIVO')")
    public ResponseEntity<ApiResponse<DespachoResponse>> crear(
            @Valid @RequestBody DespachoRequest req,
            Authentication auth) {
        
        String rutUsuario = auth != null ? auth.getName() : "SYSTEM";
        String nombreUsuario = auth != null && auth.getDetails() != null
                ? auth.getDetails().toString() : "Usuario";
        
        DespachoResponse creado = despachoService.crear(req, rutUsuario, nombreUsuario);
        
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Despacho creado. Confirme para actualizar stock.", creado));
    }
    
    @PostMapping("/{id}/confirmar")
    @PreAuthorize("hasAnyRole('JEFATURA', 'OPERATIVO')")
    public ResponseEntity<ApiResponse<DespachoResponse>> confirmar(
            @PathVariable Long id,
            Authentication auth,
            HttpServletRequest http) {
        
        String rutUsuario = auth != null ? auth.getName() : "SYSTEM";
        String nombreUsuario = auth != null && auth.getDetails() != null
                ? auth.getDetails().toString() : "Usuario";
        String ipAddress = getClientIp(http);
        
        DespachoResponse confirmado = despachoService.confirmar(
                id, rutUsuario, nombreUsuario, ipAddress);
        
        return ResponseEntity.ok(ApiResponse.ok(
                "Despacho confirmado. Stock actualizado.",
                confirmado
        ));
    }
    
    @PostMapping("/{id}/anular")
    @PreAuthorize("hasAnyRole('JEFATURA', 'OPERATIVO')")
    public ResponseEntity<ApiResponse<Void>> anular(
            @PathVariable Long id,
            @RequestParam(required = false, defaultValue = "Sin motivo") String motivo,
            Authentication auth) {
        
        String rutUsuario = auth != null ? auth.getName() : "SYSTEM";
        despachoService.anular(id, motivo, rutUsuario);
        
        return ResponseEntity.ok(ApiResponse.ok("Despacho anulado", null));
    }
    
    private String getClientIp(HttpServletRequest req) {
        String xf = req.getHeader("X-Forwarded-For");
        if (xf != null && !xf.isBlank()) {
            return xf.split(",")[0].trim();
        }
        return req.getRemoteAddr();
    }
}