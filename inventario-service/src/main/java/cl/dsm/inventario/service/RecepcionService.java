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
import java.util.List;

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
    // ============================================================
    @Transactional(readOnly = true)
    public RecepcionResponse obtenerPorId(Long id) {
        Recepcion r = recepcionRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Recepción no encontrada: " + id));
        
        List<RecepcionDetalle> detalles = recepcionDetalleRepo.findByRecepcionId(id);
        return RecepcionResponse.from(r, detalles);
    }
    
    // ============================================================
    // CREAR RECEPCIÓN (RF-020 a RF-024)
    // ============================================================
    @Transactional
    public RecepcionResponse crear(RecepcionRequest req, String rutUsuario, String nombreUsuario) {
        
        log.info("Creando recepción: {} {} - Proveedor: {}",
                req.getTipoDocumento(), req.getNumeroDocumento(), req.getNombreProveedor());
        
        // 1. Validar bodega
        Bodega bodega = bodegaRepo.findById(req.getBodegaId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Bodega no encontrada: " + req.getBodegaId()));
        
        // 2. Validar duplicado de documento (RN-014)
        if (recepcionRepo.existsByTipoDocumentoAndNumeroDocumento(
                req.getTipoDocumento(), req.getNumeroDocumento())) {
            throw new BusinessException(
                    "Ya existe una recepción para " + req.getTipoDocumento() + 
                    " N° " + req.getNumeroDocumento());
        }
        
        // 3. Generar número correlativo
        String numeroRecepcion = generarNumeroRecepcion();
        
        // 4. Crear cabecera
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
        
        // 5. Crear detalles
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
    // CONFIRMAR RECEPCIÓN (RF-025)
    // ============================================================
    @Transactional
    public RecepcionResponse confirmar(Long id, String rutUsuario, String nombreUsuario, String ipAddress) {
        
        Recepcion recepcion = recepcionRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Recepción no encontrada: " + id));
        
        if (!"BORRADOR".equals(recepcion.getEstado())) {
            throw new BusinessException(
                    "Solo se pueden confirmar recepciones en estado BORRADOR. " +
                    "Estado actual: " + recepcion.getEstado());
        }
        
        List<RecepcionDetalle> detalles = recepcionDetalleRepo.findByRecepcionId(id);
        
        if (detalles.isEmpty()) {
            throw new BusinessException("La recepción no tiene productos");
        }
        
        // Procesar cada línea
        for (RecepcionDetalle detalle : detalles) {
            procesarDetalle(recepcion, detalle, rutUsuario, nombreUsuario, ipAddress);
        }
        
        recepcion.setEstado("CONFIRMADA");
        recepcion.setActualizadoPor(rutUsuario);
        recepcion = recepcionRepo.save(recepcion);
        
        log.info("Recepción CONFIRMADA: {} - Stock actualizado", recepcion.getNumeroRecepcion());
        
        return RecepcionResponse.from(recepcion, detalles);
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
        
        // Validar vencimiento (RN-002)
        if (req.getFechaVencimiento() != null && 
                req.getFechaVencimiento().isBefore(LocalDate.now())) {
            throw new BusinessException(
                    "El artículo " + articulo.getSku() + 
                    " tiene fecha de vencimiento pasada: " + req.getFechaVencimiento());
        }
        
        // Validar que si requiere lote, venga el lote (RN-009)
        if (Boolean.TRUE.equals(articulo.getRequiereLote())) {
            if (req.getNumeroLote() == null || req.getNumeroLote().isBlank()) {
                throw new BusinessException(
                        "El artículo " + articulo.getSku() + " requiere N° de lote");
            }
        }
        
        // Validar que si requiere vencimiento, venga la fecha
        if (Boolean.TRUE.equals(articulo.getRequiereVencimiento())) {
            if (req.getFechaVencimiento() == null) {
                throw new BusinessException(
                        "El artículo " + articulo.getSku() + " requiere fecha de vencimiento");
            }
        }
        
        // Calcular monto total si no viene
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
     * ⚡ MÉTODO CORREGIDO: usa variables finales para los lambdas.
     * 
     * Procesa un detalle al confirmar:
     * 1. Crea/obtiene el stock del artículo en la bodega
     * 2. Crea/obtiene el lote
     * 3. Actualiza stock_lotes
     * 4. Registra en Kardex
     */
    private void procesarDetalle(Recepcion recepcion, RecepcionDetalle detalle,
                                   String rutUsuario, String nombreUsuario, String ipAddress) {
        
        // ⚡ Variables FINALES que se usarán dentro de lambdas
        final Articulo articulo = detalle.getArticulo();
        final Bodega bodega = recepcion.getBodega();
        final String rutUsuarioFinal = rutUsuario;
        final String sku = articulo.getSku();
        final String codigoBodega = bodega.getCodigo();
        
        // ============================================================
        // 1. Obtener o crear el stock del artículo en la bodega
        // ============================================================
        StockPorBodega stock = stockRepo
                .findByArticuloIdAndBodegaId(articulo.getId(), bodega.getId())
                .orElseGet(() -> {
                    StockPorBodega nuevo = StockPorBodega.builder()
                            .articulo(articulo)
                            .bodega(bodega)
                            .stockActual(0)
                            .ubicacionFisica(detalle.getUbicacionFisica())
                            .estante(detalle.getEstante())
                            .nivel(detalle.getNivel())
                            .creadoPor(rutUsuarioFinal)
                            .actualizadoPor(rutUsuarioFinal)
                            .build();
                    
                    // Generar código QR
                    nuevo.setCodigoQr(generarCodigoQrUnico(sku, codigoBodega));
                    
                    return stockRepo.save(nuevo);
                });
        
        Integer stockAnterior = stock.getStockActual();
        Integer stockNuevo = stockAnterior + detalle.getCantidadRecibida();
        
        stock.setStockActual(stockNuevo);
        stock.setActualizadoPor(rutUsuario);
        if (detalle.getUbicacionFisica() != null) stock.setUbicacionFisica(detalle.getUbicacionFisica());
        if (detalle.getEstante() != null) stock.setEstante(detalle.getEstante());
        if (detalle.getNivel() != null) stock.setNivel(detalle.getNivel());
        stockRepo.save(stock);
        
        // ============================================================
        // 2. Obtener o crear el lote
        // ============================================================
        Lote lote = null;
        if (detalle.getNumeroLote() != null && !detalle.getNumeroLote().isBlank()) {
            
            // ⚡ Capturar valores antes del lambda
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
            
            // ============================================================
            // 3. Actualizar stock_lotes
            // ============================================================
            // ⚡ Capturar el stock actual (final) para el lambda
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
        
        // ============================================================
        // 4. Registrar en Kardex
        // ============================================================
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
        
        log.debug("Detalle procesado: {} +{} → {}", 
                articulo.getSku(), detalle.getCantidadRecibida(), stockNuevo);
    }
    
    /**
     * Genera un código QR único: DSM-{BODEGA}-{SKU}-{HASH6}
     * 
     * ⚡ Este método NO usa variables externas mutables, así que no hay problema con lambdas.
     */
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