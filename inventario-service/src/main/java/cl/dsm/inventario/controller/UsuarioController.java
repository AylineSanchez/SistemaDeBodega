package cl.dsm.inventario.controller;

import cl.dsm.inventario.dto.ApiResponse;
import cl.dsm.inventario.dto.UsuarioDTO;
import cl.dsm.inventario.dto.UsuarioRequest;
import cl.dsm.inventario.service.UsuarioService;
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
 * Controller del módulo de Gestión de Usuarios.
 * 
 * ⚠️ SOLO JEFATURA puede acceder (RF-007, RF-092, RF-093).
 */
@Slf4j
@RestController
@RequestMapping("/api/usuarios")
@RequiredArgsConstructor
@CrossOrigin(origins = "${app.cors.allowed-origins}")
@PreAuthorize("hasRole('JEFATURA')")
public class UsuarioController {
    
    private final UsuarioService usuarioService;
    
    // ============================================================
    // LISTAR TODOS LOS USUARIOS
    // ============================================================
    @GetMapping
    public ResponseEntity<ApiResponse<List<UsuarioDTO>>> listar() {
        return ResponseEntity.ok(ApiResponse.ok(usuarioService.listarTodos()));
    }
    
    // ============================================================
    // OBTENER USUARIO POR ID
    // ============================================================
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<UsuarioDTO>> obtener(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(usuarioService.obtenerPorId(id)));
    }
    
    // ============================================================
    // CREAR NUEVO USUARIO
    // ============================================================
    @PostMapping
    public ResponseEntity<ApiResponse<UsuarioDTO>> crear(
            @Valid @RequestBody UsuarioRequest req,
            Authentication auth) {
        
        log.info("Creando usuario por {}: {}", auth.getName(), req.getRut());
        UsuarioDTO creado = usuarioService.crear(req, auth.getName());
        
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Usuario creado exitosamente", creado));
    }
    
    // ============================================================
    // ACTUALIZAR USUARIO
    // ============================================================
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<UsuarioDTO>> actualizar(
            @PathVariable Long id,
            @Valid @RequestBody UsuarioRequest req,
            Authentication auth) {
        
        UsuarioDTO actualizado = usuarioService.actualizar(id, req, auth.getName());
        
        return ResponseEntity.ok(ApiResponse.ok(
                "Usuario actualizado exitosamente",
                actualizado
        ));
    }
    
    // ============================================================
    // DESBLOQUEAR USUARIO (tras 3 intentos fallidos)
    // ============================================================
    @PostMapping("/{id}/desbloquear")
    public ResponseEntity<ApiResponse<Void>> desbloquear(
            @PathVariable Long id,
            Authentication auth) {
        
        usuarioService.desbloquear(id, auth.getName());
        
        return ResponseEntity.ok(ApiResponse.ok(
                "Usuario desbloqueado. Ya puede iniciar sesión.",
                null
        ));
    }
    
    // ============================================================
    // DESACTIVAR USUARIO (baja lógica)
    // ============================================================
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> desactivar(
            @PathVariable Long id,
            Authentication auth) {
        
        usuarioService.desactivar(id, auth.getName());
        
        return ResponseEntity.ok(ApiResponse.ok("Usuario desactivado", null));
    }
    
    // ============================================================
    // REACTIVAR USUARIO
    // ============================================================
    @PostMapping("/{id}/reactivar")
    public ResponseEntity<ApiResponse<Void>> reactivar(
            @PathVariable Long id,
            Authentication auth) {
        
        usuarioService.reactivar(id, auth.getName());
        
        return ResponseEntity.ok(ApiResponse.ok("Usuario reactivado", null));
    }
}