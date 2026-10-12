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
 * Servicio de Despacho Interno.
 * 
 * ⚡ APLICA FEFO OBLIGATORIO (RN-001):
 * Si un producto requiere lote/vencimiento, se debe despachar primero
 * el lote con fecha de vencimiento más próxima.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DespachoService {
    
    private final DespachoRepository despachoRepo;
    private final DespachoDetalleRepository despachoDetalleRepo;
    private final PedidoInternoRepository pedidoRepo;
    private final PedidoDetalleRepository pedidoDetalleRepo;
    private final CentroCostoRepository centroRepo;
    private final BodegaRepository bodegaRepo;
    private final ArticuloRepository articuloRepo;
    private final StockPorBodegaRepository stockRepo;
    private final StockLoteRepository stockLoteRepo;
    private final KardexService kardexService;
    
    // ============================================================
    // LISTAR
    // ============================================================
    @Transactional(readOnly = true)
    public PageResponse<DespachoResponse> listar(
            String q, String estado, Integer centroCostoId, Integer bodegaId,
            LocalDateTime fechaDesde, LocalDateTime fechaHasta,
            int page, int size) {
        
        Pageable pageable = PageRequest.of(page, size,
                Sort.by("fechaDespacho").descending().and(Sort.by("id").descending()));
        
        Page<Despacho> pagina = despachoRepo.buscar(
                q, estado, centroCostoId, bodegaId, fechaDesde, fechaHasta, pageable);
        
        List<DespachoResponse> content = pagina.getContent().stream()
                .map(d -> DespachoResponse.from(d, despachoDetalleRepo.findByDespachoId(d.getId())))
                .toList();
        
        return PageResponse.<DespachoResponse>builder()
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
    public DespachoResponse obtenerPorId(Long id) {
        Despacho d = despachoRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Despacho no encontrado: " + id));
        
        return DespachoResponse.from(d, despachoDetalleRepo.findByDespachoId(id));
    }
    
    // ============================================================
    // SUGERIR LOTES FEFO
    // ============================================================
    @Transactional(readOnly = true)
    public List<FefoSuggestionDTO> sugerirLotes(Long articuloId, Integer bodegaId, Integer cantidad) {
        
        List<StockLote> stockLotes = stockLoteRepo
                .findFefoPorArticuloBodega(articuloId, bodegaId);
        
        List<FefoSuggestionDTO> sugerencias = new ArrayList<>();
        int restante = cantidad;
        
        for (StockLote sl : stockLotes) {
            if (restante <= 0) break;
            
            int disponible = sl.getCantidad();
            int aUsar = Math.min(disponible, restante);
            
            sugerencias.add(FefoSuggestionDTO.builder()
                    .stockLoteId(sl.getId())
                    .loteId(sl.getLote().getId())
                    .numeroLote(sl.getLote().getNumeroLote())
                    .fechaVencimiento(sl.getLote().getFechaVencimiento())
                    .diasParaVencer(sl.getLote().getDiasParaVencer())
                    .cantidadDisponible(disponible)
                    .cantidadAUsar(aUsar)
                    .build());
            
            restante -= aUsar;
        }
        
        if (restante > 0) {
            throw new BusinessException(
                    "Stock insuficiente. Faltan " + restante + " unidades del artículo.");
        }
        
        return sugerencias;
    }
    
    // ============================================================
    // CREAR DESPACHO (BORRADOR)
    // ============================================================
    @Transactional
    public DespachoResponse crear(DespachoRequest req, String rutUsuario, String nombreUsuario) {
        
        // 1. Validar centro de costo
        CentroCosto centro = centroRepo.findById(req.getCentroCostoId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Centro de costo no encontrado: " + req.getCentroCostoId()));
        
        // 2. Validar bodega
        Bodega bodega = bodegaRepo.findById(req.getBodegaId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Bodega no encontrada: " + req.getBodegaId()));
        
        // 3. Validar pedido si viene
        PedidoInterno pedido = null;
        if (req.getPedidoId() != null) {
            pedido = pedidoRepo.findById(req.getPedidoId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Pedido no encontrado: " + req.getPedidoId()));
            
            if (!"PENDIENTE".equals(pedido.getEstado()) && !"EN_PREPARACION".equals(pedido.getEstado())) {
                throw new BusinessException(
                        "El pedido " + pedido.getNumeroPedido() + 
                        " no está en estado válido para despacho");
            }
        }
        
        // 4. Generar número correlativo
        String numeroDespacho = generarNumeroDespacho();
        
        // 5. Crear cabecera
        Despacho despacho = Despacho.builder()
                .numeroDespacho(numeroDespacho)
                .pedido(pedido)
                .centroCosto(centro)
                .bodega(bodega)
                .receptorRut(req.getReceptorRut())
                .receptorNombre(req.getReceptorNombre())
                .receptorCargo(req.getReceptorCargo())
                .estado("BORRADOR")
                .guiaInterna(req.getGuiaInterna())
                .observaciones(req.getObservaciones())
                .creadoPor(rutUsuario)
                .actualizadoPor(rutUsuario)
                .build();
        
        despacho = despachoRepo.save(despacho);
        
        // 6. Crear detalles (con validación FEFO)
        List<DespachoDetalle> detalles = new ArrayList<>();
        for (DespachoDetalleRequest detReq : req.getDetalles()) {
            DespachoDetalle detalle = crearDetalle(despacho, detReq);
            detalles.add(detalle);
        }
        
        despachoDetalleRepo.saveAll(detalles);
        
        log.info("Despacho {} creado por {} para {}", 
                 numeroDespacho, rutUsuario, centro.getNombre());
        
        return DespachoResponse.from(despacho, detalles);
    }
    
    // ============================================================
    // CONFIRMAR DESPACHO
    // ============================================================
    @Transactional
    public DespachoResponse confirmar(Long id, String rutUsuario, String nombreUsuario, String ipAddress) {
        
        Despacho despacho = despachoRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Despacho no encontrado: " + id));
        
        if (!"BORRADOR".equals(despacho.getEstado())) {
            throw new BusinessException(
                    "Solo se pueden confirmar despachos en estado BORRADOR. " +
                    "Estado actual: " + despacho.getEstado());
        }
        
        List<DespachoDetalle> detalles = despachoDetalleRepo.findByDespachoId(id);
        
        if (detalles.isEmpty()) {
            throw new BusinessException("El despacho no tiene productos");
        }
        
        // Procesar cada línea
        for (DespachoDetalle detalle : detalles) {
            procesarDetalle(despacho, detalle, rutUsuario, nombreUsuario, ipAddress);
        }
        
        despacho.setEstado("CONFIRMADO");
        despacho.setActualizadoPor(rutUsuario);
        despacho = despachoRepo.save(despacho);
        
        // Actualizar pedido si existe
        if (despacho.getPedido() != null) {
            actualizarPedidoTrasDespacho(despacho, rutUsuario, nombreUsuario);
        }
        
        log.info("Despacho CONFIRMADO: {} - Stock actualizado", despacho.getNumeroDespacho());
        
        return DespachoResponse.from(despacho, detalles);
    }
    
    // ============================================================
    // ANULAR DESPACHO
    // ============================================================
    @Transactional
    public void anular(Long id, String motivo, String rutUsuario) {
        Despacho despacho = despachoRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Despacho no encontrado: " + id));
        
        if ("CONFIRMADO".equals(despacho.getEstado())) {
            throw new BusinessException(
                    "No se puede anular un despacho confirmado. " +
                    "Debe hacer un ajuste de inventario.");
        }
        
        despacho.setEstado("ANULADO");
        despacho.setObservaciones((despacho.getObservaciones() != null ? despacho.getObservaciones() + "\n" : "")
                + "ANULADO: " + motivo);
        despacho.setActualizadoPor(rutUsuario);
        despachoRepo.save(despacho);
        
        log.info("Despacho {} anulado por {}", despacho.getNumeroDespacho(), rutUsuario);
    }
    
    // ============================================================
    // HELPERS PRIVADOS
    // ============================================================
    
    /**
     * ⚡ VALIDACIÓN FEFO Y CREACIÓN DE DETALLE
     * 
     * Si el artículo requiere lote:
     * 1. Se busca el lote con vencimiento más próximo (FEFO)
     * 2. Si el usuario especificó un lote distinto → se bloquea
     */
    private DespachoDetalle crearDetalle(Despacho despacho, DespachoDetalleRequest req) {
        
        final Articulo articulo = articuloRepo.findById(req.getArticuloId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Artículo no encontrado: " + req.getArticuloId()));
        
        final Bodega bodega = despacho.getBodega();
        final Integer cantidad = req.getCantidad();
        
        // Obtener stock del artículo en la bodega
        final StockPorBodega stock = stockRepo
                .findByArticuloIdAndBodegaId(articulo.getId(), bodega.getId())
                .orElseThrow(() -> new BusinessException(
                        "El artículo " + articulo.getSku() + 
                        " no existe en la bodega " + bodega.getNombre()));
        
        // Validar stock suficiente
        if (stock.getStockActual() < cantidad) {
            throw new BusinessException(
                    "Stock insuficiente de " + articulo.getSku() + ". " +
                    "Disponible: " + stock.getStockActual() + ", solicitado: " + cantidad);
        }
        
        // ⚡ Variable local que se usará al final (antes del lambda)
        Lote loteSeleccionado = null;
        
        // Si el artículo requiere lote/vencimiento, aplicar FEFO
        if (Boolean.TRUE.equals(articulo.getRequiereLote())) {
            
            // Obtener lotes disponibles ordenados FEFO
            List<StockLote> lotesDisponibles = stockLoteRepo
                    .findFefoPorArticuloBodega(articulo.getId(), bodega.getId());
            
            if (lotesDisponibles.isEmpty()) {
                throw new BusinessException(
                        "No hay lotes disponibles del artículo " + articulo.getSku() +
                        " en la bodega " + bodega.getNombre());
            }
            
            // Lote FEFO (el primero por vencimiento más próximo)
            final Lote loteFefo = lotesDisponibles.get(0).getLote();
            
            // Si el usuario especificó un lote, verificar que sea el FEFO
            if (req.getLoteId() != null) {
                
                final Long loteIdReq = req.getLoteId();
                Lote loteElegido = null;
                
                // Buscar el lote elegido en la lista (con loop tradicional, sin lambda)
                for (StockLote sl : lotesDisponibles) {
                    if (sl.getLote().getId().equals(loteIdReq)) {
                        loteElegido = sl.getLote();
                        break;
                    }
                }
                
                if (loteElegido == null) {
                    throw new BusinessException(
                            "El lote seleccionado no está disponible en esta bodega");
                }
                
                // ⚡ VALIDACIÓN FEFO (RN-001)
                if (!loteElegido.getId().equals(loteFefo.getId())) {
                    throw new BusinessException(
                            "BLOQUEO DE DESPACHO (FEFO): Debe despachar primero el lote " +
                            loteFefo.getNumeroLote() + 
                            " que vence el " + loteFefo.getFechaVencimiento() + 
                            " (anterior al lote escaneado). Operación cancelada.");
                }
                
                loteSeleccionado = loteElegido;
            } else {
                loteSeleccionado = loteFefo;
            }
            
            // Validar stock del lote
            final Lote loteFinal = loteSeleccionado;
            final Long stockId = stock.getId();
            final Long loteId = loteFinal.getId();
            
            StockLote slSeleccionado = stockLoteRepo
                    .findByStockIdAndLoteId(stockId, loteId)
                    .orElseThrow(() -> new BusinessException(
                            "No se encontró stock del lote " + loteFinal.getNumeroLote()));
            
            if (slSeleccionado.getCantidad() < cantidad) {
                throw new BusinessException(
                        "Stock insuficiente en el lote " + loteFinal.getNumeroLote() + 
                        ". Disponible: " + slSeleccionado.getCantidad() + 
                        ", solicitado: " + cantidad);
            }
        }
        
        // ⚡ Capturamos el valor final ANTES de construir el objeto
        final Lote loteParaDetalle = loteSeleccionado;
        
        // Calcular valores
        final BigDecimal valorUnitario = articulo.getPrecioPromedioPonderado() != null
                ? articulo.getPrecioPromedioPonderado()
                : BigDecimal.ZERO;
        final BigDecimal valorTotal = valorUnitario.multiply(BigDecimal.valueOf(cantidad));
        
        return DespachoDetalle.builder()
                .despacho(despacho)
                .articulo(articulo)
                .sku(articulo.getSku())
                .nombreProducto(articulo.getNombreTecnico())
                .lote(loteParaDetalle)
                .numeroLote(loteParaDetalle != null ? loteParaDetalle.getNumeroLote() : null)
                .fechaVencimiento(loteParaDetalle != null ? loteParaDetalle.getFechaVencimiento() : null)
                .cantidad(cantidad)
                .valorUnitario(valorUnitario)
                .valorTotal(valorTotal)
                .observaciones(req.getObservaciones())
                .build();
    }
    
    /**
     * ⚡ PROCESAR DETALLE AL CONFIRMAR
     * Actualiza stock, stock_lotes y registra Kardex.
     */
    private void procesarDetalle(Despacho despacho, DespachoDetalle detalle,
                                   String rutUsuario, String nombreUsuario, String ipAddress) {
        
        // ⚡ Variables FINALES para lambdas
        final Articulo articulo = detalle.getArticulo();
        final Bodega bodega = despacho.getBodega();
        final Lote lote = detalle.getLote();
        final Integer cantidad = detalle.getCantidad();
        final String rutUsuarioFinal = rutUsuario;
        
        // 1. Obtener stock actual
        StockPorBodega stock = stockRepo
                .findByArticuloIdAndBodegaId(articulo.getId(), bodega.getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Stock no encontrado para " + articulo.getSku()));
        
        Integer stockAnterior = stock.getStockActual();
        Integer stockNuevo = stockAnterior - cantidad;
        
        if (stockNuevo < 0) {
            throw new BusinessException(
                    "Stock insuficiente al confirmar: " + articulo.getSku());
        }
        
        stock.setStockActual(stockNuevo);
        stock.setActualizadoPor(rutUsuario);
        stockRepo.save(stock);
        
        // 2. Actualizar stock_lotes si aplica
        if (lote != null) {
            final Long stockId = stock.getId();
            final Long loteId = lote.getId();
            
            StockLote stockLote = stockLoteRepo
                    .findByStockIdAndLoteId(stockId, loteId)
                    .orElseThrow(() -> new BusinessException(
                            "Stock-lote no encontrado para " + lote.getNumeroLote()));
            
            if (stockLote.getCantidad() < cantidad) {
                throw new BusinessException(
                        "Stock insuficiente en lote " + lote.getNumeroLote());
            }
            
            stockLote.setCantidad(stockLote.getCantidad() - cantidad);
            stockLoteRepo.save(stockLote);
        }
        
        // 3. Registrar en Kardex
        kardexService.registrarMovimiento(
                articulo,
                bodega,
                lote,
                "EGRESO_DESPACHO",
                "DESPACHO",
                despacho.getNumeroDespacho(),
                despacho.getGuiaInterna(),
                -cantidad,                       // negativo para egreso
                stockAnterior,
                stockNuevo,
                detalle.getValorUnitario(),
                detalle.getValorTotal(),
                "Despacho a " + despacho.getCentroCosto().getNombre(),
                rutUsuario,
                nombreUsuario,
                ipAddress
        );
        
        log.debug("Detalle despachado: {} -{} → {}", 
                articulo.getSku(), cantidad, stockNuevo);
    }
    
    /**
     * Actualiza el pedido asociado después del despacho.
     */
    private void actualizarPedidoTrasDespacho(Despacho despacho, String rutUsuario, String nombreUsuario) {
        
        PedidoInterno pedido = despacho.getPedido();
        if (pedido == null) return;
        
        List<DespachoDetalle> detalles = despachoDetalleRepo.findByDespachoId(despacho.getId());
        List<PedidoDetalle> pedidoDetalles = pedidoDetalleRepo.findByPedidoId(pedido.getId());
        
        // Actualizar cantidades despachadas en el pedido
        for (PedidoDetalle pd : pedidoDetalles) {
            for (DespachoDetalle dd : detalles) {
                if (pd.getArticulo().getId().equals(dd.getArticulo().getId())) {
                    pd.setCantidadDespachada(pd.getCantidadDespachada() + dd.getCantidad());
                    
                    if (pd.getCantidadDespachada() >= pd.getCantidadSolicitada()) {
                        pd.setEstado("DESPACHADO");
                    } else {
                        pd.setEstado("PARCIAL");
                    }
                }
            }
            pedidoDetalleRepo.save(pd);
        }
        
        // Actualizar estado del pedido
        pedido.setEstado("DESPACHADO");
        pedido.setFechaDespacho(LocalDateTime.now());
        pedido.setReceptorRut(despacho.getReceptorRut());
        pedido.setReceptorNombre(despacho.getReceptorNombre());
        pedido.setFirmaReceptorFecha(LocalDateTime.now());
        pedido.setJefeBodegaRut(rutUsuario);
        pedido.setJefeBodegaNombre(nombreUsuario);
        pedido.setActualizadoPor(rutUsuario);
        pedidoRepo.save(pedido);
    }
    
    private String generarNumeroDespacho() {
        String year = String.valueOf(Year.now().getValue());
        Integer max = despachoRepo.findMaxNumeroDespachoByYear(year);
        int siguiente = (max != null ? max : 0) + 1;
        return String.format("DES-%s-%04d", year, siguiente);
    }
    
    // ============================================================
    // DTO AUXILIAR PARA SUGERENCIAS FEFO
    // ============================================================
    @lombok.Data @lombok.Builder @lombok.NoArgsConstructor @lombok.AllArgsConstructor
    public static class FefoSuggestionDTO {
        private Long stockLoteId;
        private Long loteId;
        private String numeroLote;
        private LocalDate fechaVencimiento;
        private long diasParaVencer;
        private Integer cantidadDisponible;
        private Integer cantidadAUsar;
    }
}