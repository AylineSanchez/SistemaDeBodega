package cl.dsm.inventario.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;
    private final JdbcTemplate jdbc;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Value("${app.email.from-name:Sistema Bodega DSM}")
    private String fromName;

    @Value("${app.frontend.base-url:http://localhost:8082}")
    private String frontendBaseUrl;

    // ============================================================
    // OBTENER DESTINATARIOS POR ROL
    // ============================================================
    public List<String> obtenerEmailsPorRoles(List<String> roles) {
        if (roles == null || roles.isEmpty()) return List.of();

        String placeholders = String.join(",", roles.stream().map(r -> "?").toList());

        String sql = """
            SELECT DISTINCT u.email
            FROM usuarios u
            JOIN roles r ON r.id = u.rol_id
            WHERE u.activo = TRUE
              AND u.bloqueado = FALSE
              AND r.nombre IN (""" + placeholders + ")";

        return jdbc.queryForList(sql, String.class, roles.toArray());
    }

    // ============================================================
    // ENVIAR ALERTA DE STOCK CRÍTICO
    // ============================================================
    @Async
    public void enviarAlertaStockCritico(List<StockCriticoInfo> articulosCriticos) {
        if (articulosCriticos == null || articulosCriticos.isEmpty()) return;

        try {
            List<String> destinatarios = obtenerEmailsPorRoles(
                    List.of("JEFATURA", "OPERATIVO"));

            if (destinatarios.isEmpty()) {
                log.warn("No hay destinatarios para alerta de stock crítico");
                return;
            }

            String subject = "🔴 Alerta: " + articulosCriticos.size() +
                    " artículos en stock crítico - Bodega DSM";
            String html = construirHtmlStockCritico(articulosCriticos);

            enviarEmail(destinatarios, subject, html);
            log.info("✓ Alerta de stock crítico enviada a {} destinatarios", destinatarios.size());

        } catch (Exception e) {
            log.error("Error enviando alerta de stock crítico: {}", e.getMessage(), e);
        }
    }

    // ============================================================
    // ⚡ NUEVO: ENVIAR ALERTA DE VENCIMIENTOS
    // ============================================================
    @Async
    public void enviarAlertaVencimientos(List<VencimientoInfo> vencimientos) {
        if (vencimientos == null || vencimientos.isEmpty()) return;

        try {
            List<String> destinatarios = obtenerEmailsPorRoles(
                    List.of("JEFATURA", "OPERATIVO", "AUDITOR"));

            if (destinatarios.isEmpty()) {
                log.warn("No hay destinatarios para alerta de vencimientos");
                return;
            }

            String subject = "⏰ Alerta: " + vencimientos.size() +
                    " lotes próximos a vencer - Bodega DSM";
            String html = construirHtmlVencimientos(vencimientos);

            enviarEmail(destinatarios, subject, html);
            log.info("✓ Alerta de vencimientos enviada a {} destinatarios", destinatarios.size());

        } catch (Exception e) {
            log.error("Error enviando alerta de vencimientos: {}", e.getMessage(), e);
        }
    }

    // ============================================================
    // ENVIAR ALERTA DE BLOQUEO SANITARIO
    // ============================================================
    @Async
    public void enviarAlertaBloqueoSanitario(BloqueoInfo bloqueo) {
        if (bloqueo == null) return;

        try {
            List<String> destinatarios = obtenerEmailsPorRoles(
                    List.of("JEFATURA", "OPERATIVO", "AUDITOR"));

            if (destinatarios.isEmpty()) {
                log.warn("No hay destinatarios para alerta de bloqueo sanitario");
                return;
            }

            String subject = "🚨 Bloqueo Sanitario: " + bloqueo.numeroBloqueo() +
                    " - Lote " + bloqueo.numeroLote();
            String html = construirHtmlBloqueo(bloqueo);

            enviarEmail(destinatarios, subject, html);
            log.info("✓ Alerta de bloqueo sanitario enviada a {} destinatarios", destinatarios.size());

        } catch (Exception e) {
            log.error("Error enviando alerta de bloqueo: {}", e.getMessage(), e);
        }
    }

    // ============================================================
    // MÉTODO GENÉRICO DE ENVÍO
    // ============================================================
    private void enviarEmail(List<String> destinatarios, String subject, String html)
            throws MessagingException, UnsupportedEncodingException {

        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setFrom(fromEmail, fromName);
        helper.setTo(destinatarios.toArray(new String[0]));
        helper.setSubject(subject);
        helper.setText(html, true);

        mailSender.send(message);
    }

    // ============================================================
    // TEMPLATE HTML: STOCK CRÍTICO
    // ============================================================
    private String construirHtmlStockCritico(List<StockCriticoInfo> articulos) {
        StringBuilder filas = new StringBuilder();
        for (StockCriticoInfo a : articulos) {
            String color = a.stockTotal() == 0 ? "#e74c3c" : "#e67e22";
            String estado = a.stockTotal() == 0 ? "QUIEBRE" : "CRÍTICO";

            filas.append("""
                <tr>
                    <td style="padding:10px;border-bottom:1px solid #eee;font-family:monospace;">%s</td>
                    <td style="padding:10px;border-bottom:1px solid #eee;">%s</td>
                    <td style="padding:10px;border-bottom:1px solid #eee;text-align:center;font-weight:700;color:%s;">%d</td>
                    <td style="padding:10px;border-bottom:1px solid #eee;text-align:center;">%d</td>
                    <td style="padding:10px;border-bottom:1px solid #eee;text-align:center;">
                        <span style="background:%s;color:white;padding:3px 10px;border-radius:10px;font-size:0.8em;font-weight:700;">%s</span>
                    </td>
                </tr>
                """.formatted(a.sku(), escapeHtml(a.nombre()), color, a.stockTotal(),
                              a.stockMinimo(), color, estado));
        }

        return """
            <!DOCTYPE html><html><head><meta charset="UTF-8"></head>
            <body style="font-family:'Segoe UI',Arial,sans-serif;background:#f0ece4;margin:0;padding:20px;">
                <div style="max-width:800px;margin:0 auto;background:#fff;border-radius:16px;overflow:hidden;box-shadow:0 4px 20px rgba(0,0,0,0.08);">
                    <div style="background:linear-gradient(135deg,#e74c3c,#c0392b);padding:30px;text-align:center;color:#fff;">
                        <h1 style="margin:0;font-size:1.4em;">🔴 Alerta de Stock Crítico</h1>
                        <p style="margin:8px 0 0 0;opacity:0.9;">Sistema de Gestión Integral de Bodega - DSM Santa Cruz</p>
                    </div>
                    <div style="padding:30px;">
                        <p style="color:#2c3e50;font-size:1.05em;">
                            Se detectaron <strong style="color:#c0392b;">%d artículos</strong>
                            con stock crítico o en quiebre que requieren reposición inmediata.
                        </p>
                        <table style="width:100%%;border-collapse:collapse;margin-top:20px;">
                            <thead><tr style="background:#f8f5f0;">
                                <th style="padding:12px;text-align:left;font-size:0.85em;">SKU</th>
                                <th style="padding:12px;text-align:left;font-size:0.85em;">Producto</th>
                                <th style="padding:12px;text-align:center;font-size:0.85em;">Stock</th>
                                <th style="padding:12px;text-align:center;font-size:0.85em;">Mínimo</th>
                                <th style="padding:12px;text-align:center;font-size:0.85em;">Estado</th>
                            </tr></thead>
                            <tbody>%s</tbody>
                        </table>
                        <p style="margin-top:25px;text-align:center;">
                            <a href="%s/articulos.html" style="display:inline-block;padding:12px 30px;background:linear-gradient(135deg,#e67e22,#d35400);color:#fff;text-decoration:none;border-radius:50px;font-weight:700;">Ver Maestro de Artículos</a>
                        </p>
                    </div>
                    <div style="background:#f8f5f0;padding:20px 30px;text-align:center;font-size:0.8em;color:#7f8c8d;">
                        © 2026 Ilustre Municipalidad de Santa Cruz - DSM
                    </div>
                </div>
            </body></html>
            """.formatted(articulos.size(), filas.toString(), frontendBaseUrl);
    }

    // ============================================================
    // ⚡ NUEVO: TEMPLATE HTML: VENCIMIENTOS
    // ============================================================
    private String construirHtmlVencimientos(List<VencimientoInfo> vencimientos) {
        StringBuilder filas = new StringBuilder();
        for (VencimientoInfo v : vencimientos) {
            String color = switch (v.estado()) {
                case "VENCIDO" -> "#7d2320";
                case "CRITICO" -> "#c0392b";
                case "PROXIMO" -> "#e67e22";
                case "VIGILAR" -> "#b7950b";
                default -> "#7f8c8d";
            };
            String bgEstado = switch (v.estado()) {
                case "VENCIDO" -> "#fadbd8";
                case "CRITICO" -> "#fdedec";
                case "PROXIMO" -> "#fdebd0";
                case "VIGILAR" -> "#fef9e7";
                default -> "#ecf0f1";
            };

            filas.append("""
                <tr>
                    <td style="padding:10px;border-bottom:1px solid #eee;font-family:monospace;font-size:0.85em;">%s</td>
                    <td style="padding:10px;border-bottom:1px solid #eee;">%s</td>
                    <td style="padding:10px;border-bottom:1px solid #eee;font-size:0.85em;color:#7f8c8d;">%s</td>
                    <td style="padding:10px;border-bottom:1px solid #eee;font-family:monospace;font-size:0.85em;">%s</td>
                    <td style="padding:10px;border-bottom:1px solid #eee;text-align:center;font-size:0.85em;">%s</td>
                    <td style="padding:10px;border-bottom:1px solid #eee;text-align:center;font-weight:700;color:%s;">%d</td>
                    <td style="padding:10px;border-bottom:1px solid #eee;text-align:center;font-weight:700;">%d</td>
                    <td style="padding:10px;border-bottom:1px solid #eee;text-align:center;">
                        <span style="background:%s;color:%s;padding:3px 10px;border-radius:10px;font-size:0.75em;font-weight:700;">%s</span>
                    </td>
                </tr>
                """.formatted(v.sku(), escapeHtml(v.nombre()), escapeHtml(v.categoria()),
                              v.numeroLote(), v.fechaVencimiento(), color, v.diasParaVencer(),
                              v.stockTotal(), bgEstado, color, v.estado()));
        }

        return """
            <!DOCTYPE html><html><head><meta charset="UTF-8"></head>
            <body style="font-family:'Segoe UI',Arial,sans-serif;background:#f0ece4;margin:0;padding:20px;">
                <div style="max-width:1000px;margin:0 auto;background:#fff;border-radius:16px;overflow:hidden;box-shadow:0 4px 20px rgba(0,0,0,0.08);">
                    <div style="background:linear-gradient(135deg,#e67e22,#d35400);padding:30px;text-align:center;color:#fff;">
                        <h1 style="margin:0;font-size:1.4em;">⏰ Alerta de Vencimientos</h1>
                        <p style="margin:8px 0 0 0;opacity:0.9;">Sistema de Gestión Integral de Bodega - DSM Santa Cruz</p>
                    </div>
                    <div style="padding:30px;">
                        <p style="color:#2c3e50;font-size:1.05em;">
                            Se detectaron <strong style="color:#d35400;">%d lotes</strong>
                            próximos a vencer que requieren priorización en su despacho (FEFO).
                        </p>
                        <div style="background:#fef9e7;border-left:4px solid #f39c12;padding:12px 18px;border-radius:8px;margin:15px 0;font-size:0.9em;color:#7d6608;">
                            ℹ️ <strong>Recuerde:</strong> Por normativa FEFO, debe despachar primero el lote con vencimiento más próximo.
                        </div>
                        <table style="width:100%%;border-collapse:collapse;margin-top:20px;">
                            <thead><tr style="background:#f8f5f0;">
                                <th style="padding:10px;text-align:left;font-size:0.8em;">SKU</th>
                                <th style="padding:10px;text-align:left;font-size:0.8em;">Producto</th>
                                <th style="padding:10px;text-align:left;font-size:0.8em;">Categoría</th>
                                <th style="padding:10px;text-align:left;font-size:0.8em;">Lote</th>
                                <th style="padding:10px;text-align:center;font-size:0.8em;">Vence</th>
                                <th style="padding:10px;text-align:center;font-size:0.8em;">Días</th>
                                <th style="padding:10px;text-align:center;font-size:0.8em;">Stock</th>
                                <th style="padding:10px;text-align:center;font-size:0.8em;">Estado</th>
                            </tr></thead>
                            <tbody>%s</tbody>
                        </table>
                        <p style="margin-top:25px;text-align:center;">
                            <a href="%s/trazabilidad.html" style="display:inline-block;padding:12px 30px;background:linear-gradient(135deg,#e67e22,#d35400);color:#fff;text-decoration:none;border-radius:50px;font-weight:700;">Ver Trazabilidad Sanitaria</a>
                        </p>
                    </div>
                    <div style="background:#f8f5f0;padding:20px 30px;text-align:center;font-size:0.8em;color:#7f8c8d;">
                        © 2026 Ilustre Municipalidad de Santa Cruz - DSM
                    </div>
                </div>
            </body></html>
            """.formatted(vencimientos.size(), filas.toString(), frontendBaseUrl);
    }

    // ============================================================
    // TEMPLATE HTML: BLOQUEO SANITARIO
    // ============================================================
    private String construirHtmlBloqueo(BloqueoInfo b) {
        return """
            <!DOCTYPE html><html><head><meta charset="UTF-8"></head>
            <body style="font-family:'Segoe UI',Arial,sans-serif;background:#f0ece4;margin:0;padding:20px;">
                <div style="max-width:700px;margin:0 auto;background:#fff;border-radius:16px;overflow:hidden;box-shadow:0 4px 20px rgba(0,0,0,0.08);">
                    <div style="background:linear-gradient(135deg,#e74c3c,#c0392b);padding:30px;text-align:center;color:#fff;">
                        <h1 style="margin:0;font-size:1.4em;">🚨 Bloqueo Sanitario de Lote</h1>
                    </div>
                    <div style="padding:30px;">
                        <div style="background:#fdedec;border-left:4px solid #e74c3c;padding:15px 20px;border-radius:8px;margin-bottom:20px;">
                            <strong style="color:#c0392b;">N° Bloqueo:</strong> <span>%s</span>
                        </div>
                        <h2 style="font-size:1.1em;">Información del Lote</h2>
                        <table style="width:100%%;">
                            <tr><td style="padding:8px 0;color:#7f8c8d;font-weight:600;">Lote:</td><td><strong>%s</strong></td></tr>
                            <tr><td style="padding:8px 0;color:#7f8c8d;font-weight:600;">SKU:</td><td>%s</td></tr>
                            <tr><td style="padding:8px 0;color:#7f8c8d;font-weight:600;">Producto:</td><td>%s</td></tr>
                            <tr><td style="padding:8px 0;color:#7f8c8d;font-weight:600;">Vencimiento:</td><td>%s</td></tr>
                            <tr><td style="padding:8px 0;color:#7f8c8d;font-weight:600;">Stock afectado:</td><td style="color:#c0392b;font-weight:700;">%d unidades</td></tr>
                        </table>
                        <h2 style="font-size:1.1em;margin-top:25px;">Información de la Alerta</h2>
                        <table style="width:100%%;">
                            <tr><td style="padding:8px 0;color:#7f8c8d;font-weight:600;">Tipo:</td><td>%s</td></tr>
                            <tr><td style="padding:8px 0;color:#7f8c8d;font-weight:600;">N° Oficio ISP:</td><td>%s</td></tr>
                            <tr><td style="padding:8px 0;color:#7f8c8d;font-weight:600;">Fecha Alerta:</td><td>%s</td></tr>
                            <tr><td style="padding:8px 0;color:#7f8c8d;font-weight:600;">Bloqueado por:</td><td>%s</td></tr>
                        </table>
                        <div style="background:#f8f5f0;padding:15px;border-radius:8px;margin-top:20px;">
                            <strong style="color:#7f8c8d;font-size:0.85em;">JUSTIFICACIÓN:</strong>
                            <p style="color:#2c3e50;margin-top:8px;">%s</p>
                        </div>
                        <div style="background:#fef9e7;border-left:4px solid #f39c12;padding:12px 18px;border-radius:8px;margin-top:20px;font-size:0.9em;color:#7d6608;">
                            ⚠️ <strong>El lote no podrá ser despachado</strong> hasta que se levante el bloqueo.
                        </div>
                        <p style="margin-top:25px;text-align:center;">
                            <a href="%s/trazabilidad.html" style="display:inline-block;padding:12px 30px;background:linear-gradient(135deg,#e67e22,#d35400);color:#fff;text-decoration:none;border-radius:50px;font-weight:700;">Ver Trazabilidad Sanitaria</a>
                        </p>
                    </div>
                    <div style="background:#f8f5f0;padding:20px 30px;text-align:center;font-size:0.8em;color:#7f8c8d;">
                        © 2026 Ilustre Municipalidad de Santa Cruz - DSM
                    </div>
                </div>
            </body></html>
            """.formatted(
                b.numeroBloqueo(), b.numeroLote(), b.sku(), escapeHtml(b.nombreProducto()),
                b.fechaVencimiento() != null ? b.fechaVencimiento() : "—",
                b.stockAfectado(), b.tipoAlerta(),
                b.numeroOficioIsp() != null ? b.numeroOficioIsp() : "—",
                b.fechaAlerta() != null ? b.fechaAlerta() : "—",
                escapeHtml(b.bloqueadoPorNombre()), escapeHtml(b.justificacion()),
                frontendBaseUrl);
    }

    private String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#039;");
    }

    // ============================================================
    // CLASES INTERNAS (records)
    // ============================================================
    public record StockCriticoInfo(String sku, String nombre, int stockTotal, int stockMinimo) {}

    // ⚡ NUEVO
    public record VencimientoInfo(
            String sku,
            String nombre,
            String categoria,
            String numeroLote,
            String fechaVencimiento,
            int diasParaVencer,
            String estado,
            int stockTotal
    ) {}

    public record BloqueoInfo(
            String numeroBloqueo, String numeroLote, String sku, String nombreProducto,
            String fechaVencimiento, int stockAfectado, String tipoAlerta,
            String numeroOficioIsp, String fechaAlerta, String bloqueadoPorNombre,
            String justificacion
    ) {}
}