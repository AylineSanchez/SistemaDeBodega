package cl.dsm.auth.service;

import cl.dsm.auth.entity.Usuario;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class MfaService {
    
    private final EmailService emailService;
    
    @Value("${app.mfa.longitud-codigo}")
    private int longitud;
    
    @Value("${app.mfa.codigo-expiracion-segundos}")
    private long expiracionSegundos;
    
    private final SecureRandom random = new SecureRandom();
    
    /**
     * Genera un código numérico aleatorio y lo envía por email al usuario.
     */
    public String generarCodigo(Usuario usuario) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < longitud; i++) {
            sb.append(random.nextInt(10));
        }
        String codigo = sb.toString();
        
        usuario.setUltimoCodigoMfa(codigo);
        usuario.setUltimoCodigoMfaExpira(
            LocalDateTime.now().plusSeconds(expiracionSegundos)
        );
        
        // Enviar por email (asíncrono)
        int minutos = (int) (expiracionSegundos / 60);
        emailService.enviarEmailCodigoMfa(
            usuario.getEmail(),
            usuario.getNombreCompleto(),
            codigo,
            minutos
        );
        
        // Log de debug (útil si el email falla)
        log.debug(">>> [MFA] Código generado para {} ({}): {}", 
                  usuario.getNombreCompleto(), usuario.getEmail(), codigo);
        
        return codigo;
    }
    
    public boolean validarCodigo(Usuario usuario, String codigoIngresado) {
        if (usuario.getUltimoCodigoMfa() == null) return false;
        if (usuario.getUltimoCodigoMfaExpira() == null) return false;
        if (LocalDateTime.now().isAfter(usuario.getUltimoCodigoMfaExpira())) return false;
        return usuario.getUltimoCodigoMfa().equals(codigoIngresado);
    }
}