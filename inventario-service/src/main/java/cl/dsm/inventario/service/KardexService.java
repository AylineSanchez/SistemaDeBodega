package cl.dsm.inventario.service;

import cl.dsm.inventario.dto.KardexDTO;
import cl.dsm.inventario.dto.PageResponse;
import cl.dsm.inventario.entity.*;
import cl.dsm.inventario.repository.KardexRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Servicio de Kardex: historial perpetuo de movimientos.
 * 
 * Registra CADA movimiento de stock (ingreso, egreso, ajuste, merma).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KardexService {
    
    private final KardexRepository kardexRepo;
    
    /**
     * Registra un movimiento en el Kardex.
     * Este método es llamado por RecepcionService y otros servicios.
     */
    @Transactional
    public Kardex registrarMovimiento(
            Articulo articulo,
            Bodega bodega,
            Lote lote,
            String tipoMovimiento,
            String documentoTipo,
            String documentoNumero,
            String documentoReferencia,
            Integer cantidad,                 // positivo=ingreso, negativo=egreso
            Integer stockAnterior,
            Integer stockNuevo,
            BigDecimal valorUnitario,
            BigDecimal valorTotal,
            String observaciones,
            String usuarioRut,
            String usuarioNombre,
            String ipAddress) {
        
        Kardex kardex = Kardex.builder()
                .articulo(articulo)
                .bodega(bodega)
                .lote(lote)
                .tipoMovimiento(tipoMovimiento)
                .documentoTipo(documentoTipo)
                .documentoNumero(documentoNumero)
                .documentoReferencia(documentoReferencia)
                .cantidad(cantidad)
                .stockAnterior(stockAnterior)
                .stockNuevo(stockNuevo)
                .valorUnitario(valorUnitario)
                .valorTotal(valorTotal)
                .observaciones(observaciones)
                .usuarioRut(usuarioRut)
                .usuarioNombre(usuarioNombre)
                .ipAddress(ipAddress)
                .build();
        
        kardex = kardexRepo.save(kardex);
        
        log.info("Kardex: {} {} unidades de {} en {} (stock: {} → {})",
                tipoMovimiento, cantidad, articulo.getSku(), bodega.getCodigo(),
                stockAnterior, stockNuevo);
        
        return kardex;
    }
    
    // ============================================================
    // CONSULTAS
    // ============================================================
    
    @Transactional(readOnly = true)
    public PageResponse<KardexDTO> listar(
            Long articuloId, Integer bodegaId, String tipoMovimiento,
            LocalDateTime fechaDesde, LocalDateTime fechaHasta,
            int page, int size) {
        
        Pageable pageable = PageRequest.of(page, size,
                Sort.by("fechaMovimiento").descending().and(Sort.by("id").descending()));
        
        Page<Kardex> pagina = kardexRepo.buscar(
                articuloId, bodegaId, tipoMovimiento,
                fechaDesde, fechaHasta, pageable);
        
        return PageResponse.<KardexDTO>builder()
                .content(pagina.getContent().stream().map(KardexDTO::from).toList())
                .page(pagina.getNumber())
                .size(pagina.getSize())
                .totalElements(pagina.getTotalElements())
                .totalPages(pagina.getTotalPages())
                .first(pagina.isFirst())
                .last(pagina.isLast())
                .build();
    }
    
    /**
     * Reconstitucion de stock a una fecha pasada (RF-056).
     */
    @Transactional(readOnly = true)
    public Integer obtenerStockAFecha(Long articuloId, Integer bodegaId, LocalDateTime fecha) {
        Integer stock = kardexRepo.findStockAFecha(articuloId, bodegaId, fecha);
        return stock != null ? stock : 0;
    }
    
    /**
     * Último movimiento de un artículo en una bodega.
     */
    @Transactional(readOnly = true)
    public Kardex obtenerUltimoMovimiento(Long articuloId, Integer bodegaId) {
        return kardexRepo.findUltimoMovimiento(articuloId, bodegaId);
    }
}