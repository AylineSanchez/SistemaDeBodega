package cl.dsm.inventario.controller;

import cl.dsm.inventario.dto.*;
import cl.dsm.inventario.service.CentroCostoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/centros-costo")
@RequiredArgsConstructor
@CrossOrigin(origins = "${app.cors.allowed-origins}")
public class CentroCostoController {

    private final CentroCostoService centroService;

    // ============================================================
    // LISTAR SOLO ACTIVOS (para selects)
    // ============================================================
    @GetMapping
    @PreAuthorize("hasAnyRole('JEFATURA', 'OPERATIVO', 'ENCARGADO_DEPENDENCIA')")
    public ResponseEntity<ApiResponse<List<CentroCostoDTO>>> listar() {
        return ResponseEntity.ok(ApiResponse.ok(centroService.listarActivos()));
    }

    // ============================================================
    // LISTAR TODOS (activos + inactivos) para gestión
    // ============================================================
    @GetMapping("/todos")
    @PreAuthorize("hasAnyRole('JEFATURA', 'OPERATIVO')")
    public ResponseEntity<ApiResponse<List<CentroCostoDTO>>> listarTodos() {
        return ResponseEntity.ok(ApiResponse.ok(centroService.listarTodos()));
    }

    // ============================================================
    // OBTENER POR ID
    // ============================================================
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('JEFATURA', 'OPERATIVO', 'ENCARGADO_DEPENDENCIA')")
    public ResponseEntity<ApiResponse<CentroCostoDTO>> obtener(@PathVariable Integer id) {
        return ResponseEntity.ok(ApiResponse.ok(centroService.obtenerPorId(id)));
    }

    // ============================================================
    // CREAR - Solo JEFATURA y OPERATIVO
    // ============================================================
    @PostMapping
    @PreAuthorize("hasAnyRole('JEFATURA', 'OPERATIVO')")
    public ResponseEntity<ApiResponse<CentroCostoDTO>> crear(
            @Valid @RequestBody CentroCostoRequest req,
            Authentication auth) {

        String rutUsuario = auth != null ? auth.getName() : "SYSTEM";
        CentroCostoDTO creado = centroService.crear(req, rutUsuario);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Centro de costo creado exitosamente", creado));
    }

    // ============================================================
    // ACTUALIZAR - Solo JEFATURA y OPERATIVO
    // ============================================================
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('JEFATURA', 'OPERATIVO')")
    public ResponseEntity<ApiResponse<CentroCostoDTO>> actualizar(
            @PathVariable Integer id,
            @Valid @RequestBody CentroCostoRequest req,
            Authentication auth) {

        String rutUsuario = auth != null ? auth.getName() : "SYSTEM";
        CentroCostoDTO actualizado = centroService.actualizar(id, req, rutUsuario);

        return ResponseEntity.ok(ApiResponse.ok("Centro de costo actualizado", actualizado));
    }

    // ============================================================
    // DESACTIVAR - Solo JEFATURA y OPERATIVO
    // ============================================================
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('JEFATURA', 'OPERATIVO')")
    public ResponseEntity<ApiResponse<Void>> desactivar(
            @PathVariable Integer id,
            Authentication auth) {

        String rutUsuario = auth != null ? auth.getName() : "SYSTEM";
        centroService.desactivar(id, rutUsuario);

        return ResponseEntity.ok(ApiResponse.ok("Centro de costo desactivado", null));
    }

    // ============================================================
    // REACTIVAR - Solo JEFATURA y OPERATIVO
    // ============================================================
    @PostMapping("/{id}/reactivar")
    @PreAuthorize("hasAnyRole('JEFATURA', 'OPERATIVO')")
    public ResponseEntity<ApiResponse<Void>> reactivar(
            @PathVariable Integer id,
            Authentication auth) {

        String rutUsuario = auth != null ? auth.getName() : "SYSTEM";
        centroService.reactivar(id, rutUsuario);

        return ResponseEntity.ok(ApiResponse.ok("Centro de costo reactivado", null));
    }
}