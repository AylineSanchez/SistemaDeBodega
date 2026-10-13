package cl.dsm.inventario.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Configuración de seguridad del sistema de bodega.
 * 
 * Roles del sistema (según Sección 3.3 del informe):
 *   - JEFATURA              → Control global del sistema
 *   - OPERATIVO             → Operación diaria (recepción, despacho, inventario)
 *   - AUDITOR               → Norma Técnica 226 (trazabilidad sanitaria)
 *   - ENCARGADO_DEPENDENCIA → Solo lectura + creación de pedidos
 * 
 * Matriz de permisos por endpoint (ver documentación en cada bloque)
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {
    
    private final JwtAuthenticationFilter jwtFilter;
    
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> {})
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                
                // ============================================================
                // 1. RECURSOS PÚBLICOS (HTML, CSS, JS, imágenes)
                //    No requieren autenticación
                // ============================================================
                .requestMatchers(
                    "/",
                    "/login.html",
                    "/favicon.ico",
                    "/css/**",
                    "/js/**",
                    "/img/**",
                    "/images/**",
                    // Páginas HTML (el contenido real se protege vía API)
                    "/dashboard.html",
                    "/articulos.html",
                    "/articulo-form.html",
                    "/recepcion.html",
                    "/despacho.html",
                    "/trazabilidad.html",
                    "/reportes.html",
                    "/configuracion.html",
                    "/usuarios.html",
                    "/qr-print.html"
                ).permitAll()
                
                // ============================================================
                // 2. MÓDULO DE USUARIOS (RF-007, RF-092, RF-093)
                //    SOLO JEFATURA
                // ============================================================
                .requestMatchers("/api/usuarios/**").hasRole("JEFATURA")
                
                // ============================================================
                // 3. MÓDULO DE TRAZABILIDAD NT 226 (RF-069, RN-013)
                //    JEFATURA + AUDITOR
                // ============================================================
                .requestMatchers("/api/trazabilidad/**")
                    .hasAnyRole("JEFATURA", "AUDITOR")
                
                // ============================================================
                // 4. MÓDULO DE REPORTES (RF-075 a RF-085)
                //    JEFATURA + OPERATIVO (lectura) + AUDITOR
                // ============================================================
                .requestMatchers("/api/reportes/**")
                    .hasAnyRole("JEFATURA", "OPERATIVO", "AUDITOR")
                
                // ============================================================
                // 5. MÓDULO DE IMPORTACIÓN EXCEL (RF-019)
                //    JEFATURA + OPERATIVO
                // ============================================================
                .requestMatchers("/api/import/**")
                    .hasAnyRole("JEFATURA", "OPERATIVO")
                
                // ============================================================
                // 6. MÓDULO DE RECEPCIÓN (RF-020 a RF-028)
                // ============================================================
                // 6.1 Escritura: JEFATURA + OPERATIVO
                .requestMatchers(HttpMethod.POST, "/api/recepciones/**")
                    .hasAnyRole("JEFATURA", "OPERATIVO")
                .requestMatchers(HttpMethod.PUT, "/api/recepciones/**")
                    .hasAnyRole("JEFATURA", "OPERATIVO")
                .requestMatchers(HttpMethod.DELETE, "/api/recepciones/**")
                    .hasAnyRole("JEFATURA", "OPERATIVO")
                // 6.2 Lectura: JEFATURA + OPERATIVO + AUDITOR (incluye kardex)
                .requestMatchers(HttpMethod.GET, "/api/recepciones/**")
                    .hasAnyRole("JEFATURA", "OPERATIVO", "AUDITOR")
                
                // ============================================================
                // 7. MÓDULO DE DESPACHO (RF-029 a RF-041)
                // ============================================================
                // 7.1 Escritura: JEFATURA + OPERATIVO
                .requestMatchers(HttpMethod.POST, "/api/despachos/**")
                    .hasAnyRole("JEFATURA", "OPERATIVO")
                .requestMatchers(HttpMethod.PUT, "/api/despachos/**")
                    .hasAnyRole("JEFATURA", "OPERATIVO")
                .requestMatchers(HttpMethod.DELETE, "/api/despachos/**")
                    .hasAnyRole("JEFATURA", "OPERATIVO")
                // 7.2 Lectura: todos los roles (el encargado de dependencia ve sus pedidos)
                .requestMatchers(HttpMethod.GET, "/api/despachos/**")
                    .hasAnyRole("JEFATURA", "OPERATIVO", "AUDITOR", "ENCARGADO_DEPENDENCIA")
                
                // 7.3 Pedidos internos (RF-029, RF-040, RF-041)
                .requestMatchers("/api/pedidos/**")
                    .hasAnyRole("JEFATURA", "OPERATIVO", "ENCARGADO_DEPENDENCIA")
                
                // 7.4 Centros de costo (catálogo de servicios)
                .requestMatchers("/api/centros-costo/**")
                    .hasAnyRole("JEFATURA", "OPERATIVO", "ENCARGADO_DEPENDENCIA")
                
                // ============================================================
                // 8. MÓDULO DE ARTÍCULOS / INVENTARIO
                // ============================================================
                // 8.1 Escritura de artículos: JEFATURA + OPERATIVO
                .requestMatchers(HttpMethod.POST, "/api/articulos/**")
                    .hasAnyRole("JEFATURA", "OPERATIVO")
                .requestMatchers(HttpMethod.PUT, "/api/articulos/**")
                    .hasAnyRole("JEFATURA", "OPERATIVO")
                .requestMatchers(HttpMethod.DELETE, "/api/articulos/**")
                    .hasAnyRole("JEFATURA", "OPERATIVO")
                // 8.2 Lectura de artículos: todos los roles (RF-016, RF-033)
                .requestMatchers(HttpMethod.GET, "/api/articulos/**")
                    .hasAnyRole("JEFATURA", "OPERATIVO", "AUDITOR", "ENCARGADO_DEPENDENCIA")
                
                // ============================================================
                // 9. CATÁLOGOS (categorías, bodegas)
                //    Todos los roles autenticados
                // ============================================================
                .requestMatchers("/api/categorias/**").authenticated()
                .requestMatchers("/api/bodegas/**").authenticated()
                
                // ============================================================
                // 10. QRs (códigos QR de artículo+bodega)
                //     Todos los roles autenticados
                // ============================================================
                .requestMatchers("/api/qr/**").authenticated()

                // Configuración: lectura para todos, escritura solo JEFATURA
                .requestMatchers(HttpMethod.GET, "/api/configuracion/**").authenticated()
                .requestMatchers(HttpMethod.PUT, "/api/configuracion/**").hasRole("JEFATURA")
                                
                // ============================================================
                // 11. CUALQUIER OTRA API → autenticado
                // ============================================================
                .requestMatchers("/api/**").authenticated()
                
                // ============================================================
                // 12. CUALQUIER OTRA RUTA → autenticado
                // ============================================================
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        
        return http.build();
    }
}