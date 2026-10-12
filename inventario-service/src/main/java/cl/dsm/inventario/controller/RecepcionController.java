package cl.dsm.inventario.controller;

import cl.dsm.inventario.dto.*;
import cl.dsm.inventario.service.KardexService;
import cl.dsm.inventario.service.RecepcionService;
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

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Controller del Módulo de Recepción.
 * 
 * ⚠️ Solo JEFATURA y OPERATIVO (RF-020 a RF-028).
 */
@Slf4j
@RestController
@RequestMapping("/api/recepciones")
@RequiredArgsConstructor
@CrossOrigin(origins = "${app.cors.allowed-origins}")
public class RecepcionController {
    
    private final RecepcionService recepcionService;
    private final KardexService kardexService;
    
    // ============================================================
    // LISTAR RECEPCIONES
    // ============================================================
    @GetMapping
    @PreAuthorize("hasAnyRole('JEFATURA', 'OPERATIVO', 'AUDITOR')")
    public ResponseEntity<ApiResponse<PageResponse<RecepcionResumenDTO>>> listar(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) Integer bodegaId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaDesde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaHasta,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        return ResponseEntity.ok(ApiResponse.ok(
                recepcionService.listar(q, estado, bodegaId, fechaDesde, fechaHasta, page, size)
        ));
    }
    
    // ============================================================
    // OBTENER POR ID
    // ============================================================
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('JEFATURA', 'OPERATIVO', 'AUDITOR')")
    public ResponseEntity<ApiResponse<RecepcionResponse>> obtener(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(recepcionService.obtenerPorId(id)));
    }
    
    // ============================================================
    // CREAR RECEPCIÓN (BORRADOR)
    // ============================================================
    @PostMapping
    @PreAuthorize("hasAnyRole('JEFATURA', 'OPERATIVO')")
    public ResponseEntity<ApiResponse<RecepcionResponse>> crear(
            @Valid @RequestBody RecepcionRequest req,
            Authentication auth) {
        
        String rutUsuario = auth != null ? auth.getName() : "SYSTEM";
        String nombreUsuario = auth != null && auth.getDetails() != null
                ? auth.getDetails().toString() : "Usuario";
        
        RecepcionResponse creada = recepcionService.crear(req, rutUsuario, nombreUsuario);
        
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Recepción creada exitosamente. Confirme para actualizar stock.", creada));
    }
    
    // ============================================================
    // CONFIRMAR RECEPCIÓN (actualiza stock + kardex)
    // ============================================================
    @PostMapping("/{id}/confirmar")
    @PreAuthorize("hasAnyRole('JEFATURA', 'OPERATIVO')")
    public ResponseEntity<ApiResponse<RecepcionResponse>> confirmar(
            @PathVariable Long id,
            Authentication auth,
            HttpServletRequest http) {
        
        String rutUsuario = auth != null ? auth.getName() : "SYSTEM";
        String nombreUsuario = auth != null && auth.getDetails() != null
                ? auth.getDetails().toString() : "Usuario";
        String ipAddress = getClientIp(http);
        
        RecepcionResponse confirmada = recepcionService.confirmar(
                id, rutUsuario, nombreUsuario, ipAddress);
        
        return ResponseEntity.ok(ApiResponse.ok(
                "Recepción confirmada. Stock actualizado correctamente.",
                confirmada
        ));
    }
    
    // ============================================================
    // ANULAR RECEPCIÓN (solo BORRADOR)
    // ============================================================
    @PostMapping("/{id}/anular")
    @PreAuthorize("hasAnyRole('JEFATURA', 'OPERATIVO')")
    public ResponseEntity<ApiResponse<Void>> anular(
            @PathVariable Long id,
            @RequestParam(required = false, defaultValue = "Sin motivo") String motivo,
            Authentication auth) {
        
        String rutUsuario = auth != null ? auth.getName() : "SYSTEM";
        recepcionService.anular(id, motivo, rutUsuario);
        
        return ResponseEntity.ok(ApiResponse.ok("Recepción anulada", null));
    }
    
    // ============================================================
    // KARDEX DE UN ARTÍCULO (historial de movimientos)
    // ============================================================
    @GetMapping("/kardex")
    @PreAuthorize("hasAnyRole('JEFATURA', 'OPERATIVO', 'AUDITOR')")
    public ResponseEntity<ApiResponse<PageResponse<KardexDTO>>> listarKardex(
            @RequestParam(required = false) Long articuloId,
            @RequestParam(required = false) Integer bodegaId,
            @RequestParam(required = false) String tipoMovimiento,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fechaDesde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fechaHasta,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        return ResponseEntity.ok(ApiResponse.ok(
                kardexService.listar(articuloId, bodegaId, tipoMovimiento,
                        fechaDesde, fechaHasta, page, size)
        ));
    }
    
    // ============================================================
    // RECONSTITUCIÓN: stock a una fecha pasada
    // ============================================================
    @GetMapping("/kardex/stock-a-fecha")
    @PreAuthorize("hasAnyRole('JEFATURA', 'AUDITOR')")
    public ResponseEntity<ApiResponse<Integer>> stockAFecha(
            @RequestParam Long articuloId,
            @RequestParam Integer bodegaId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fecha) {
        
        Integer stock = kardexService.obtenerStockAFecha(articuloId, bodegaId, fecha);
        
        return ResponseEntity.ok(ApiResponse.ok(stock));
    }
    
    // ============================================================
    // HELPERS
    // ============================================================
    private String getClientIp(HttpServletRequest req) {
        String xf = req.getHeader("X-Forwarded-For");
        if (xf != null && !xf.isBlank()) {
            return xf.split(",")[0].trim();
        }
        return req.getRemoteAddr();
    }
}