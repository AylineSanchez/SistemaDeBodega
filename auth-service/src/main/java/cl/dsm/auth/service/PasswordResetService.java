package cl.dsm.auth.service;

import cl.dsm.auth.dto.ForgotPasswordRequest;
import cl.dsm.auth.dto.ResetPasswordRequest;
import cl.dsm.auth.entity.PasswordResetToken;
import cl.dsm.auth.entity.Usuario;
import cl.dsm.auth.exception.AuthException;
import cl.dsm.auth.repository.PasswordResetTokenRepository;
import cl.dsm.auth.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;

@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetService {
    
    private final UsuarioRepository usuarioRepo;
    private final PasswordResetTokenRepository tokenRepo;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final AuditoriaService auditoria;
    
    @Value("${app.frontend.base-url}")
    private String frontendBaseUrl;
    
    @Value("${app.password-reset.token-expiration-minutes}")
    private int tokenExpirationMinutes;
    
    @Value("${app.password-reset.max-solicitudes-por-hora}")
    private int maxSolicitudesPorHora;
    
    private final SecureRandom random = new SecureRandom();
    
    @Transactional
    public void solicitarReset(ForgotPasswordRequest req, String ip, String userAgent) {
        
        String identificador = req.getIdentificador().trim();
        Usuario usuario = buscarPorIdentificador(identificador);
        
        if (usuario == null) {
            log.warn("Solicitud de reset para identificador inexistente: {}", identificador);
            auditoria.registrar(null, null, "PASSWORD_RESET_REQUEST_UNKNOWN",
                    "AUTH", "Solicitud para identificador inexistente: " + identificador,
                    ip, userAgent);
            return;
        }
        
        long solicitudesRecientes = tokenRepo.contarSolicitudesRecientes(
                usuario.getId(), LocalDateTime.now().minusHours(1));
        
        if (solicitudesRecientes >= maxSolicitudesPorHora) {
            log.warn("Rate limit alcanzado para usuario: {}", usuario.getRut());
            auditoria.registrar(usuario.getRut(), usuario.getNombreCompleto(),
                    "PASSWORD_RESET_RATE_LIMIT", "AUTH",
                    "Demasiadas solicitudes en la última hora", ip, userAgent);
            throw new AuthException(
                "Ha solicitado demasiados cambios. Espere una hora antes de reintentar.");
        }
        
        tokenRepo.invalidarTokensAnteriores(usuario.getId(), LocalDateTime.now());
        
        String token = generarTokenSeguro();
        
        PasswordResetToken resetToken = PasswordResetToken.builder()
                .usuario(usuario)
                .token(token)
                .expiraEn(LocalDateTime.now().plusMinutes(tokenExpirationMinutes))
                .usado(false)
                .ipSolicitud(ip)
                .userAgent(userAgent)
                .build();
        
        tokenRepo.save(resetToken);
        
        String enlace = frontendBaseUrl + "/reset-password.html?token=" + token;
        emailService.enviarEmailResetPassword(
                usuario.getEmail(),
                usuario.getNombreCompleto(),
                enlace,
                tokenExpirationMinutes
        );
        
        auditoria.registrar(usuario.getRut(), usuario.getNombreCompleto(),
                "PASSWORD_RESET_REQUESTED", "AUTH",
                "Solicitud de reseteo enviada a " + maskEmail(usuario.getEmail()),
                ip, userAgent);
        
        log.info("Token de reset generado para usuario {}", usuario.getRut());
    }
    
    @Transactional
    public void resetearPassword(ResetPasswordRequest req, String ip, String userAgent) {
        
        if (!req.getNuevaPassword().equals(req.getConfirmarPassword())) {
            throw new AuthException("Las contraseñas no coinciden");
        }
        
        PasswordResetToken resetToken = tokenRepo.findByToken(req.getToken())
                .orElseThrow(() -> {
                    auditoria.registrar(null, null, "PASSWORD_RESET_INVALID_TOKEN",
                            "AUTH", "Token no encontrado", ip, userAgent);
                    return new AuthException("Token inválido o expirado");
                });
        
        if (!resetToken.isValido()) {
            auditoria.registrar(resetToken.getUsuario().getRut(),
                    resetToken.getUsuario().getNombreCompleto(),
                    "PASSWORD_RESET_INVALID_TOKEN", "AUTH",
                    resetToken.isExpirado() ? "Token expirado" : "Token ya usado",
                    ip, userAgent);
            throw new AuthException("Token inválido o expirado");
        }
        
        Usuario usuario = resetToken.getUsuario();
        
        usuario.setPasswordHash(passwordEncoder.encode(req.getNuevaPassword()));
        usuario.setIntentosFallidos(0);
        usuario.setBloqueado(false);
        usuario.setFechaActualizacion(LocalDateTime.now());
        usuarioRepo.save(usuario);
        
        resetToken.setUsado(true);
        resetToken.setFechaUso(LocalDateTime.now());
        tokenRepo.save(resetToken);
        
        auditoria.registrar(usuario.getRut(), usuario.getNombreCompleto(),
                "PASSWORD_RESET_SUCCESS", "AUTH",
                "Contraseña restablecida exitosamente", ip, userAgent);
        
        log.info("Contraseña restablecida para usuario {}", usuario.getRut());
    }
    
    /**
     * Limpieza automática de tokens expirados cada 6 horas.
     * ⚠️ IMPORTANTE: @Transactional es obligatorio para @Modifying queries.
     */
    @Scheduled(fixedRate = 6 * 60 * 60 * 1000)
    @Transactional
    public void limpiarTokensExpirados() {
        try {
            int eliminados = tokenRepo.eliminarExpirados(
                    LocalDateTime.now().minusDays(1));
            if (eliminados > 0) {
                log.info("Tokens expirados eliminados: {}", eliminados);
            }
        } catch (Exception e) {
            log.error("Error al limpiar tokens expirados: {}", e.getMessage());
        }
    }
    
    private Usuario buscarPorIdentificador(String identificador) {
        if (identificador.contains("@")) {
            return usuarioRepo.findByEmail(identificador).orElse(null);
        }
        return usuarioRepo.findByRut(identificador).orElse(null);
    }
    
    private String generarTokenSeguro() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    
    private String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 2) return "***" + email.substring(at);
        return email.charAt(0) + "***" + email.substring(at - 1);
    }
}