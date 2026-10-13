package cl.dsm.inventario.controller;

import cl.dsm.inventario.dto.*;
import cl.dsm.inventario.service.ConfiguracionService;
import cl.dsm.inventario.service.NotificacionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/configuracion")
@RequiredArgsConstructor
@CrossOrigin(origins = "${app.cors.allowed-origins}")
public class ConfiguracionController {

    private final ConfiguracionService configService;
    private final NotificacionService notificacionService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<ConfiguracionDTO>>> listar() {
        return ResponseEntity.ok(ApiResponse.ok(configService.listarTodas()));
    }

    @PutMapping
    @PreAuthorize("hasRole('JEFATURA')")
    public ResponseEntity<ApiResponse<ConfiguracionDTO>> actualizar(
            @Valid @RequestBody ConfiguracionUpdateRequest req,
            Authentication auth) {

        String rutUsuario = auth != null ? auth.getName() : "SYSTEM";
        ConfiguracionDTO actualizado = configService.actualizar(req, rutUsuario);

        return ResponseEntity.ok(ApiResponse.ok("Configuración actualizada", actualizado));
    }

    @PostMapping("/probar-notificacion-critico")
    @PreAuthorize("hasRole('JEFATURA')")
    public ResponseEntity<ApiResponse<String>> probarNotificacionCritico() {
        notificacionService.verificarYNotificarStockCritico();
        return ResponseEntity.ok(ApiResponse.ok(
                "Notificación de stock crítico enviada (revisa el correo)",
                "OK"));
    }

    // ⚡ NUEVO: Probar notificación de vencimientos
    @PostMapping("/probar-notificacion-vencimiento")
    @PreAuthorize("hasRole('JEFATURA')")
    public ResponseEntity<ApiResponse<String>> probarNotificacionVencimiento() {
        notificacionService.verificarYNotificarVencimientos();
        return ResponseEntity.ok(ApiResponse.ok(
                "Notificación de vencimientos enviada (revisa el correo)",
                "OK"));
    }
}