package cl.dsm.auth.service;

import cl.dsm.auth.entity.Usuario;
import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class JwtService {
    
    @Value("${app.jwt.secret}")
    private String secret;
    
    @Value("${app.jwt.expiration-ms}")
    private long expirationMs;
    
    private SecretKey getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secret);
        return Keys.hmacShaKeyFor(keyBytes);
    }
    
    /**
     * Genera JWT definitivo tras autenticación completa (con o sin MFA).
     */
    public String generarToken(Usuario usuario) {
        Date now = new Date();
        Date exp = new Date(now.getTime() + expirationMs);
        
        Map<String, Object> claims = new HashMap<>();
        claims.put("nombre", usuario.getNombreCompleto());
        claims.put("email", usuario.getEmail());
        claims.put("rol", usuario.getRol().getNombre());
        
        if (usuario.getDependencia() != null && !usuario.getDependencia().isBlank()) {
            claims.put("dependencia", usuario.getDependencia());
        }
        
        return Jwts.builder()
                .subject(usuario.getRut())
                .claims(claims)
                .issuedAt(now)
                .expiration(exp)
                .signWith(getSigningKey())
                .compact();
    }
    
    /**
     * Genera token temporal (5 min) para la etapa intermedia entre login y MFA.
     */
    public String generarTokenTemporalMfa(Usuario usuario) {
        Date now = new Date();
        Date exp = new Date(now.getTime() + 5 * 60 * 1000);
        
        return Jwts.builder()
                .subject(usuario.getRut())
                .claim("stage", "MFA_PENDING")
                .issuedAt(now)
                .expiration(exp)
                .signWith(getSigningKey())
                .compact();
    }
    
    public Claims parsear(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
    
    public String extraerRut(String token) {
        return parsear(token).getSubject();
    }
    
    public boolean esValido(String token) {
        try {
            parsear(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Token inválido: {}", e.getMessage());
            return false;
        }
    }
    
    public long getExpirationMs() {
        return expirationMs;
    }
}