package cl.dsm.inventario.controller;

import cl.dsm.inventario.dto.ApiResponse;
import cl.dsm.inventario.dto.QrInfoDTO;
import cl.dsm.inventario.entity.StockPorBodega;
import cl.dsm.inventario.exception.ResourceNotFoundException;
import cl.dsm.inventario.repository.StockPorBodegaRepository;
import cl.dsm.inventario.service.QrService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/qr")
@RequiredArgsConstructor
@CrossOrigin(origins = "${app.cors.allowed-origins}")
public class QrController {

    private final QrService qrService;
    private final StockPorBodegaRepository stockRepo;

    @GetMapping(value = "/{codigo}", produces = MediaType.IMAGE_PNG_VALUE)
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> generarQrImagen(@PathVariable String codigo) {

        StockPorBodega stock = stockRepo.findByCodigoQrConRelaciones(codigo)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Código QR no encontrado: " + codigo));

        String contenido = construirContenidoQr(stock);
        byte[] png = qrService.generarQrPng(contenido, 300);

        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=3600")
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"qr-" + codigo + ".png\"")
                .body(png);
    }

    @GetMapping("/info/{codigo}")
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<QrInfoDTO>> obtenerInfoQr(@PathVariable String codigo) {
        StockPorBodega stock = stockRepo.findByCodigoQrConRelaciones(codigo)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Código QR no encontrado: " + codigo));

        return ResponseEntity.ok(ApiResponse.ok(QrInfoDTO.from(stock)));
    }

    @GetMapping("/articulo/{articuloId}")
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<List<QrInfoDTO>>> listarQrsArticulo(
            @PathVariable Long articuloId) {

        List<StockPorBodega> stocks = stockRepo.findByArticuloIdAndActivoTrue(articuloId);

        List<QrInfoDTO> qrs = stocks.stream()
                .filter(s -> s.getCodigoQr() != null)
                .map(QrInfoDTO::from)
                .toList();

        return ResponseEntity.ok(ApiResponse.ok(qrs));
    }

    /**
     * ⚡ Endpoint de mantenimiento: regenera los QRs de stocks que no los tengan.
     * Solo accesible por JEFATURA.
     */
    @PostMapping("/regenerar-faltantes")
    @PreAuthorize("hasRole('JEFATURA')")
    @Transactional
    public ResponseEntity<ApiResponse<Integer>> regenerarFaltantes() {
        List<StockPorBodega> sinQr = stockRepo.findByCodigoQrIsNullAndActivoTrue();

        int count = 0;
        for (StockPorBodega stock : sinQr) {
            try {
                String prefijo = "DSM-" + stock.getBodega().getCodigo()
                        + "-" + stock.getArticulo().getSku() + "-";

                String candidato;
                int intentos = 0;
                do {
                    candidato = prefijo + UUID.randomUUID().toString()
                            .replace("-", "").substring(0, 6).toUpperCase();
                    intentos++;
                    if (intentos > 10) {
                        throw new RuntimeException("No se pudo generar QR único");
                    }
                } while (stockRepo.existsByCodigoQr(candidato));

                stock.setCodigoQr(candidato);
                stockRepo.save(stock);
                count++;

                log.info("QR regenerado para stock {} (artículo {}): {}",
                        stock.getId(), stock.getArticulo().getSku(), candidato);

            } catch (Exception e) {
                log.error("Error regenerando QR para stock {}: {}", stock.getId(), e.getMessage());
            }
        }

        return ResponseEntity.ok(ApiResponse.ok(
                "Se regeneraron " + count + " QRs",
                count
        ));
    }

    private String construirContenidoQr(StockPorBodega stock) {
        return String.format(
            "{\"codigo\":\"%s\",\"sku\":\"%s\",\"articulo\":\"%s\",\"bodega\":\"%s\",\"stock\":%d}",
            stock.getCodigoQr(),
            stock.getArticulo().getSku(),
            escaparJson(stock.getArticulo().getNombreTecnico()),
            stock.getBodega().getCodigo(),
            stock.getStockActual()
        );
    }

    private String escaparJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}