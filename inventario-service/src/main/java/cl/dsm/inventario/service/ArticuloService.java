package cl.dsm.inventario.service;

import cl.dsm.inventario.dto.ArticuloRequest;
import cl.dsm.inventario.dto.ArticuloResponse;
import cl.dsm.inventario.dto.PageResponse;
import cl.dsm.inventario.entity.Articulo;
import cl.dsm.inventario.entity.Bodega;
import cl.dsm.inventario.entity.Categoria;
import cl.dsm.inventario.entity.StockPorBodega;
import cl.dsm.inventario.exception.BusinessException;
import cl.dsm.inventario.exception.ResourceNotFoundException;
import cl.dsm.inventario.repository.ArticuloRepository;
import cl.dsm.inventario.repository.BodegaRepository;
import cl.dsm.inventario.repository.CategoriaRepository;
import cl.dsm.inventario.repository.StockPorBodegaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ArticuloService {

    private final ArticuloRepository articuloRepo;
    private final CategoriaRepository categoriaRepo;
    private final BodegaRepository bodegaRepo;
    private final StockPorBodegaRepository stockRepo;

    // ============================================================
    // LISTAR CON PAGINACIÓN Y FILTROS
    // ⚡ Ahora acepta filtro por bodega (se envía al backend)
    // ============================================================
    @Transactional(readOnly = true)
    public PageResponse<ArticuloResponse> listar(
            String q, Integer categoriaId, Integer bodegaId,
            Boolean incluirInactivos,
            int page, int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by("nombreTecnico").ascending());

        Page<Articulo> pagina = articuloRepo.buscar(
                q != null ? q.trim() : "",
                categoriaId,
                bodegaId,
                incluirInactivos != null && incluirInactivos,
                pageable);

        List<ArticuloResponse> responses = pagina.getContent().stream()
                .map(this::toResponse)
                .toList();

        return PageResponse.<ArticuloResponse>builder()
                .content(responses)
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
    public ArticuloResponse obtenerPorId(Long id) {
        Articulo a = articuloRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Artículo no encontrado: " + id));
        return toResponse(a);
    }

    // ============================================================
    // OBTENER POR SKU
    // ============================================================
    @Transactional(readOnly = true)
    public ArticuloResponse obtenerPorSku(String sku) {
        Articulo a = articuloRepo.findBySku(sku)
                .orElseThrow(() -> new ResourceNotFoundException("Artículo no encontrado: " + sku));
        return toResponse(a);
    }

    // ============================================================
    // CREAR
    // ============================================================
    @Transactional
    public ArticuloResponse crear(ArticuloRequest req, String rutUsuario) {

        Categoria categoria = categoriaRepo.findById(req.getCategoriaId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Categoría no encontrada: " + req.getCategoriaId()));

        String sku = (req.getSku() != null && !req.getSku().isBlank())
                ? req.getSku().trim().toUpperCase()
                : generarSku(categoria.getCodigo());

        if (articuloRepo.existsBySku(sku)) {
            throw new BusinessException("Ya existe un artículo con SKU: " + sku);
        }

        boolean requiereLote = determinarRequiereLote(categoria, req);
        boolean requiereVenc = determinarRequiereVencimiento(categoria, req);
        boolean esDM = determinarEsDispositivoMedico(categoria, req);

        Articulo articulo = Articulo.builder()
                .sku(sku)
                .nombreTecnico(req.getNombreTecnico().trim())
                .presentacion(req.getPresentacion())
                .concentracion(req.getConcentracion())
                .categoria(categoria)
                .subcategoria(req.getSubcategoria())
                .descripcion(req.getDescripcion())
                .unidadMedidaBase(req.getUnidadMedidaBase().toUpperCase())
                .unidadCompra(req.getUnidadCompra())
                .factorConversion(req.getFactorConversion() != null ? req.getFactorConversion() : 1)
                .stockMinimo(req.getStockMinimo() != null ? req.getStockMinimo() : 0)
                .stockMaximo(req.getStockMaximo())
                .stockSeguridad(req.getStockSeguridad() != null ? req.getStockSeguridad() : 0)
                .puntoReorden(req.getPuntoReorden())
                .requiereLote(requiereLote)
                .requiereVencimiento(requiereVenc)
                .registroIsp(req.getRegistroIsp())
                .esDispositivoMedico(esDM)
                .esInsumoCritico(req.getEsInsumoCritico())
                .condicionesAlmacenamiento(req.getCondicionesAlmacenamiento())
                .codigoUnspsc(req.getCodigoUnspsc())
                .tipoProveedorHabitual(req.getTipoProveedorHabitual())
                .codigoCenabast(req.getCodigoCenabast())
                .fabricante(req.getFabricante())
                .paisOrigen(req.getPaisOrigen())
                .precioPromedioPonderado(req.getPrecioPromedioPonderado())
                .observaciones(req.getObservaciones())
                .origenImportacion("MANUAL")
                .activo(true)
                .creadoPor(rutUsuario)
                .actualizadoPor(rutUsuario)
                .build();

        articulo = articuloRepo.save(articulo);
        log.info("Artículo creado: {} ({}) por {}", sku, articulo.getNombreTecnico(), rutUsuario);

        if (req.getBodegaId() != null) {
            Bodega bodega = bodegaRepo.findById(req.getBodegaId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Bodega no encontrada: " + req.getBodegaId()));

            StockPorBodega stock = StockPorBodega.builder()
                    .articulo(articulo)
                    .bodega(bodega)
                    .stockActual(req.getStockInicial() != null ? req.getStockInicial().intValue() : 0)
                    .ubicacionFisica(req.getUbicacionFisica())
                    .creadoPor(rutUsuario)
                    .actualizadoPor(rutUsuario)
                    .build();

            stockRepo.save(stock);
            log.info("Stock inicial creado: {} unidades en {}",
                     stock.getStockActual(), bodega.getNombre());
        }

        Articulo fresco = articuloRepo.findById(articulo.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Error al recargar el artículo"));

        return toResponse(fresco);
    }

    // ============================================================
    // ACTUALIZAR
    // ============================================================
    @Transactional
    public ArticuloResponse actualizar(Long id, ArticuloRequest req, String rutUsuario) {

        Articulo articulo = articuloRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Artículo no encontrado: " + id));

        Categoria categoria = categoriaRepo.findById(req.getCategoriaId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Categoría no encontrada: " + req.getCategoriaId()));

        if (req.getSku() != null && !req.getSku().isBlank()
                && !req.getSku().equals(articulo.getSku())) {
            String nuevoSku = req.getSku().trim().toUpperCase();
            if (articuloRepo.existsBySku(nuevoSku)) {
                throw new BusinessException("Ya existe otro artículo con SKU: " + nuevoSku);
            }
            articulo.setSku(nuevoSku);
        }

        articulo.setNombreTecnico(req.getNombreTecnico().trim());
        articulo.setPresentacion(req.getPresentacion());
        articulo.setConcentracion(req.getConcentracion());
        articulo.setCategoria(categoria);
        articulo.setSubcategoria(req.getSubcategoria());
        articulo.setDescripcion(req.getDescripcion());
        articulo.setUnidadMedidaBase(req.getUnidadMedidaBase().toUpperCase());
        articulo.setUnidadCompra(req.getUnidadCompra());
        articulo.setFactorConversion(req.getFactorConversion());
        articulo.setStockMinimo(req.getStockMinimo());
        articulo.setStockMaximo(req.getStockMaximo());
        articulo.setStockSeguridad(req.getStockSeguridad());
        articulo.setPuntoReorden(req.getPuntoReorden());
        articulo.setRequiereLote(determinarRequiereLote(categoria, req));
        articulo.setRequiereVencimiento(determinarRequiereVencimiento(categoria, req));
        articulo.setRegistroIsp(req.getRegistroIsp());
        articulo.setEsDispositivoMedico(determinarEsDispositivoMedico(categoria, req));
        articulo.setEsInsumoCritico(req.getEsInsumoCritico());
        articulo.setCondicionesAlmacenamiento(req.getCondicionesAlmacenamiento());
        articulo.setCodigoUnspsc(req.getCodigoUnspsc());
        articulo.setTipoProveedorHabitual(req.getTipoProveedorHabitual());
        articulo.setCodigoCenabast(req.getCodigoCenabast());
        articulo.setFabricante(req.getFabricante());
        articulo.setPaisOrigen(req.getPaisOrigen());
        articulo.setPrecioPromedioPonderado(req.getPrecioPromedioPonderado());
        articulo.setObservaciones(req.getObservaciones());
        articulo.setActualizadoPor(rutUsuario);

        articulo = articuloRepo.save(articulo);
        log.info("Artículo actualizado: {} por {}", articulo.getSku(), rutUsuario);

        Articulo fresco = articuloRepo.findById(articulo.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Error al recargar el artículo"));

        return toResponse(fresco);
    }

    // ============================================================
    // BAJA LÓGICA
    // ============================================================
    @Transactional
    public void desactivar(Long id, String rutUsuario) {
        Articulo articulo = articuloRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Artículo no encontrado: " + id));

        articulo.setActivo(false);
        articulo.setActualizadoPor(rutUsuario);
        articuloRepo.save(articulo);

        log.info("Artículo desactivado: {} por {}", articulo.getSku(), rutUsuario);
    }

    // ============================================================
    // REACTIVAR
    // ============================================================
    @Transactional
    public void reactivar(Long id, String rutUsuario) {
        Articulo articulo = articuloRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Artículo no encontrado: " + id));

        articulo.setActivo(true);
        articulo.setActualizadoPor(rutUsuario);
        articuloRepo.save(articulo);

        log.info("Artículo reactivado: {} por {}", articulo.getSku(), rutUsuario);
    }

    // ============================================================
    // HELPERS
    // ============================================================

    private ArticuloResponse toResponse(Articulo a) {
        List<StockPorBodega> stocks = stockRepo.findByArticuloIdAndActivoTrue(a.getId());

        Integer stockTotal = stocks.stream()
                .mapToInt(StockPorBodega::getStockActual)
                .sum();

        return ArticuloResponse.from(a, stocks, stockTotal);
    }

    private String generarSku(String categoriaCodigo) {
        String prefijo = "GEN-" + categoriaCodigo + "-";
        long count = articuloRepo.count() + 1;
        String candidato = prefijo + String.format("%05d", count);

        while (articuloRepo.existsBySku(candidato)) {
            count++;
            candidato = prefijo + String.format("%05d", count);
        }

        return candidato;
    }

    private boolean determinarRequiereLote(Categoria cat, ArticuloRequest req) {
        if (req.getRequiereLote() != null) return req.getRequiereLote();
        String cod = cat.getCodigo();
        return "MEDICOS".equals(cod) || "DENTAL".equals(cod) || "IMPL-DENTAL".equals(cod);
    }

    private boolean determinarRequiereVencimiento(Categoria cat, ArticuloRequest req) {
        if (req.getRequiereVencimiento() != null) return req.getRequiereVencimiento();
        String cod = cat.getCodigo();
        return "MEDICOS".equals(cod) || "DENTAL".equals(cod) || "IMPL-DENTAL".equals(cod);
    }

    private boolean determinarEsDispositivoMedico(Categoria cat, ArticuloRequest req) {
        if (req.getEsDispositivoMedico() != null) return req.getEsDispositivoMedico();
        String cod = cat.getCodigo();
        return "MEDICOS".equals(cod) || "DENTAL".equals(cod) || "IMPL-DENTAL".equals(cod);
    }
}