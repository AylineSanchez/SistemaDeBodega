package cl.dsm.inventario.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.util.List;

/**
 * Filtro que valida el JWT emitido por el auth-service.
 * 
 * Genera las authorities con el prefijo ROLE_ para que funcionen
 * con hasRole() y hasAnyRole() de Spring Security.
 */
@Slf4j
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    
    @Value("${app.jwt.secret}")
    private String secret;
    
    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
    }
    
    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        
        // Buscar token en header O en query param (para imágenes QR)
        String token = null;
        String header = request.getHeader("Authorization");
        
        if (header != null && header.startsWith("Bearer ")) {
            token = header.substring(7);
        } else {
            String tokenParam = request.getParameter("token");
            if (tokenParam != null && !tokenParam.isBlank()) {
                token = tokenParam;
            }
        }
        
        if (token != null) {
            try {
                Claims claims = Jwts.parser()
                        .verifyWith(getSigningKey())
                        .build()
                        .parseSignedClaims(token)
                        .getPayload();
                
                String rut = claims.getSubject();
                String rol = claims.get("rol", String.class);
                String nombre = claims.get("nombre", String.class);
                
                // ⚡ IMPORTANTE: prefijo ROLE_ para hasRole()
                var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + rol));
                
                var auth = new UsernamePasswordAuthenticationToken(rut, null, authorities);
                auth.setDetails(nombre);
                
                SecurityContextHolder.getContext().setAuthentication(auth);
                
                log.debug("JWT válido: usuario={}, rol={}", rut, rol);
                
            } catch (Exception e) {
                log.debug("Token JWT inválido: {}", e.getMessage());
            }
        }
        
        filterChain.doFilter(request, response);
    }
}