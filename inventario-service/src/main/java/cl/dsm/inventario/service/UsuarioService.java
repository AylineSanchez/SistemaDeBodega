package cl.dsm.inventario.service;

import cl.dsm.inventario.dto.UsuarioDTO;
import cl.dsm.inventario.dto.UsuarioRequest;
import cl.dsm.inventario.exception.BusinessException;
import cl.dsm.inventario.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Servicio de gestión de usuarios.
 * 
 * Usa JdbcTemplate directamente porque la tabla `usuarios` está en el
 * auth-service y no tenemos la entidad JPA aquí. Solo consultamos y
 * actualizamos vía SQL.
 * 
 * ⚠️ Solo accesible desde JEFATURA.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UsuarioService {
    
    private final JdbcTemplate jdbc;
    
    // ============================================================
    // LISTAR TODOS LOS USUARIOS
    // ============================================================
    @Transactional(readOnly = true)
    public List<UsuarioDTO> listarTodos() {
        String sql = """
            SELECT u.id, u.rut, u.nombre_completo, u.email,
                   r.nombre AS rol, u.dependencia,
                   u.activo, u.bloqueado, u.intentos_fallidos,
                   u.mfa_enabled, u.ultimo_acceso, u.fecha_creacion
            FROM usuarios u
            JOIN roles r ON r.id = u.rol_id
            ORDER BY r.id, u.nombre_completo
        """;
        
        return jdbc.query(sql, new UsuarioRowMapper());
    }
    
    // ============================================================
    // OBTENER POR ID
    // ============================================================
    @Transactional(readOnly = true)
    public UsuarioDTO obtenerPorId(Long id) {
        String sql = """
            SELECT u.id, u.rut, u.nombre_completo, u.email,
                   r.nombre AS rol, u.dependencia,
                   u.activo, u.bloqueado, u.intentos_fallidos,
                   u.mfa_enabled, u.ultimo_acceso, u.fecha_creacion
            FROM usuarios u
            JOIN roles r ON r.id = u.rol_id
            WHERE u.id = ?
        """;
        
        List<UsuarioDTO> result = jdbc.query(sql, new UsuarioRowMapper(), id);
        
        if (result.isEmpty()) {
            throw new ResourceNotFoundException("Usuario no encontrado: " + id);
        }
        
        return result.get(0);
    }
    
    // ============================================================
    // CREAR USUARIO
    // ============================================================
    @Transactional
    public UsuarioDTO crear(UsuarioRequest req, String rutAdmin) {
        
        // Validar RUT único
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM usuarios WHERE rut = ?",
                Integer.class,
                req.getRut()
        );
        
        if (count != null && count > 0) {
            throw new BusinessException("Ya existe un usuario con RUT: " + req.getRut());
        }
        
        // Validar email único
        Integer countEmail = jdbc.queryForObject(
                "SELECT COUNT(*) FROM usuarios WHERE email = ?",
                Integer.class,
                req.getEmail()
        );
        
        if (countEmail != null && countEmail > 0) {
            throw new BusinessException("Ya existe un usuario con email: " + req.getEmail());
        }
        
        // Obtener rol_id
        Integer rolId = jdbc.queryForObject(
                "SELECT id FROM roles WHERE nombre = ?",
                Integer.class,
                req.getRol()
        );
        
        if (rolId == null) {
            throw new BusinessException("Rol no válido: " + req.getRol());
        }
        
        // Password por defecto (debe ser cambiada por el usuario en el primer login)
        String defaultPasswordHash = "$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/LewY5GyYqVr/3KZ5u";
        
        // Insertar
        String sql = """
            INSERT INTO usuarios 
                (rut, nombre_completo, email, password_hash, rol_id, dependencia,
                 activo, bloqueado, intentos_fallidos, mfa_enabled, fecha_creacion, fecha_actualizacion)
            VALUES (?, ?, ?, ?, ?, ?, TRUE, FALSE, 0, TRUE, NOW(), NOW())
            RETURNING id
        """;
        
        Long nuevoId = jdbc.queryForObject(sql, Long.class,
                req.getRut(),
                req.getNombreCompleto(),
                req.getEmail(),
                defaultPasswordHash,
                rolId,
                req.getDependencia()
        );
        
        log.info("Usuario creado: {} (RUT: {}) por {}", req.getNombreCompleto(), req.getRut(), rutAdmin);
        
        return obtenerPorId(nuevoId);
    }
    
    // ============================================================
    // ACTUALIZAR USUARIO
    // ============================================================
    @Transactional
    public UsuarioDTO actualizar(Long id, UsuarioRequest req, String rutAdmin) {
        
        // Verificar que existe
        UsuarioDTO existente = obtenerPorId(id);
        
        // Validar RUT único (si cambió)
        if (!req.getRut().equals(existente.getRut())) {
            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM usuarios WHERE rut = ? AND id != ?",
                    Integer.class,
                    req.getRut(),
                    id
            );
            
            if (count != null && count > 0) {
                throw new BusinessException("Ya existe otro usuario con RUT: " + req.getRut());
            }
        }
        
        // Validar email único
        if (!req.getEmail().equals(existente.getEmail())) {
            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM usuarios WHERE email = ? AND id != ?",
                    Integer.class,
                    req.getEmail(),
                    id
            );
            
            if (count != null && count > 0) {
                throw new BusinessException("Ya existe otro usuario con email: " + req.getEmail());
            }
        }
        
        // Obtener rol_id
        Integer rolId = jdbc.queryForObject(
                "SELECT id FROM roles WHERE nombre = ?",
                Integer.class,
                req.getRol()
        );
        
        if (rolId == null) {
            throw new BusinessException("Rol no válido: " + req.getRol());
        }
        
        // Actualizar
        String sql = """
            UPDATE usuarios SET
                rut = ?,
                nombre_completo = ?,
                email = ?,
                rol_id = ?,
                dependencia = ?,
                fecha_actualizacion = NOW()
            WHERE id = ?
        """;
        
        jdbc.update(sql,
                req.getRut(),
                req.getNombreCompleto(),
                req.getEmail(),
                rolId,
                req.getDependencia(),
                id
        );
        
        log.info("Usuario actualizado: {} por {}", req.getRut(), rutAdmin);
        
        return obtenerPorId(id);
    }
    
    // ============================================================
    // DESBLOQUEAR
    // ============================================================
    @Transactional
    public void desbloquear(Long id, String rutAdmin) {
        int updated = jdbc.update("""
            UPDATE usuarios SET
                bloqueado = FALSE,
                intentos_fallidos = 0,
                fecha_actualizacion = NOW()
            WHERE id = ?
        """, id);
        
        if (updated == 0) {
            throw new ResourceNotFoundException("Usuario no encontrado: " + id);
        }
        
        log.info("Usuario {} desbloqueado por {}", id, rutAdmin);
    }
    
    // ============================================================
    // DESACTIVAR (baja lógica)
    // ============================================================
    @Transactional
    public void desactivar(Long id, String rutAdmin) {
        int updated = jdbc.update("""
            UPDATE usuarios SET
                activo = FALSE,
                fecha_actualizacion = NOW()
            WHERE id = ?
        """, id);
        
        if (updated == 0) {
            throw new ResourceNotFoundException("Usuario no encontrado: " + id);
        }
        
        log.info("Usuario {} desactivado por {}", id, rutAdmin);
    }
    
    // ============================================================
    // REACTIVAR
    // ============================================================
    @Transactional
    public void reactivar(Long id, String rutAdmin) {
        int updated = jdbc.update("""
            UPDATE usuarios SET
                activo = TRUE,
                fecha_actualizacion = NOW()
            WHERE id = ?
        """, id);
        
        if (updated == 0) {
            throw new ResourceNotFoundException("Usuario no encontrado: " + id);
        }
        
        log.info("Usuario {} reactivado por {}", id, rutAdmin);
    }
    
    // ============================================================
    // ROW MAPPER
    // ============================================================
    private static class UsuarioRowMapper implements RowMapper<UsuarioDTO> {
        @Override
        public UsuarioDTO mapRow(ResultSet rs, int rowNum) throws SQLException {
            return UsuarioDTO.builder()
                    .id(rs.getLong("id"))
                    .rut(rs.getString("rut"))
                    .nombreCompleto(rs.getString("nombre_completo"))
                    .email(rs.getString("email"))
                    .rol(rs.getString("rol"))
                    .dependencia(rs.getString("dependencia"))
                    .activo(rs.getBoolean("activo"))
                    .bloqueado(rs.getBoolean("bloqueado"))
                    .intentosFallidos(rs.getInt("intentos_fallidos"))
                    .mfaEnabled(rs.getBoolean("mfa_enabled"))
                    .ultimoAcceso(rs.getTimestamp("ultimo_acceso") != null
                            ? rs.getTimestamp("ultimo_acceso").toLocalDateTime() : null)
                    .fechaCreacion(rs.getTimestamp("fecha_creacion") != null
                            ? rs.getTimestamp("fecha_creacion").toLocalDateTime() : null)
                    .build();
        }
    }
}