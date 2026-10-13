package cl.dsm.inventario.service;

import cl.dsm.inventario.dto.ConfiguracionDTO;
import cl.dsm.inventario.dto.ConfiguracionUpdateRequest;
import cl.dsm.inventario.entity.Configuracion;
import cl.dsm.inventario.exception.BusinessException;
import cl.dsm.inventario.exception.ResourceNotFoundException;
import cl.dsm.inventario.repository.ConfiguracionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConfiguracionService {

    private final ConfiguracionRepository configRepo;

    // ============================================================
    // LISTAR TODAS
    // ============================================================
    @Transactional(readOnly = true)
    public List<ConfiguracionDTO> listarTodas() {
        return configRepo.findAllByOrderByClaveAsc().stream()
                .map(ConfiguracionDTO::from)
                .toList();
    }

    // ============================================================
    // OBTENER UNA
    // ============================================================
    @Transactional(readOnly = true)
    public ConfiguracionDTO obtener(String clave) {
        Configuracion c = configRepo.findByClave(clave)
                .orElseThrow(() -> new ResourceNotFoundException("Configuración no encontrada: " + clave));
        return ConfiguracionDTO.from(c);
    }

    // ============================================================
    // ACTUALIZAR UNA
    // ============================================================
    @Transactional
    public ConfiguracionDTO actualizar(ConfiguracionUpdateRequest req, String rutUsuario) {
        Configuracion c = configRepo.findByClave(req.getClave())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Configuración no encontrada: " + req.getClave()));

        if ("INT".equals(c.getTipo())) {
            try {
                Integer.parseInt(req.getValor());
            } catch (NumberFormatException e) {
                throw new BusinessException("El valor debe ser un número entero");
            }
        }
        if ("BOOLEAN".equals(c.getTipo())) {
            if (!"true".equalsIgnoreCase(req.getValor())
                    && !"false".equalsIgnoreCase(req.getValor())) {
                throw new BusinessException("El valor debe ser 'true' o 'false'");
            }
        }

        c.setValor(req.getValor());
        c.setActualizadoPor(rutUsuario);
        c = configRepo.save(c);

        log.info("Configuración '{}' actualizada a '{}' por {}",
                c.getClave(), c.getValor(), rutUsuario);

        return ConfiguracionDTO.from(c);
    }

    // ============================================================
    // HELPERS
    // ============================================================
    public int getDiasAlertaCritico() {
        return configRepo.getInt("dias_alerta_vencimiento_critico", 120);
    }

    public int getDiasAlertaProximo() {
        return configRepo.getInt("dias_alerta_vencimiento_proximo", 180);
    }

    public int getDiasAlertaVigilar() {
        return configRepo.getInt("dias_alerta_vencimiento_vigilar", 365);
    }

    public int getTimeoutSesionMinutos() {
        return configRepo.getInt("timeout_sesion_minutos", 15);
    }

    public boolean isNotifCriticoActivo() {
        return configRepo.getBoolean("notif_critico_activo", true);
    }

    public boolean isNotifBloqueoActivo() {
        return configRepo.getBoolean("notif_bloqueo_activo", true);
    }

    // ⚡ NUEVO
    public boolean isNotifVencimientoActivo() {
        return configRepo.getBoolean("notif_vencimiento_activo", true);
    }
}