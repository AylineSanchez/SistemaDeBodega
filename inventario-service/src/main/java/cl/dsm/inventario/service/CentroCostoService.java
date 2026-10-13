package cl.dsm.inventario.service;

import cl.dsm.inventario.dto.CentroCostoDTO;
import cl.dsm.inventario.dto.CentroCostoRequest;
import cl.dsm.inventario.entity.CentroCosto;
import cl.dsm.inventario.exception.BusinessException;
import cl.dsm.inventario.exception.ResourceNotFoundException;
import cl.dsm.inventario.repository.CentroCostoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CentroCostoService {

    private final CentroCostoRepository centroRepo;

    // ============================================================
    // LISTAR TODOS (activos e inactivos)
    // ============================================================
    @Transactional(readOnly = true)
    public List<CentroCostoDTO> listarTodos() {
        return centroRepo.findAllByOrderByCodigoAsc().stream()
                .map(CentroCostoDTO::from)
                .toList();
    }

    // ============================================================
    // LISTAR SOLO ACTIVOS
    // ============================================================
    @Transactional(readOnly = true)
    public List<CentroCostoDTO> listarActivos() {
        return centroRepo.findByActivoTrueOrderByNombreAsc().stream()
                .map(CentroCostoDTO::from)
                .toList();
    }

    // ============================================================
    // OBTENER POR ID
    // ============================================================
    @Transactional(readOnly = true)
    public CentroCostoDTO obtenerPorId(Integer id) {
        CentroCosto c = centroRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Centro de costo no encontrado: " + id));
        return CentroCostoDTO.from(c);
    }

    // ============================================================
    // CREAR
    // ============================================================
    @Transactional
    public CentroCostoDTO crear(CentroCostoRequest req, String rutUsuario) {
        // Validar código único
        if (centroRepo.existsByCodigo(req.getCodigo().toUpperCase())) {
            throw new BusinessException("Ya existe un centro de costo con el código: " + req.getCodigo());
        }

        CentroCosto centro = CentroCosto.builder()
                .codigo(req.getCodigo().trim().toUpperCase())
                .nombre(req.getNombre().trim())
                .descripcion(req.getDescripcion())
                .tipo(req.getTipo() != null && !req.getTipo().isBlank() ? req.getTipo() : "OTRO")
                .responsableRut(req.getResponsableRut())
                .responsableNombre(req.getResponsableNombre())
                .activo(true)
                .build();

        centro = centroRepo.save(centro);
        log.info("Centro de costo creado: {} ({}) por {}", centro.getCodigo(), centro.getNombre(), rutUsuario);

        return CentroCostoDTO.from(centro);
    }

    // ============================================================
    // ACTUALIZAR
    // ============================================================
    @Transactional
    public CentroCostoDTO actualizar(Integer id, CentroCostoRequest req, String rutUsuario) {
        CentroCosto centro = centroRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Centro de costo no encontrado: " + id));

        // Validar código único (si cambió)
        String nuevoCodigo = req.getCodigo().trim().toUpperCase();
        if (!nuevoCodigo.equals(centro.getCodigo())) {
            if (centroRepo.existsByCodigo(nuevoCodigo)) {
                throw new BusinessException("Ya existe otro centro de costo con el código: " + nuevoCodigo);
            }
            centro.setCodigo(nuevoCodigo);
        }

        centro.setNombre(req.getNombre().trim());
        centro.setDescripcion(req.getDescripcion());
        centro.setTipo(req.getTipo() != null && !req.getTipo().isBlank() ? req.getTipo() : "OTRO");
        centro.setResponsableRut(req.getResponsableRut());
        centro.setResponsableNombre(req.getResponsableNombre());

        centro = centroRepo.save(centro);
        log.info("Centro de costo actualizado: {} por {}", centro.getCodigo(), rutUsuario);

        return CentroCostoDTO.from(centro);
    }

    // ============================================================
    // DESACTIVAR (baja lógica)
    // ============================================================
    @Transactional
    public void desactivar(Integer id, String rutUsuario) {
        CentroCosto centro = centroRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Centro de costo no encontrado: " + id));

        centro.setActivo(false);
        centroRepo.save(centro);

        log.info("Centro de costo desactivado: {} por {}", centro.getCodigo(), rutUsuario);
    }

    // ============================================================
    // REACTIVAR
    // ============================================================
    @Transactional
    public void reactivar(Integer id, String rutUsuario) {
        CentroCosto centro = centroRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Centro de costo no encontrado: " + id));

        centro.setActivo(true);
        centroRepo.save(centro);

        log.info("Centro de costo reactivado: {} por {}", centro.getCodigo(), rutUsuario);
    }
}