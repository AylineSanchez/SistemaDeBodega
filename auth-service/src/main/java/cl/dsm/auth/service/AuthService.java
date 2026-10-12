package cl.dsm.auth.service;

import cl.dsm.auth.dto.LoginRequest;
import cl.dsm.auth.dto.LoginResponse;
import cl.dsm.auth.dto.MfaVerifyRequest;
import cl.dsm.auth.entity.Usuario;
import cl.dsm.auth.exception.AuthException;
import cl.dsm.auth.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {
    
    private final UsuarioRepository usuarioRepo;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final MfaService mfaService;
    private final AuditoriaService auditoria;
    
    @Value("${app.security.max-intentos-fallidos}")
    private int maxIntentos;
    
    /**
     * Etapa 1: valida RUT + password.
     * - Si MFA habilitado → devuelve sessionToken temporal (stage=MFA_REQUIRED)
     * - Si MFA deshabilitado → devuelve JWT final (stage=AUTHENTICATED)
     */
    @Transactional
    public LoginResponse iniciarSesion(LoginRequest req, String ip, String userAgent) {
        
        // ✅ Normalizar RUT: acepta "123456785", "12.345.678-5", "12345678-K", etc.
        String rutNormalizado = normalizarRut(req.getRut());
        log.debug("Login attempt: rutOriginal='{}' → rutNormalizado='{}'", 
                  req.getRut(), rutNormalizado);
        
        Usuario usuario = usuarioRepo.findByRut(rutNormalizado)
                .orElseThrow(() -> {
                    auditoria.registrar(rutNormalizado, null, "LOGIN_FAILED",
                            "AUTH", "Usuario no encontrado: " + rutNormalizado, ip, userAgent);
                    return new AuthException("Credenciales inválidas");
                });
        
        if (!usuario.getActivo()) {
            auditoria.registrar(usuario.getRut(), usuario.getNombreCompleto(),
                    "LOGIN_FAILED", "AUTH", "Usuario desactivado", ip, userAgent);
            throw new AuthException("Usuario desactivado. Contacte al administrador.");
        }
        
        if (usuario.getBloqueado()) {
            auditoria.registrar(usuario.getRut(), usuario.getNombreCompleto(),
                    "LOGIN_BLOCKED", "AUTH", "Intento con cuenta bloqueada", ip, userAgent);
            throw new AuthException("Cuenta bloqueada. Contacte al administrador.");
        }
        
        if (!passwordEncoder.matches(req.getPassword(), usuario.getPasswordHash())) {
            usuarioRepo.incrementarIntentosFallidos(usuario.getId());
            int intentos = usuario.getIntentosFallidos() + 1;
            
            auditoria.registrar(usuario.getRut(), usuario.getNombreCompleto(),
                    "LOGIN_FAILED", "AUTH", 
                    "Contraseña incorrecta (intento " + intentos + "/" + maxIntentos + ")",
                    ip, userAgent);
            
            if (intentos >= maxIntentos) {
                usuarioRepo.bloquearUsuario(usuario.getId());
                auditoria.registrar(usuario.getRut(), usuario.getNombreCompleto(),
                        "USER_BLOCKED", "AUTH", 
                        "Bloqueado tras " + maxIntentos + " intentos fallidos",
                        ip, userAgent);
                throw new AuthException("Cuenta bloqueada tras " + maxIntentos + 
                        " intentos fallidos. Contacte al administrador.");
            }
            throw new AuthException("Credenciales inválidas. Intentos restantes: " + 
                    (maxIntentos - intentos));
        }
        
        // Credenciales correctas
        usuarioRepo.resetearIntentosFallidos(usuario.getId());
        
        if (usuario.getMfaEnabled()) {
            try {
                mfaService.generarCodigo(usuario);
                usuarioRepo.save(usuario);
                
                String sessionToken = jwtService.generarTokenTemporalMfa(usuario);
                
                auditoria.registrar(usuario.getRut(), usuario.getNombreCompleto(),
                        "LOGIN_MFA_PENDING", "AUTH", 
                        "Credenciales OK, esperando verificación MFA", ip, userAgent);
                
                return LoginResponse.builder()
                        .success(true)
                        .stage("MFA_REQUIRED")
                        .message("Se envió un código de verificación a " + maskEmail(usuario.getEmail()))
                        .sessionToken(sessionToken)
                        .build();
                        
            } catch (Exception e) {
                log.error("Error al enviar código MFA: {}", e.getMessage(), e);
                auditoria.registrar(usuario.getRut(), usuario.getNombreCompleto(),
                        "MFA_EMAIL_FAILED", "AUTH", 
                        "Error al enviar email: " + e.getMessage(), ip, userAgent);
                throw new AuthException(
                    "No se pudo enviar el código de verificación. Contacte al administrador.");
            }
        }
        
        return completarLogin(usuario, ip, userAgent);
    }
    
    /**
     * Etapa 2: valida código MFA y emite JWT final.
     */
    @Transactional
    public LoginResponse verificarMfa(MfaVerifyRequest req, String ip, String userAgent) {
        
        if (!jwtService.esValido(req.getSessionToken())) {
            throw new AuthException("Sesión expirada. Vuelva a iniciar sesión.");
        }
        
        String rut = jwtService.extraerRut(req.getSessionToken());
        // El RUT del JWT ya está normalizado
        String rutNormalizado = normalizarRut(rut);
        
        Usuario usuario = usuarioRepo.findByRut(rutNormalizado)
                .orElseThrow(() -> new AuthException("Usuario no encontrado"));
        
        if (!mfaService.validarCodigo(usuario, req.getCodigo())) {
            auditoria.registrar(usuario.getRut(), usuario.getNombreCompleto(),
                    "MFA_FAILED", "AUTH", "Código MFA inválido o expirado", ip, userAgent);
            throw new AuthException("Código inválido o expirado");
        }
        
        // Limpiar código usado (evita replay)
        usuario.setUltimoCodigoMfa(null);
        usuario.setUltimoCodigoMfaExpira(null);
        usuarioRepo.save(usuario);
        
        return completarLogin(usuario, ip, userAgent);
    }
    
    /**
     * Completa el login: emite JWT final y registra en auditoría.
     */
    private LoginResponse completarLogin(Usuario usuario, String ip, String userAgent) {
        usuario.setUltimoAcceso(LocalDateTime.now());
        usuarioRepo.save(usuario);
        
        String jwt = jwtService.generarToken(usuario);
        
        auditoria.registrar(usuario.getRut(), usuario.getNombreCompleto(),
                "LOGIN_SUCCESS", "AUTH", "Inicio de sesión exitoso", ip, userAgent);
        
        log.info("Login exitoso: {} ({})", usuario.getRut(), usuario.getRol().getNombre());
        
        return LoginResponse.builder()
                .success(true)
                .stage("AUTHENTICATED")
                .message("Bienvenido/a, " + usuario.getNombreCompleto())
                .token(jwt)
                .tokenType("Bearer")
                .expiresIn(jwtService.getExpirationMs() / 1000)
                .usuario(LoginResponse.UsuarioInfo.builder()
                        .rut(usuario.getRut())
                        .nombreCompleto(usuario.getNombreCompleto())
                        .email(usuario.getEmail())
                        .rol(usuario.getRol().getNombre())
                        .dependencia(usuario.getDependencia())
                        .build())
                .build();
    }
    
    /**
     * ============================================================
     * NORMALIZACIÓN DE RUT CHILENO
     * ============================================================
     * Convierte cualquier entrada al formato canónico "12.345.678-5".
     * 
     * Ejemplos:
     *   "123456785"       → "12.345.678-5"
     *   "12345678-5"      → "12.345.678-5"
     *   "12.345.678-5"    → "12.345.678-5"  (idempotente)
     *   "12.345.6785"     → "12.345.678-5"
     *   "12345678k"       → "12.345.678-K"
     *   "12345678K"       → "12.345.678-K"
     *   "12345678-k"      → "12.345.678-K"
     *   "  12345678-5  "  → "12.345.678-5"  (trim)
     */
    private String normalizarRut(String rut) {
        if (rut == null || rut.isBlank()) return rut;
        
        // 1. Limpiar: solo dígitos y K/k
        String limpio = rut.replaceAll("[^0-9kK]", "").toUpperCase();
        if (limpio.length() < 2) return rut.trim();
        
        // 2. Separar cuerpo y DV
        String cuerpo = limpio.substring(0, limpio.length() - 1);
        String dv = limpio.substring(limpio.length() - 1);
        
        // 3. Formatear cuerpo con puntos cada 3 dígitos desde la derecha
        StringBuilder sb = new StringBuilder();
        int contador = 0;
        for (int i = cuerpo.length() - 1; i >= 0; i--) {
            sb.insert(0, cuerpo.charAt(i));
            contador++;
            if (contador % 3 == 0 && i > 0) {
                sb.insert(0, '.');
            }
        }
        
        return sb.toString() + "-" + dv;
    }
    
    /**
     * Enmascara un email para mostrarlo parcialmente.
     * "claudia@dsm.cl" → "c***a@dsm.cl"
     */
    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) return email;
        int at = email.indexOf('@');
        if (at <= 2) return "***" + email.substring(at);
        return email.charAt(0) + "***" + email.substring(at - 1);
    }
}