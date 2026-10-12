package cl.dsm.auth.controller;

import cl.dsm.auth.dto.ApiResponse;
import cl.dsm.auth.dto.ForgotPasswordRequest;
import cl.dsm.auth.dto.ResetPasswordRequest;
import cl.dsm.auth.service.PasswordResetService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth/password")
@RequiredArgsConstructor
@CrossOrigin(origins = "${app.cors.allowed-origins}")
public class PasswordResetController {
    
    private final PasswordResetService resetService;
    
    /**
     * POST /api/auth/password/forgot
     */
    @PostMapping("/forgot")
    public ResponseEntity<ApiResponse<Map<String, String>>> forgot(
            @Valid @RequestBody ForgotPasswordRequest req,
            HttpServletRequest http) {
        
        resetService.solicitarReset(req, getClientIp(http), http.getHeader("User-Agent"));
        
        return ResponseEntity.ok(ApiResponse.ok(
                "Si el usuario existe, recibirá un email con las instrucciones.",
                Map.of("identificador", req.getIdentificador())
        ));
    }
    
    /**
     * POST /api/auth/password/reset
     */
    @PostMapping("/reset")
    public ResponseEntity<ApiResponse<Void>> reset(
            @Valid @RequestBody ResetPasswordRequest req,
            HttpServletRequest http) {
        
        resetService.resetearPassword(req, getClientIp(http), http.getHeader("User-Agent"));
        
        return ResponseEntity.ok(ApiResponse.ok(
                "Contraseña restablecida exitosamente. Ya puede iniciar sesión.",
                null
        ));
    }
    
    private String getClientIp(HttpServletRequest req) {
        String xf = req.getHeader("X-Forwarded-For");
        if (xf != null && !xf.isBlank()) {
            return xf.split(",")[0].trim();
        }
        return req.getRemoteAddr();
    }
}