package cl.dsm.inventario.service;

import cl.dsm.inventario.dto.ArticuloResponse;
import cl.dsm.inventario.dto.StockBodegaRequest;
import cl.dsm.inventario.entity.Articulo;
import cl.dsm.inventario.entity.Bodega;
import cl.dsm.inventario.entity.StockPorBodega;
import cl.dsm.inventario.exception.BusinessException;
import cl.dsm.inventario.exception.ResourceNotFoundException;
import cl.dsm.inventario.repository.ArticuloRepository;
import cl.dsm.inventario.repository.BodegaRepository;
import cl.dsm.inventario.repository.StockPorBodegaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockBodegaService {
    
    private final StockPorBodegaRepository stockRepo;
    private final ArticuloRepository articuloRepo;
    private final BodegaRepository bodegaRepo;
    
    @Transactional(readOnly = true)
    public List<ArticuloResponse.StockBodegaDTO> listarPorArticulo(Long articuloId) {
        if (!articuloRepo.existsById(articuloId)) {
            throw new ResourceNotFoundException("Artículo no encontrado: " + articuloId);
        }
        
        return stockRepo.findByArticuloIdAndActivoTrue(articuloId).stream()
                .map(this::toDTO)
                .toList();
    }
    
    @Transactional
    public ArticuloResponse.StockBodegaDTO agregarABodega(Long articuloId,
                                                           StockBodegaRequest req,
                                                           String rutUsuario) {
        Articulo articulo = articuloRepo.findById(articuloId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Artículo no encontrado: " + articuloId));
        
        Bodega bodega = bodegaRepo.findById(req.getBodegaId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Bodega no encontrada: " + req.getBodegaId()));
        
        if (stockRepo.existsByArticuloIdAndBodegaId(articuloId, req.getBodegaId())) {
            throw new BusinessException(
                    "El artículo ya está asignado a la bodega " + bodega.getNombre());
        }
        
        // ⚡ Generar código QR único ANTES de guardar
        String codigoQr = generarCodigoQrUnico(articulo.getSku(), bodega.getCodigo());
        
        StockPorBodega stock = StockPorBodega.builder()
                .articulo(articulo)
                .bodega(bodega)
                .stockActual(req.getStockActual() != null ? req.getStockActual() : 0)
                .ubicacionFisica(req.getUbicacionFisica())
                .estante(req.getEstante())
                .nivel(req.getNivel())
                .codigoQr(codigoQr)
                .creadoPor(rutUsuario)
                .actualizadoPor(rutUsuario)
                .build();
        
        stock = stockRepo.save(stock);
        log.info("Artículo {} agregado a bodega {} con {} unidades. QR: {}", 
                 articulo.getSku(), bodega.getNombre(), stock.getStockActual(), codigoQr);
        
        return toDTO(stock);
    }
    
    @Transactional
    public ArticuloResponse.StockBodegaDTO actualizarStock(Long articuloId,
                                                             Integer bodegaId,
                                                             StockBodegaRequest req,
                                                             String rutUsuario) {
        StockPorBodega stock = stockRepo.findByArticuloIdAndBodegaId(articuloId, bodegaId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "El artículo no está asignado a esta bodega"));
        
        if (req.getStockActual() != null && req.getStockActual() < 0) {
            throw new BusinessException("El stock no puede ser negativo");
        }
        
        stock.setStockActual(req.getStockActual());
        if (req.getUbicacionFisica() != null) stock.setUbicacionFisica(req.getUbicacionFisica());
        if (req.getEstante() != null) stock.setEstante(req.getEstante());
        if (req.getNivel() != null) stock.setNivel(req.getNivel());
        stock.setActualizadoPor(rutUsuario);
        
        stock = stockRepo.save(stock);
        log.info("Stock actualizado: {} en {} = {} unidades", 
                 stock.getArticulo().getSku(), 
                 stock.getBodega().getNombre(),
                 stock.getStockActual());
        
        return toDTO(stock);
    }
    
    @Transactional
    public void quitarDeBodega(Long articuloId, Integer bodegaId, String rutUsuario) {
        StockPorBodega stock = stockRepo.findByArticuloIdAndBodegaId(articuloId, bodegaId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "El artículo no está asignado a esta bodega"));
        
        if (stock.getStockActual() > 0) {
            throw new BusinessException(
                    "No se puede quitar de la bodega porque aún tiene stock. " +
                    "Debe moverlo o darlo de baja primero.");
        }
        
        stock.setActivo(false);
        stock.setActualizadoPor(rutUsuario);
        stockRepo.save(stock);
        
        log.info("Artículo {} quitado de bodega {}", 
                 stock.getArticulo().getSku(), stock.getBodega().getNombre());
    }
    
    // ============================================================
    // GENERACIÓN DE CÓDIGO QR ÚNICO
    // ============================================================
    
    /**
     * Genera un código QR único en formato: DSM-{BODEGA}-{SKU}-{HASH6}
     * Ejemplo: DSM-B2-DM-03865-A1B2C3
     */
    private String generarCodigoQrUnico(String sku, String bodegaCodigo) {
        String prefijo = "DSM-" + bodegaCodigo + "-" + sku + "-";
        String hash;
        String candidato;
        int intentos = 0;
        
        do {
            hash = generarHash6();
            candidato = prefijo + hash;
            intentos++;
            if (intentos > 10) {
                throw new BusinessException(
                    "No se pudo generar un código QR único después de 10 intentos");
            }
        } while (stockRepo.existsByCodigoQr(candidato));
        
        return candidato;
    }
    
    private String generarHash6() {
        String uuid = UUID.randomUUID().toString().replace("-", "");
        return uuid.substring(0, 6).toUpperCase();
    }
    
    /**
     * Convierte un StockPorBodega a DTO sin tocar la entidad.
     */
    private ArticuloResponse.StockBodegaDTO toDTO(StockPorBodega s) {
        return ArticuloResponse.StockBodegaDTO.builder()
                .id(s.getId())
                .bodegaId(s.getBodega().getId())
                .bodegaCodigo(s.getBodega().getCodigo())
                .bodegaNombre(s.getBodega().getNombre())
                .dependencia(s.getBodega().getDependencia())
                .stockActual(s.getStockActual())
                .ubicacionFisica(s.getUbicacionFisica())
                .estante(s.getEstante())
                .nivel(s.getNivel())
                .codigoQr(s.getCodigoQr())
                .activo(s.getActivo())
                .build();
    }
}