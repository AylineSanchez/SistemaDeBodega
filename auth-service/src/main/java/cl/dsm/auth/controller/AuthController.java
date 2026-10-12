package cl.dsm.auth.controller;

import cl.dsm.auth.dto.*;
import cl.dsm.auth.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@CrossOrigin(origins = "${app.cors.allowed-origins}")
public class AuthController {
    
    private final AuthService authService;
    
    /**
     * POST /api/auth/login
     * Etapa 1: valida RUT + password.
     */
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest req,
                                                HttpServletRequest http) {
        log.debug("Login attempt: {}", req.getRut());
        LoginResponse resp = authService.iniciarSesion(
                req,
                getClientIp(http),
                http.getHeader("User-Agent")
        );
        return ResponseEntity.ok(resp);
    }
    
    /**
     * POST /api/auth/mfa/verify
     * Etapa 2: valida código MFA y emite JWT final.
     */
    @PostMapping("/mfa/verify")
    public ResponseEntity<LoginResponse> verifyMfa(@Valid @RequestBody MfaVerifyRequest req,
                                                    HttpServletRequest http) {
        LoginResponse resp = authService.verificarMfa(
                req,
                getClientIp(http),
                http.getHeader("User-Agent")
        );
        return ResponseEntity.ok(resp);
    }
    
    /**
     * GET /api/auth/health
     */
    @GetMapping("/health")
    public ResponseEntity<ApiResponse<String>> health() {
        return ResponseEntity.ok(ApiResponse.ok("Auth service OK"));
    }
    
    private String getClientIp(HttpServletRequest req) {
        String xf = req.getHeader("X-Forwarded-For");
        if (xf != null && !xf.isBlank()) {
            return xf.split(",")[0].trim();
        }
        return req.getRemoteAddr();
    }
}