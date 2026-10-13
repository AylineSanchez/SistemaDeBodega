package cl.dsm.inventario.controller;

import cl.dsm.inventario.dto.*;
import cl.dsm.inventario.service.ArticuloService;
import cl.dsm.inventario.service.StockBodegaService;
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
@RequestMapping("/api/articulos")
@RequiredArgsConstructor
@CrossOrigin(origins = "${app.cors.allowed-origins}")
public class ArticuloController {

    private final ArticuloService articuloService;
    private final StockBodegaService stockService;

    // ============================================================
    // LISTAR - Todos los roles autenticados
    //
    // ⚡ Ahora acepta:
    //   - bodegaId: filtra por bodega
    //   - estadoStock: QUIEBRE | CRITICO | BAJO | NORMAL
    //   - ordenStock:  ASC | DESC (por stock total)
    // ============================================================
    @GetMapping
    @PreAuthorize("hasAnyRole('JEFATURA', 'OPERATIVO', 'AUDITOR', 'ENCARGADO_DEPENDENCIA')")
    public ResponseEntity<ApiResponse<PageResponse<ArticuloResponse>>> listar(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Integer categoriaId,
            @RequestParam(required = false) Integer bodegaId,
            @RequestParam(required = false, defaultValue = "false") Boolean incluirInactivos,
            @RequestParam(required = false) String estadoStock,
            @RequestParam(required = false) String ordenStock,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        return ResponseEntity.ok(ApiResponse.ok(
                articuloService.listar(q, categoriaId, bodegaId, incluirInactivos,
                                       estadoStock, ordenStock, page, size)
        ));
    }

    // ============================================================
    // OBTENER POR ID - Todos los roles
    // ============================================================
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('JEFATURA', 'OPERATIVO', 'AUDITOR', 'ENCARGADO_DEPENDENCIA')")
    public ResponseEntity<ApiResponse<ArticuloResponse>> obtener(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(articuloService.obtenerPorId(id)));
    }

    // ============================================================
    // OBTENER POR SKU - Todos los roles
    // ============================================================
    @GetMapping("/sku/{sku}")
    @PreAuthorize("hasAnyRole('JEFATURA', 'OPERATIVO', 'AUDITOR', 'ENCARGADO_DEPENDENCIA')")
    public ResponseEntity<ApiResponse<ArticuloResponse>> obtenerPorSku(@PathVariable String sku) {
        return ResponseEntity.ok(ApiResponse.ok(articuloService.obtenerPorSku(sku)));
    }

    // ============================================================
    // CREAR - Solo JEFATURA y OPERATIVO
    // ============================================================
    @PostMapping
    @PreAuthorize("hasAnyRole('JEFATURA', 'OPERATIVO')")
    public ResponseEntity<ApiResponse<ArticuloResponse>> crear(
            @Valid @RequestBody ArticuloRequest req,
            Authentication auth) {

        String rutUsuario = auth != null ? auth.getName() : "SYSTEM";
        ArticuloResponse creado = articuloService.crear(req, rutUsuario);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Artículo creado exitosamente", creado));
    }

    // ============================================================
    // ACTUALIZAR - Solo JEFATURA y OPERATIVO
    // ============================================================
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('JEFATURA', 'OPERATIVO')")
    public ResponseEntity<ApiResponse<ArticuloResponse>> actualizar(
            @PathVariable Long id,
            @Valid @RequestBody ArticuloRequest req,
            Authentication auth) {

        String rutUsuario = auth != null ? auth.getName() : "SYSTEM";
        ArticuloResponse actualizado = articuloService.actualizar(id, req, rutUsuario);

        return ResponseEntity.ok(ApiResponse.ok("Artículo actualizado exitosamente", actualizado));
    }

    // ============================================================
    // DESACTIVAR - Solo JEFATURA y OPERATIVO
    // ============================================================
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('JEFATURA', 'OPERATIVO')")
    public ResponseEntity<ApiResponse<Void>> desactivar(
            @PathVariable Long id,
            Authentication auth) {

        String rutUsuario = auth != null ? auth.getName() : "SYSTEM";
        articuloService.desactivar(id, rutUsuario);

        return ResponseEntity.ok(ApiResponse.ok("Artículo desactivado exitosamente", null));
    }

    // ============================================================
    // REACTIVAR - Solo JEFATURA y OPERATIVO
    // ============================================================
    @PostMapping("/{id}/reactivar")
    @PreAuthorize("hasAnyRole('JEFATURA', 'OPERATIVO')")
    public ResponseEntity<ApiResponse<Void>> reactivar(
            @PathVariable Long id,
            Authentication auth) {

        String rutUsuario = auth != null ? auth.getName() : "SYSTEM";
        articuloService.reactivar(id, rutUsuario);

        return ResponseEntity.ok(ApiResponse.ok("Artículo reactivado exitosamente", null));
    }

    // ============================================================
    // LISTAR STOCKS - Todos los roles
    // ============================================================
    @GetMapping("/{id}/stocks")
    @PreAuthorize("hasAnyRole('JEFATURA', 'OPERATIVO', 'AUDITOR', 'ENCARGADO_DEPENDENCIA')")
    public ResponseEntity<ApiResponse<List<ArticuloResponse.StockBodegaDTO>>> listarStocks(
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(stockService.listarPorArticulo(id)));
    }

    // ============================================================
    // AGREGAR A BODEGA - Solo JEFATURA y OPERATIVO
    // ============================================================
    @PostMapping("/{id}/stocks")
    @PreAuthorize("hasAnyRole('JEFATURA', 'OPERATIVO')")
    public ResponseEntity<ApiResponse<ArticuloResponse.StockBodegaDTO>> agregarABodega(
            @PathVariable Long id,
            @Valid @RequestBody StockBodegaRequest req,
            Authentication auth) {

        String rutUsuario = auth != null ? auth.getName() : "SYSTEM";
        ArticuloResponse.StockBodegaDTO stock = stockService.agregarABodega(id, req, rutUsuario);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Artículo agregado a la bodega", stock));
    }

    // ============================================================
    // ACTUALIZAR STOCK - Solo JEFATURA y OPERATIVO
    // ============================================================
    @PutMapping("/{id}/stocks/{bodegaId}")
    @PreAuthorize("hasAnyRole('JEFATURA', 'OPERATIVO')")
    public ResponseEntity<ApiResponse<ArticuloResponse.StockBodegaDTO>> actualizarStock(
            @PathVariable Long id,
            @PathVariable Integer bodegaId,
            @Valid @RequestBody StockBodegaRequest req,
            Authentication auth) {

        String rutUsuario = auth != null ? auth.getName() : "SYSTEM";
        ArticuloResponse.StockBodegaDTO stock = stockService.actualizarStock(
                id, bodegaId, req, rutUsuario);

        return ResponseEntity.ok(ApiResponse.ok("Stock actualizado", stock));
    }

    // ============================================================
    // QUITAR DE BODEGA - Solo JEFATURA y OPERATIVO
    // ============================================================
    @DeleteMapping("/{id}/stocks/{bodegaId}")
    @PreAuthorize("hasAnyRole('JEFATURA', 'OPERATIVO')")
    public ResponseEntity<ApiResponse<Void>> quitarDeBodega(
            @PathVariable Long id,
            @PathVariable Integer bodegaId,
            Authentication auth) {

        String rutUsuario = auth != null ? auth.getName() : "SYSTEM";
        stockService.quitarDeBodega(id, bodegaId, rutUsuario);

        return ResponseEntity.ok(ApiResponse.ok("Artículo quitado de la bodega", null));
    }
}