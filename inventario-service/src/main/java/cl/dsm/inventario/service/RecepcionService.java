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
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Servicio del Módulo de Recepción.
 *
 * Cubre RF-020 a RF-028.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecepcionService {

    private final RecepcionRepository recepcionRepo;
    private final RecepcionDetalleRepository recepcionDetalleRepo;
    private final ArticuloRepository articuloRepo;
    private final BodegaRepository bodegaRepo;
    private final LoteRepository loteRepo;
    private final StockPorBodegaRepository stockRepo;
    private final StockLoteRepository stockLoteRepo;
    private final KardexService kardexService;

    // ============================================================
    // LISTAR CON FILTROS
    // ============================================================
    @Transactional(readOnly = true)
    public PageResponse<RecepcionResumenDTO> listar(
            String q, String estado, Integer bodegaId,
            LocalDate fechaDesde, LocalDate fechaHasta,
            int page, int size) {

        Pageable pageable = PageRequest.of(page, size,
                Sort.by("fechaRecepcion").descending().and(Sort.by("id").descending()));

        Page<Recepcion> pagina = recepcionRepo.buscar(
                q, estado, bodegaId, fechaDesde, fechaHasta, pageable);

        List<RecepcionResumenDTO> content = pagina.getContent().stream()
                .map(this::toResumen)
                .toList();

        return PageResponse.<RecepcionResumenDTO>builder()
                .content(content)
                .page(pagina.getNumber())
                .size(pagina.getSize())
                .totalElements(pagina.getTotalElements())
                .totalPages(pagina.getTotalPages())
                .first(pagina.isFirst())
                .last(pagina.isLast())
                .build();
    }

    // ============================================================
    // OBTENER POR ID
    // ⚡ Si la recepción está CONFIRMADA, carga los QRs desde stock_por_bodega
    // ============================================================
    @Transactional(readOnly = true)
    public RecepcionResponse obtenerPorId(Long id) {
        Recepcion r = recepcionRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Recepción no encontrada: " + id));

        List<RecepcionDetalle> detalles = recepcionDetalleRepo.findByRecepcionId(id);
        RecepcionResponse response = RecepcionResponse.from(r, detalles);

        if ("CONFIRMADA".equals(r.getEstado())) {
            for (RecepcionDetalleResponse d : response.getDetalles()) {
                try {
                    for (RecepcionDetalle detalle : detalles) {
                        if (!detalle.getArticulo().getId().equals(d.getArticuloId())) continue;

                        Integer bodegaId = (detalle.getBodega() != null)
                                ? detalle.getBodega().getId()
                                : r.getBodega().getId();

                        StockPorBodega stock = stockRepo
                                .findByArticuloIdAndBodegaId(d.getArticuloId(), bodegaId)
                                .orElse(null);

                        if (stock != null && stock.getCodigoQr() != null) {
                            d.setCodigoQr(stock.getCodigoQr());
                            d.setBodegaCodigo(stock.getBodega().getCodigo());
                            d.setBodegaNombre(stock.getBodega().getNombre());
                            break;
                        }
                    }
                } catch (Exception e) {
                    log.warn("No se pudo cargar QR para detalle {}: {}", d.getId(), e.getMessage());
                }
            }
        }

        return response;
    }

    // ============================================================
    // CREAR RECEPCIÓN
    // ============================================================
    @Transactional
    public RecepcionResponse crear(RecepcionRequest req, String rutUsuario, String nombreUsuario) {

        log.info("Creando recepción: {} {} - Proveedor: {}",
                req.getTipoDocumento(), req.getNumeroDocumento(), req.getNombreProveedor());

        Bodega bodega = bodegaRepo.findById(req.getBodegaId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Bodega no encontrada: " + req.getBodegaId()));

        if (recepcionRepo.existsByTipoDocumentoAndNumeroDocumento(
                req.getTipoDocumento(), req.getNumeroDocumento())) {
            throw new BusinessException(
                    "Ya existe una recepción para " + req.getTipoDocumento() +
                    " N° " + req.getNumeroDocumento());
        }

        String numeroRecepcion = generarNumeroRecepcion();

        Recepcion recepcion = Recepcion.builder()
                .numeroRecepcion(numeroRecepcion)
                .tipoDocumento(req.getTipoDocumento())
                .numeroDocumento(req.getNumeroDocumento())
                .numeroOrdenCompra(req.getNumeroOrdenCompra())
                .numeroGuiaDespacho(req.getNumeroGuiaDespacho())
                .rutProveedor(req.getRutProveedor())
                .nombreProveedor(req.getNombreProveedor())
                .telefonoProveedor(req.getTelefonoProveedor())
                .direccionProveedor(req.getDireccionProveedor())
                .tipoProveedor(req.getTipoProveedor())
                .fechaEmision(req.getFechaEmision())
                .fechaRecepcion(req.getFechaRecepcion())
                .bodega(bodega)
                .receptorRut(req.getReceptorRut() != null ? req.getReceptorRut() : rutUsuario)
                .receptorNombre(req.getReceptorNombre() != null ? req.getReceptorNombre() : nombreUsuario)
                .receptorFecha(LocalDateTime.now())
                .montoNeto(req.getMontoNeto())
                .montoIva(req.getMontoIva())
                .montoTotal(req.getMontoTotal())
                .estado("BORRADOR")
                .observaciones(req.getObservaciones())
                .creadoPor(rutUsuario)
                .actualizadoPor(rutUsuario)
                .build();

        recepcion = recepcionRepo.save(recepcion);

        List<RecepcionDetalle> detalles = new ArrayList<>();

        for (RecepcionDetalleRequest detReq : req.getDetalles()) {
            RecepcionDetalle detalle = crearDetalle(recepcion, detReq);
            detalles.add(detalle);
        }

        recepcionDetalleRepo.saveAll(detalles);

        log.info("Recepción creada: {} con {} líneas", numeroRecepcion, detalles.size());

        return RecepcionResponse.from(recepcion, detalles);
    }

    // ============================================================
    // CONFIRMAR RECEPCIÓN
    // ⚡ Al confirmar, se generan los QRs de cada stock
    // ============================================================
    @Transactional
    public RecepcionResponse confirmar(Long id, String rutUsuario, String nombreUsuario, String ipAddress) {

        final Recepcion recepcion = recepcionRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Recepción no encontrada: " + id));

        if (!"BORRADOR".equals(recepcion.getEstado())) {
            throw new BusinessException(
                    "Solo se pueden confirmar recepciones en estado BORRADOR. " +
                    "Estado actual: " + recepcion.getEstado());
        }

        final List<RecepcionDetalle> detalles = recepcionDetalleRepo.findByRecepcionId(id);

        if (detalles.isEmpty()) {
            throw new BusinessException("La recepción no tiene productos");
        }

        // ⚡ Map: detalleId → codigoQr
        final Map<Long, String> qrsPorDetalleId = new HashMap<>();

        for (RecepcionDetalle detalle : detalles) {
            String codigoQr = procesarDetalle(recepcion, detalle, rutUsuario, nombreUsuario, ipAddress);
            qrsPorDetalleId.put(detalle.getId(), codigoQr);

            log.info("QR generado para detalleId {} (artículo {}): {}",
                    detalle.getId(), detalle.getArticulo().getId(), codigoQr);
        }

        recepcion.setEstado("CONFIRMADA");
        recepcion.setActualizadoPor(rutUsuario);
        recepcionRepo.save(recepcion);

        log.info("Recepción CONFIRMADA: {} - {} QRs generados",
                recepcion.getNumeroRecepcion(), qrsPorDetalleId.size());

        // ⚡ Construir la respuesta con los QRs
        final RecepcionResponse response = RecepcionResponse.from(recepcion, detalles);

        for (RecepcionDetalleResponse d : response.getDetalles()) {
            try {
                String qr = null;
                String bodegaCodigo = null;
                String bodegaNombre = null;

                for (RecepcionDetalle detalle : detalles) {
                    if (!detalle.getArticulo().getId().equals(d.getArticuloId())) continue;

                    Integer bodegaId = (detalle.getBodega() != null)
                            ? detalle.getBodega().getId()
                            : recepcion.getBodega().getId();

                    // 1. Buscar en stock_por_bodega por articuloId + bodegaId
                    StockPorBodega stock = stockRepo
                            .findByArticuloIdAndBodegaId(d.getArticuloId(), bodegaId)
                            .orElse(null);

                    if (stock != null && stock.getCodigoQr() != null) {
                        qr = stock.getCodigoQr();
                        bodegaCodigo = stock.getBodega().getCodigo();
                        bodegaNombre = stock.getBodega().getNombre();
                        break;
                    }

                    // 2. Fallback: usar el QR del mapa por detalleId
                    if (qrsPorDetalleId.containsKey(detalle.getId())) {
                        qr = qrsPorDetalleId.get(detalle.getId());
                        Bodega b = detalle.getBodega() != null ? detalle.getBodega() : recepcion.getBodega();
                        bodegaCodigo = b.getCodigo();
                        bodegaNombre = b.getNombre();
                        break;
                    }
                }

                if (qr != null) {
                    d.setCodigoQr(qr);
                    d.setBodegaCodigo(bodegaCodigo != null ? bodegaCodigo : recepcion.getBodega().getCodigo());
                    d.setBodegaNombre(bodegaNombre != null ? bodegaNombre : recepcion.getBodega().getNombre());
                    log.info("✅ QR asignado a artículo {} (SKU {}): {}", d.getArticuloId(), d.getSku(), qr);
                } else {
                    log.error("❌ NO se encontró QR para artículo {} (SKU {})", d.getArticuloId(), d.getSku());
                }

            } catch (Exception e) {
                log.error("Error asignando QR a artículo {}: {}", d.getArticuloId(), e.getMessage(), e);
            }
        }

        return response;
    }

    // ============================================================
    // ANULAR RECEPCIÓN
    // ============================================================
    @Transactional
    public void anular(Long id, String motivo, String rutUsuario) {
        Recepcion recepcion = recepcionRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Recepción no encontrada: " + id));

        if ("CONFIRMADA".equals(recepcion.getEstado())) {
            throw new BusinessException(
                    "No se puede anular una recepción ya confirmada. " +
                    "Debe hacer un ajuste de inventario.");
        }

        recepcion.setEstado("ANULADA");
        recepcion.setObservaciones((recepcion.getObservaciones() != null ? recepcion.getObservaciones() + "\n" : "")
                + "ANULADA: " + motivo);
        recepcion.setActualizadoPor(rutUsuario);
        recepcionRepo.save(recepcion);

        log.info("Recepción ANULADA: {}", recepcion.getNumeroRecepcion());
    }

    // ============================================================
    // HELPERS PRIVADOS
    // ============================================================

    private String generarNumeroRecepcion() {
        String year = String.valueOf(Year.now().getValue());
        Integer maxNumero = recepcionRepo.findMaxNumeroRecepcionByYear(year);
        int siguiente = (maxNumero != null ? maxNumero : 0) + 1;
        return String.format("REC-%s-%04d", year, siguiente);
    }

    private RecepcionDetalle crearDetalle(Recepcion recepcion, RecepcionDetalleRequest req) {

        Articulo articulo = articuloRepo.findById(req.getArticuloId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Artículo no encontrado: " + req.getArticuloId()));

        Bodega bodegaDetalle = recepcion.getBodega();
        if (req.getBodegaId() != null) {
            bodegaDetalle = bodegaRepo.findById(req.getBodegaId())
                    .orElse(recepcion.getBodega());
        }

        if (req.getFechaVencimiento() != null &&
                req.getFechaVencimiento().isBefore(LocalDate.now())) {
            throw new BusinessException(
                    "El artículo " + articulo.getSku() +
                    " tiene fecha de vencimiento pasada: " + req.getFechaVencimiento());
        }

        if (Boolean.TRUE.equals(articulo.getRequiereLote())) {
            if (req.getNumeroLote() == null || req.getNumeroLote().isBlank()) {
                throw new BusinessException(
                        "El artículo " + articulo.getSku() + " requiere N° de lote");
            }
        }

        if (Boolean.TRUE.equals(articulo.getRequiereVencimiento())) {
            if (req.getFechaVencimiento() == null) {
                throw new BusinessException(
                        "El artículo " + articulo.getSku() + " requiere fecha de vencimiento");
            }
        }

        BigDecimal montoTotal = req.getMontoTotal();
        if (montoTotal == null || montoTotal.compareTo(BigDecimal.ZERO) == 0) {
            if (req.getValorUnitario() != null && req.getCantidadRecibida() != null) {
                montoTotal = req.getValorUnitario()
                        .multiply(BigDecimal.valueOf(req.getCantidadRecibida()));
            } else {
                montoTotal = BigDecimal.ZERO;
            }
        }

        return RecepcionDetalle.builder()
                .recepcion(recepcion)
                .articulo(articulo)
                .bodega(bodegaDetalle)
                .sku(articulo.getSku())
                .nombreProducto(articulo.getNombreTecnico())
                .cantidadRecibida(req.getCantidadRecibida())
                .cantidadPendiente(req.getCantidadPendiente() != null ? req.getCantidadPendiente() : 0)
                .valorUnitario(req.getValorUnitario() != null ? req.getValorUnitario() : BigDecimal.ZERO)
                .montoTotal(montoTotal)
                .numeroLote(req.getNumeroLote())
                .fechaVencimiento(req.getFechaVencimiento())
                .registroIsp(req.getRegistroIsp() != null ? req.getRegistroIsp() : articulo.getRegistroIsp())
                .ubicacionFisica(req.getUbicacionFisica())
                .estante(req.getEstante())
                .nivel(req.getNivel())
                .estado("RECIBIDA")
                .observaciones(req.getObservaciones())
                .build();
    }

    /**
     * ⚡ Procesa un detalle al confirmar.
     *
     * REGLA IMPORTANTE:
     * - Si el StockPorBodega NO existe → se crea con QR.
     * - Si el StockPorBodega EXISTE pero NO tiene codigoQr → se le genera uno.
     * - Si ya tiene codigoQr → se reutiliza.
     *
     * @return el código QR del stock procesado
     */
    private String procesarDetalle(final Recepcion recepcion, RecepcionDetalle detalle,
                                   String rutUsuario, String nombreUsuario, String ipAddress) {

        final Articulo articulo = detalle.getArticulo();
        final Bodega bodega = (detalle.getBodega() != null)
                ? detalle.getBodega()
                : recepcion.getBodega();
        final String rutUsuarioFinal = rutUsuario;
        final String sku = articulo.getSku();
        final String codigoBodega = bodega.getCodigo();

        // ⚡ Buscar stock existente
        StockPorBodega stock = stockRepo
                .findByArticuloIdAndBodegaId(articulo.getId(), bodega.getId())
                .orElse(null);

        // Si NO existe → crear
        if (stock == null) {
            stock = StockPorBodega.builder()
                    .articulo(articulo)
                    .bodega(bodega)
                    .stockActual(0)
                    .ubicacionFisica(detalle.getUbicacionFisica())
                    .estante(detalle.getEstante())
                    .nivel(detalle.getNivel())
                    .creadoPor(rutUsuarioFinal)
                    .actualizadoPor(rutUsuarioFinal)
                    .build();

            stock.setCodigoQr(generarCodigoQrUnico(sku, codigoBodega));
            stock = stockRepo.save(stock);

            log.info("Stock CREADO para artículo {} en bodega {}: id={}, QR={}",
                    sku, codigoBodega, stock.getId(), stock.getCodigoQr());
        }

        // ⚡ Si EXISTE pero NO tiene QR → generarlo y guardarlo
        if (stock.getCodigoQr() == null || stock.getCodigoQr().isBlank()) {
            String qrGenerado = generarCodigoQrUnico(sku, codigoBodega);
            stock.setCodigoQr(qrGenerado);
            stock = stockRepo.save(stock);

            log.warn("Stock {} (artículo {}) NO tenía QR. Generado y guardado: {}",
                    stock.getId(), sku, qrGenerado);
        }

        // ⚡ Verificar que el QR esté persistido (log de seguridad)
        if (stock.getCodigoQr() == null || stock.getCodigoQr().isBlank()) {
            throw new BusinessException(
                "No se pudo generar código QR para el artículo " + sku + " en bodega " + codigoBodega);
        }

        // Actualizar stock
        Integer stockAnterior = stock.getStockActual();
        Integer stockNuevo = stockAnterior + detalle.getCantidadRecibida();

        stock.setStockActual(stockNuevo);
        stock.setActualizadoPor(rutUsuario);
        if (detalle.getUbicacionFisica() != null) stock.setUbicacionFisica(detalle.getUbicacionFisica());
        if (detalle.getEstante() != null) stock.setEstante(detalle.getEstante());
        if (detalle.getNivel() != null) stock.setNivel(detalle.getNivel());
        stock = stockRepo.save(stock);

        // Procesar lote
        Lote lote = null;
        if (detalle.getNumeroLote() != null && !detalle.getNumeroLote().isBlank()) {

            final Long articuloId = articulo.getId();
            final String numeroLote = detalle.getNumeroLote();
            final LocalDate fechaVencimiento = detalle.getFechaVencimiento();
            final String registroIsp = detalle.getRegistroIsp();
            final String fabricante = articulo.getFabricante();
            final String paisOrigen = articulo.getPaisOrigen();

            lote = loteRepo.findByArticuloIdAndNumeroLote(articuloId, numeroLote)
                    .orElseGet(() -> {
                        Lote nuevo = Lote.builder()
                                .articulo(articulo)
                                .numeroLote(numeroLote)
                                .fechaVencimiento(fechaVencimiento)
                                .registroIsp(registroIsp)
                                .fabricante(fabricante)
                                .paisOrigen(paisOrigen)
                                .build();
                        return loteRepo.save(nuevo);
                    });

            final StockPorBodega stockFinal = stock;
            final Lote loteFinal = lote;
            final Long stockId = stock.getId();
            final Long loteId = lote.getId();
            final Integer cantidadRecibida = detalle.getCantidadRecibida();

            StockLote stockLote = stockLoteRepo
                    .findByStockIdAndLoteId(stockId, loteId)
                    .orElseGet(() -> {
                        StockLote nuevo = StockLote.builder()
                                .stock(stockFinal)
                                .lote(loteFinal)
                                .cantidad(0)
                                .build();
                        return stockLoteRepo.save(nuevo);
                    });

            stockLote.setCantidad(stockLote.getCantidad() + cantidadRecibida);
            stockLoteRepo.save(stockLote);
        }

        // Registrar en Kardex
        kardexService.registrarMovimiento(
                articulo,
                bodega,
                lote,
                "INGRESO_RECEPCION",
                "RECEPCION",
                recepcion.getNumeroRecepcion(),
                recepcion.getNumeroDocumento(),
                detalle.getCantidadRecibida(),
                stockAnterior,
                stockNuevo,
                detalle.getValorUnitario(),
                detalle.getMontoTotal(),
                "Recepción " + recepcion.getTipoDocumento() + " N° " + recepcion.getNumeroDocumento(),
                rutUsuario,
                nombreUsuario,
                ipAddress
        );

        log.debug("Detalle procesado: {} +{} → {} (QR: {})",
                sku, detalle.getCantidadRecibida(), stockNuevo, stock.getCodigoQr());

        return stock.getCodigoQr();
    }

    private String generarCodigoQrUnico(String sku, String bodegaCodigo) {
        String prefijo = "DSM-" + bodegaCodigo + "-" + sku + "-";
        String hash;
        String candidato;
        int intentos = 0;

        do {
            hash = java.util.UUID.randomUUID().toString()
                    .replace("-", "").substring(0, 6).toUpperCase();
            candidato = prefijo + hash;
            intentos++;
            if (intentos > 10) {
                throw new BusinessException("No se pudo generar código QR único");
            }
        } while (stockRepo.existsByCodigoQr(candidato));

        return candidato;
    }

    private RecepcionResumenDTO toResumen(Recepcion r) {
        RecepcionResumenDTO dto = RecepcionResumenDTO.from(r);
        Integer totalLineas = recepcionDetalleRepo.findByRecepcionId(r.getId()).size();
        Integer totalUnidades = recepcionDetalleRepo.sumarCantidadPorRecepcion(r.getId());
        dto.setTotalLineas(totalLineas);
        dto.setTotalUnidades(totalUnidades != null ? totalUnidades : 0);
        return dto;
    }
}