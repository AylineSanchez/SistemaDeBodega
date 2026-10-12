package cl.dsm.auth.service;

import cl.dsm.auth.entity.LogAuditoria;
import cl.dsm.auth.repository.LogAuditoriaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditoriaService {
    
    private final LogAuditoriaRepository repo;
    
    /**
     * Registra un evento en el log de auditoría.
     * Usa REQUIRES_NEW para que se guarde incluso si la transacción principal falla.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrar(String rut, String nombre, String accion,
                          String modulo, String descripcion,
                          String ip, String userAgent) {
        try {
            LogAuditoria logEntry = LogAuditoria.builder()
                    .usuarioRut(rut)
                    .usuarioNombre(nombre)
                    .accion(accion)
                    .modulo(modulo)
                    .descripcion(descripcion)
                    .ipAddress(ip)
                    .userAgent(userAgent)
                    .build();
            repo.save(logEntry);
        } catch (Exception e) {
            // No propagar excepción: la auditoría no debe romper el flujo principal
            log.error("Error al registrar auditoría: {}", e.getMessage());
        }
    }
}