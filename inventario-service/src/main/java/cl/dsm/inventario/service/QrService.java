package cl.dsm.inventario.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Servicio para generar imágenes PNG de códigos QR.
 * Usa la librería ZXing de Google.
 */
@Slf4j
@Service
public class QrService {
    
    private static final int DEFAULT_SIZE = 300;
    private static final String FORMAT = "PNG";
    
    /**
     * Genera una imagen PNG del QR con el contenido dado.
     */
    public byte[] generarQrPng(String contenido, int size) {
        if (contenido == null || contenido.isBlank()) {
            throw new IllegalArgumentException("El contenido del QR no puede estar vacío");
        }
        
        try {
            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
            hints.put(EncodeHintType.MARGIN, 1);
            
            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix matrix = writer.encode(contenido, BarcodeFormat.QR_CODE, size, size, hints);
            
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, FORMAT, baos);
            
            log.debug("QR generado: {} bytes para '{}'", baos.size(), 
                      contenido.length() > 30 ? contenido.substring(0, 30) + "..." : contenido);
            
            return baos.toByteArray();
            
        } catch (WriterException | IOException e) {
            log.error("Error generando QR: {}", e.getMessage(), e);
            throw new RuntimeException("No se pudo generar el código QR", e);
        }
    }
    
    public byte[] generarQrPng(String contenido) {
        return generarQrPng(contenido, DEFAULT_SIZE);
    }
}