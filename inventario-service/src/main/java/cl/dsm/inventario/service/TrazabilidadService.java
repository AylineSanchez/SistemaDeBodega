package cl.dsm.inventario.service;

import cl.dsm.inventario.dto.*;
import cl.dsm.inventario.entity.*;
import cl.dsm.inventario.exception.BusinessException;
import cl.dsm.inventario.exception.ResourceNotFoundException;
import cl.dsm.inventario.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;

/**
 * Servicio del Módulo de Trazabilidad Sanitaria NT 226/247.
 *
 * Cubre RF-067 a RF-074.
 *
 * ⚡ NUEVO: envía notificaciones por email al crear/levantar bloqueos sanitarios.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrazabilidadService {

    private final BloqueoSanitarioRepository bloqueoRepo;
    private final BloqueoBodegaRepository bloqueoBodegaRepo;
    private final TrazabilidadDMRepository trazabilidadRepo;
    private final LoteRepository loteRepo;
    private final ArticuloRepository articuloRepo;
    private final StockLoteRepository stockLoteRepo;
    private final StockPorBodegaRepository stockRepo;
    private final KardexService kardexService;
    private final NotificacionService notificacionService;  // ⚡ NUEVO

    // ============================================================
    // BLOQUEOS SANITARIOS
    // ============================================================

    /**
     * Lista los bloqueos con filtros.
     */
    @Transactional(readOnly = true)
    public PageResponse<BloqueoResponse> listarBloqueos(
            String q, String estado, String tipoAlerta, int page, int size) {

        Pageable pageable = PageRequest.of(page, size);
        Page<BloqueoSanitario> pagina = bloqueoRepo.buscar(q, estado, tipoAlerta, pageable);

        List<BloqueoResponse> content = pagina.getContent().stream()
                .map(b -> BloqueoResponse.from(b, bloqueoBodegaRepo.findByBloqueoId(b.getId())))
                .toList();

        return PageResponse.<BloqueoResponse>builder()
                .content(content)
                .page(pagina.getNumber())
                .size(pagina.getSize())
                .totalElements(pagina.getTotalElements())
                .totalPages(pagina.getTotalPages())
                .first(pagina.isFirst())
                .last(pagina.isLast())
                .build();
    }

    @Transactional(readOnly = true)
    public BloqueoResponse obtenerBloqueoPorId(Long id) {
        BloqueoSanitario b = bloqueoRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Bloqueo no encontrado: " + id));

        return BloqueoResponse.from(b, bloqueoBodegaRepo.findByBloqueoId(id));
    }

    /**
     * ⚡ CREAR BLOQUEO SANITARIO (RF-069)
     *
     * 1. Valida que el lote no esté ya bloqueado
     * 2. Bloquea el lote (flag bloqueado = true)
     * 3. Registra el bloqueo en cada bodega donde tenga stock
     * 4. Registra movimiento de bloqueo en Kardex
     * 5. ⚡ Envía notificación por email a JEFATURA + OPERATIVO + AUDITOR
     */
    @Transactional
    public BloqueoResponse crearBloqueo(BloqueoRequest req, String rutUsuario, String nombreUsuario) {

        // 1. Validar lote
        Lote lote = loteRepo.findById(req.getLoteId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Lote no encontrado: " + req.getLoteId()));

        // 2. Validar que no esté ya bloqueado
        if (bloqueoRepo.tieneBloqueoActivo(lote.getId())) {
            throw new BusinessException(
                    "El lote " + lote.getNumeroLote() + " ya tiene un bloqueo activo");
        }

        Articulo articulo = lote.getArticulo();

        // 3. Crear número correlativo
        String numeroBloqueo = generarNumeroBloqueo();

        // 4. Crear cabecera
        BloqueoSanitario bloqueo = BloqueoSanitario.builder()
                .numeroBloqueo(numeroBloqueo)
                .lote(lote)
                .articulo(articulo)
                .numeroLote(lote.getNumeroLote())
                .nombreProducto(articulo.getNombreTecnico())
                .sku(articulo.getSku())
                .fechaVencimiento(lote.getFechaVencimiento())
                .registroIsp(lote.getRegistroIsp())
                .tipoAlerta(req.getTipoAlerta())
                .numeroOficioIsp(req.getNumeroOficioIsp())
                .fechaAlerta(req.getFechaAlerta())
                .descripcionAlerta(req.getDescripcionAlerta())
                .justificacion(req.getJustificacion())
                .bloqueadoPorRut(rutUsuario)
                .bloqueadoPorNombre(nombreUsuario)
                .estado("ACTIVO")
                .build();

        bloqueo = bloqueoRepo.save(bloqueo);

        // 5. Bloquear el lote (flag)
        lote.setBloqueado(true);
        lote.setMotivoBloqueo(req.getJustificacion());
        lote.setFechaBloqueo(LocalDateTime.now());
        lote.setBloqueadoPor(rutUsuario);
        loteRepo.save(lote);

        // 6. Buscar todos los stocks de ese lote y bloquearlos
        List<StockLote> stockLotes = stockLoteRepo.findByLoteId(lote.getId());
        List<BloqueoBodega> bloqueosBodega = new ArrayList<>();
        int stockTotalAfectado = 0;

        for (StockLote sl : stockLotes) {
            if (sl.getCantidad() > 0) {
                BloqueoBodega bb = BloqueoBodega.builder()
                        .bloqueo(bloqueo)
                        .bodega(sl.getStock().getBodega())
                        .stock(sl.getStock())
                        .stockLote(sl)
                        .cantidadBloqueada(sl.getCantidad())
                        .estado("BLOQUEADO")
                        .build();

                bloqueosBodega.add(bb);
                stockTotalAfectado += sl.getCantidad();
            }
        }

        bloqueoBodegaRepo.saveAll(bloqueosBodega);

        // 7. Actualizar contadores en el bloqueo
        bloqueo.setStockAfectado(stockTotalAfectado);
        bloqueo.setBodegasAfectadas(bloqueosBodega.size());
        bloqueo = bloqueoRepo.save(bloqueo);

        // 8. Registrar en Kardex (BLOQUEO_ISP)
        for (BloqueoBodega bb : bloqueosBodega) {
            kardexService.registrarMovimiento(
                    articulo,
                    bb.getBodega(),
                    lote,
                    "BLOQUEO_ISP",
                    "BLOQUEO",
                    numeroBloqueo,
                    req.getNumeroOficioIsp(),
                    0,
                    bb.getCantidadBloqueada(),
                    bb.getCantidadBloqueada(),
                    null,
                    null,
                    "Bloqueo sanitario: " + req.getTipoAlerta() +
                        " - " + req.getNumeroOficioIsp(),
                    rutUsuario,
                    nombreUsuario,
                    null
            );
        }

        log.warn("BLOQUEO SANITARIO creado: {} - Lote {} ({} unidades afectadas en {} bodegas)",
                numeroBloqueo, lote.getNumeroLote(), stockTotalAfectado, bloqueosBodega.size());

        // ⚡ 9. Enviar notificación por email (no bloquear si falla)
        try {
            notificacionService.notificarBloqueoSanitario(bloqueo);
        } catch (Exception e) {
            log.error("Error al enviar notificación de bloqueo: {}", e.getMessage(), e);
            // No propagamos el error: el bloqueo ya se creó correctamente
        }

        return BloqueoResponse.from(bloqueo, bloqueosBodega);
    }

    /**
     * ⚡ LEVANTAR BLOQUEO (RF-069)
     *
     * Solo lo puede hacer el Auditor NT 226.
     * También envía notificación por email avisando del levantamiento.
     */
    @Transactional
    public BloqueoResponse levantarBloqueo(Long id, String motivo, String rutUsuario, String nombreUsuario) {

        BloqueoSanitario bloqueo = bloqueoRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Bloqueo no encontrado: " + id));

        if (!"ACTIVO".equals(bloqueo.getEstado())) {
            throw new BusinessException(
                    "Solo se pueden levantar bloqueos en estado ACTIVO. " +
                    "Estado actual: " + bloqueo.getEstado());
        }

        // 1. Actualizar el bloqueo
        bloqueo.setEstado("LEVANTADO");
        bloqueo.setLevantadoPorRut(rutUsuario);
        bloqueo.setLevantadoPorNombre(nombreUsuario);
        bloqueo.setFechaLevantamiento(LocalDateTime.now());
        bloqueo.setMotivoLevantamiento(motivo);
        bloqueo = bloqueoRepo.save(bloqueo);

        // 2. Desbloquear el lote
        Lote lote = bloqueo.getLote();
        lote.setBloqueado(false);
        lote.setMotivoBloqueo(null);
        lote.setFechaBloqueo(null);
        lote.setBloqueadoPor(null);
        loteRepo.save(lote);

        // 3. Actualizar BloqueoBodega
        List<BloqueoBodega> bloqueosBodega = bloqueoBodegaRepo.findByBloqueoId(id);
        for (BloqueoBodega bb : bloqueosBodega) {
            bb.setEstado("DESBLOQUEADO");
        }
        bloqueoBodegaRepo.saveAll(bloqueosBodega);

        // 4. Registrar en Kardex
        for (BloqueoBodega bb : bloqueosBodega) {
            kardexService.registrarMovimiento(
                    bloqueo.getArticulo(),
                    bb.getBodega(),
                    lote,
                    "DESBLOQUEO_ISP",
                    "BLOQUEO",
                    bloqueo.getNumeroBloqueo(),
                    null,
                    0,
                    bb.getCantidadBloqueada(),
                    bb.getCantidadBloqueada(),
                    null,
                    null,
                    "Desbloqueo: " + motivo,
                    rutUsuario,
                    nombreUsuario,
                    null
            );
        }

        log.info("BLOQUEO {} LEVANTADO por {} - Motivo: {}",
                bloqueo.getNumeroBloqueo(), rutUsuario, motivo);

        // ⚡ 5. Notificar por email que se levantó el bloqueo
        try {
            notificacionService.notificarBloqueoSanitario(bloqueo);
        } catch (Exception e) {
            log.error("Error al enviar notificación de levantamiento: {}", e.getMessage(), e);
        }

        return BloqueoResponse.from(bloqueo, bloqueosBodega);
    }

    // ============================================================
    // TRAZABILIDAD DM
    // ============================================================

    @Transactional(readOnly = true)
    public PageResponse<TrazabilidadResponse> listarTrazabilidad(
            String q, String estado, String estadoDm, Long articuloId,
            int page, int size) {

        Pageable pageable = PageRequest.of(page, size);
        Page<TrazabilidadDM> pagina = trazabilidadRepo.buscar(
                q, estado, estadoDm, articuloId, pageable);

        List<TrazabilidadResponse> content = pagina.getContent().stream()
                .map(TrazabilidadResponse::from)
                .toList();

        return PageResponse.<TrazabilidadResponse>builder()
                .content(content)
                .page(pagina.getNumber())
                .size(pagina.getSize())
                .totalElements(pagina.getTotalElements())
                .totalPages(pagina.getTotalPages())
                .first(pagina.isFirst())
                .last(pagina.isLast())
                .build();
    }

    @Transactional(readOnly = true)
    public TrazabilidadResponse obtenerTrazabilidadPorId(Long id) {
        TrazabilidadDM t = trazabilidadRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Registro no encontrado: " + id));
        return TrazabilidadResponse.from(t);
    }

    /**
     * Crear registro de trazabilidad DM.
     */
    @Transactional
    public TrazabilidadResponse crearTrazabilidad(
            TrazabilidadRequest req, String rutUsuario) {

        Articulo articulo = articuloRepo.findById(req.getArticuloId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Artículo no encontrado: " + req.getArticuloId()));

        Lote lote = null;
        if (req.getLoteId() != null) {
            lote = loteRepo.findById(req.getLoteId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Lote no encontrado: " + req.getLoteId()));
        }

        TrazabilidadDM t = TrazabilidadDM.builder()
                .articulo(articulo)
                .lote(lote)
                .nombreProducto(req.getNombreProducto())
                .rutProveedor(req.getRutProveedor())
                .nombreProveedor(req.getNombreProveedor())
                .numeroFactura(req.getNumeroFactura())
                .numeroGuia(req.getNumeroGuia())
                .modelo(req.getModelo())
                .numeroLote(req.getNumeroLote())
                .numeroSerie(req.getNumeroSerie())
                .fechaVencimiento(req.getFechaVencimiento())
                .estadoDm(req.getEstadoDm() != null ? req.getEstadoDm() : "NUEVO")
                .numeroRegistroSanitario(req.getNumeroRegistroSanitario())
                .formaAdquisicion(req.getFormaAdquisicion())
                .fechaRecepcionConforme(req.getFechaRecepcionConforme())
                .numeroInventario(req.getNumeroInventario())
                .pacienteRun(req.getPacienteRun())
                .pacienteNombre(req.getPacienteNombre())
                .fechaImplantacion(req.getFechaImplantacion())
                .profesionalImplantador(req.getProfesionalImplantador())
                .procedimiento(req.getProcedimiento())
                .estado(req.getEstado() != null ? req.getEstado() : "EN_BODEGA")
                .observaciones(req.getObservaciones())
                .creadoPor(rutUsuario)
                .actualizadoPor(rutUsuario)
                .build();

        t = trazabilidadRepo.save(t);

        log.info("Trazabilidad DM creada: {} - Lote {}",
                articulo.getSku(), req.getNumeroLote());

        return TrazabilidadResponse.from(t);
    }

    /**
     * Exportar trazabilidad para fiscalización (RF-073).
     */
    @Transactional(readOnly = true)
    public List<TrazabilidadResponse> exportarTrazabilidad(String filtro) {
        Pageable pageable = PageRequest.of(0, 10000);
        Page<TrazabilidadDM> pagina = trazabilidadRepo.buscar(filtro, null, null, null, pageable);

        return pagina.getContent().stream()
                .map(TrazabilidadResponse::from)
                .toList();
    }

    // ============================================================
    // HELPERS
    // ============================================================

    private String generarNumeroBloqueo() {
        String year = String.valueOf(Year.now().getValue());
        Integer max = bloqueoRepo.findMaxNumeroBloqueoByYear(year);
        int siguiente = (max != null ? max : 0) + 1;
        return String.format("BLQ-%s-%04d", year, siguiente);
    }
}