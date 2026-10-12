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
 * Servicio de Pedidos Internos.
 * 
 * Los pedidos son generados por los encargados de dependencias y
 * gestionados por bodega (picking).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PedidoService {
    
    private final PedidoInternoRepository pedidoRepo;
    private final PedidoDetalleRepository detalleRepo;
    private final CentroCostoRepository centroRepo;
    private final ArticuloRepository articuloRepo;
    
    // ============================================================
    // LISTAR CON FILTROS
    // ============================================================
    @Transactional(readOnly = true)
    public PageResponse<PedidoResponse> listar(
            String q, String estado, Integer centroCostoId, String prioridad,
            int page, int size) {
        
        Pageable pageable = PageRequest.of(page, size);
        Page<PedidoInterno> pagina = pedidoRepo.buscar(
                q, estado, centroCostoId, prioridad, pageable);
        
        List<PedidoResponse> content = pagina.getContent().stream()
                .map(p -> PedidoResponse.from(p, detalleRepo.findByPedidoId(p.getId())))
                .toList();
        
        return PageResponse.<PedidoResponse>builder()
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
    public PedidoResponse obtenerPorId(Long id) {
        PedidoInterno p = pedidoRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido no encontrado: " + id));
        
        List<PedidoDetalle> detalles = detalleRepo.findByPedidoId(id);
        return PedidoResponse.from(p, detalles);
    }
    
    // ============================================================
    // CREAR PEDIDO
    // ============================================================
    @Transactional
    public PedidoResponse crear(PedidoRequest req, String rutUsuario, String nombreUsuario) {
        
        // Validar centro de costo
        CentroCosto centro = centroRepo.findById(req.getCentroCostoId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Centro de costo no encontrado: " + req.getCentroCostoId()));
        
        // Generar número correlativo
        String numeroPedido = generarNumeroPedido(centro.getCodigo());
        
        // Crear cabecera
        PedidoInterno pedido = PedidoInterno.builder()
                .numeroPedido(numeroPedido)
                .centroCosto(centro)
                .solicitanteRut(req.getSolicitanteRut())
                .solicitanteNombre(req.getSolicitanteNombre())
                .solicitanteCargo(req.getSolicitanteCargo())
                .prioridad(req.getPrioridad() != null ? req.getPrioridad() : "NORMAL")
                .fechaRequerida(req.getFechaRequerida())
                .estado("PENDIENTE")
                .jefeServicioRut(req.getJefeServicioRut())
                .jefeServicioNombre(req.getJefeServicioNombre())
                .observaciones(req.getObservaciones())
                .creadoPor(rutUsuario)
                .actualizadoPor(rutUsuario)
                .build();
        
        pedido = pedidoRepo.save(pedido);
        
        // Crear detalles
        List<PedidoDetalle> detalles = new ArrayList<>();
        for (PedidoDetalleRequest detReq : req.getDetalles()) {
            Articulo articulo = articuloRepo.findById(detReq.getArticuloId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Artículo no encontrado: " + detReq.getArticuloId()));
            
            PedidoDetalle detalle = PedidoDetalle.builder()
                    .pedido(pedido)
                    .articulo(articulo)
                    .sku(articulo.getSku())
                    .nombreProducto(articulo.getNombreTecnico())
                    .cantidadSolicitada(detReq.getCantidadSolicitada())
                    .cantidadDespachada(0)
                    .estado("PENDIENTE")
                    .observaciones(detReq.getObservaciones())
                    .build();
            
            detalles.add(detalle);
        }
        
        detalleRepo.saveAll(detalles);
        
        log.info("Pedido {} creado por {} para {}", 
                 numeroPedido, rutUsuario, centro.getNombre());
        
        return PedidoResponse.from(pedido, detalles);
    }
    
    // ============================================================
    // CANCELAR PEDIDO
    // ============================================================
    @Transactional
    public void cancelar(Long id, String motivo, String rutUsuario) {
        PedidoInterno pedido = pedidoRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido no encontrado: " + id));
        
        if ("DESPACHADO".equals(pedido.getEstado())) {
            throw new BusinessException("No se puede cancelar un pedido ya despachado");
        }
        
        pedido.setEstado("CANCELADO");
        pedido.setObservaciones((pedido.getObservaciones() != null ? pedido.getObservaciones() + "\n" : "")
                + "CANCELADO: " + motivo);
        pedido.setActualizadoPor(rutUsuario);
        pedidoRepo.save(pedido);
        
        log.info("Pedido {} cancelado por {}", pedido.getNumeroPedido(), rutUsuario);
    }
    
    // ============================================================
    // HELPERS
    // ============================================================
    
    private String generarNumeroPedido(String centroCodigo) {
        String year = String.valueOf(Year.now().getValue());
        Integer max = pedidoRepo.findMaxNumeroByCentroAndYear(centroCodigo, year);
        int siguiente = (max != null ? max : 0) + 1;
        return String.format("PED-%s-%s-%04d", centroCodigo, year, siguiente);
    }
}