package cl.dsm.inventario.controller;

import cl.dsm.inventario.dto.ApiResponse;
import cl.dsm.inventario.dto.ImportResultDTO;
import cl.dsm.inventario.service.ExcelImportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * Controller para importación masiva desde Excel.
 * 
 * ⚠️ Solo JEFATURA y OPERATIVO (RF-019).
 */
@Slf4j
@RestController
@RequestMapping("/api/import")
@RequiredArgsConstructor
@CrossOrigin(origins = "${app.cors.allowed-origins}")
public class ImportController {
    
    private final ExcelImportService importService;
    
    /**
     * POST /api/import/excel
     * Importa un Excel y crea todos los artículos.
     */
    @PostMapping(value = "/excel", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('JEFATURA', 'OPERATIVO')")
    public ResponseEntity<ApiResponse<ImportResultDTO>> importarExcel(
            @RequestParam("archivo") MultipartFile archivo,
            Authentication auth) {
        
        String rutUsuario = auth != null ? auth.getName() : "SYSTEM";
        log.info("Importación solicitada por {}: {}", rutUsuario, archivo.getOriginalFilename());
        
        ImportResultDTO resultado = importService.importarDesdeExcel(archivo, rutUsuario);
        
        return ResponseEntity.ok(ApiResponse.ok(
                "Importación completada. " + resultado.getImportados() + " artículos creados.",
                resultado
        ));
    }
}