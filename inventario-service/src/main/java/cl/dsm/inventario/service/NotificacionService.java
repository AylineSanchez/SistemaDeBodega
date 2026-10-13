package cl.dsm.inventario.service;

import cl.dsm.inventario.entity.Articulo;
import cl.dsm.inventario.entity.BloqueoSanitario;
import cl.dsm.inventario.entity.Lote;
import cl.dsm.inventario.entity.StockPorBodega;
import cl.dsm.inventario.repository.ArticuloRepository;
import cl.dsm.inventario.repository.LoteRepository;
import cl.dsm.inventario.repository.StockPorBodegaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificacionService {

    private final EmailService emailService;
    private final ConfiguracionService configService;
    private final ArticuloRepository articuloRepo;
    private final StockPorBodegaRepository stockRepo;
    private final LoteRepository loteRepo;
    private final JdbcTemplate jdbc;

    private LocalDate ultimaNotifCritico = null;
    private LocalDate ultimaNotifVencimiento = null;

    // ============================================================
    // NOTIFICACIÓN DE STOCK CRÍTICO (1 VEZ AL DÍA - 8:00 AM)
    // ============================================================
    @Scheduled(cron = "0 0 8 * * *", zone = "America/Santiago")
    @Transactional(readOnly = true)
    public void verificarYNotificarStockCritico() {
        if (!configService.isNotifCriticoActivo()) {
            log.debug("Notificaciones de stock crítico desactivadas");
            return;
        }

        LocalDate hoy = LocalDate.now();
        if (hoy.equals(ultimaNotifCritico)) {
            log.debug("Ya se envió notificación de stock crítico hoy");
            return;
        }

        log.info("=== Verificando stock crítico ===");

        Set<Long> articulosConMovimiento = new HashSet<>();
        try {
            List<Long> ids = jdbc.queryForList(
                    "SELECT DISTINCT articulo_id FROM kardex",
                    Long.class);
            articulosConMovimiento.addAll(ids);
        } catch (Exception e) {
            log.warn("No se pudo consultar el Kardex: {}", e.getMessage());
        }

        List<Articulo> articulos = articuloRepo.findAll();
        List<EmailService.StockCriticoInfo> criticos = new ArrayList<>();

        for (Articulo a : articulos) {
            if (!Boolean.TRUE.equals(a.getActivo())) continue;

            int stockTotal = stockRepo.findByArticuloIdAndActivoTrue(a.getId())
                    .stream()
                    .mapToInt(StockPorBodega::getStockActual)
                    .sum();

            Integer minObj = a.getStockMinimo();
            int minimo = (minObj != null && minObj > 0) ? minObj : 0;

            boolean esQuiebre = (stockTotal == 0);
            boolean esCritico = (minimo > 0 && stockTotal > 0 && stockTotal <= minimo);

            if (esCritico) {
                criticos.add(new EmailService.StockCriticoInfo(
                        a.getSku(), a.getNombreTecnico(), stockTotal, minimo));
            } else if (esQuiebre && articulosConMovimiento.contains(a.getId())) {
                criticos.add(new EmailService.StockCriticoInfo(
                        a.getSku(), a.getNombreTecnico(), stockTotal, minimo));
            }
        }

        if (!criticos.isEmpty()) {
            emailService.enviarAlertaStockCritico(criticos);
            ultimaNotifCritico = hoy;
            log.info("✓ Notificación de stock crítico enviada: {} artículos", criticos.size());
        } else {
            log.info("No hay artículos en stock crítico hoy");
        }
    }

    // ============================================================
    // ⚡ NUEVO: NOTIFICACIÓN DE VENCIMIENTOS (1 VEZ AL DÍA - 8:05 AM)
    //
    // Envía un correo con todos los lotes próximos a vencer,
    // respetando la configuración de días del sistema.
    // ============================================================
    @Scheduled(cron = "0 5 8 * * *", zone = "America/Santiago")
    @Transactional(readOnly = true)
    public void verificarYNotificarVencimientos() {
        if (!configService.isNotifVencimientoActivo()) {
            log.debug("Notificaciones de vencimiento desactivadas");
            return;
        }

        LocalDate hoy = LocalDate.now();
        if (hoy.equals(ultimaNotifVencimiento)) {
            log.debug("Ya se envió notificación de vencimientos hoy");
            return;
        }

        log.info("=== Verificando lotes por vencer ===");

        int diasCritico = configService.getDiasAlertaCritico();   // default 120
        int diasProximo = configService.getDiasAlertaProximo();   // default 180
        int diasVigilar = configService.getDiasAlertaVigilar();   // default 365

        // ⚡ Buscar lotes activos, no bloqueados, que venzan dentro del plazo de "vigilar" (365 días)
        List<Lote> lotes = loteRepo.findLotesPorVencer(hoy.plusDays(diasVigilar));

        List<EmailService.VencimientoInfo> porVencer = new ArrayList<>();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy");

        for (Lote lote : lotes) {
            if (lote.getFechaVencimiento() == null) continue;
            if (Boolean.TRUE.equals(lote.getBloqueado())) continue;
            if (!Boolean.TRUE.equals(lote.getActivo())) continue;

            long dias = ChronoUnit.DAYS.between(hoy, lote.getFechaVencimiento());

            String estado;
            if (dias < 0) {
                estado = "VENCIDO";
            } else if (dias <= diasCritico) {
                estado = "CRITICO";
            } else if (dias <= diasProximo) {
                estado = "PROXIMO";
            } else if (dias <= diasVigilar) {
                estado = "VIGILAR";
            } else {
                continue; // Fuera del rango
            }

            // Stock total del lote (suma de todas las bodegas)
            Integer stockTotal = loteRepo.getStockTotalLote(lote.getId());
            if (stockTotal == null || stockTotal == 0) continue; // Sin stock, no alertar

            porVencer.add(new EmailService.VencimientoInfo(
                    lote.getArticulo().getSku(),
                    lote.getArticulo().getNombreTecnico(),
                    lote.getArticulo().getCategoria().getNombre(),
                    lote.getNumeroLote(),
                    lote.getFechaVencimiento().format(fmt),
                    (int) dias,
                    estado,
                    stockTotal
            ));
        }

        if (!porVencer.isEmpty()) {
            emailService.enviarAlertaVencimientos(porVencer);
            ultimaNotifVencimiento = hoy;
            log.info("✓ Notificación de vencimientos enviada: {} lotes", porVencer.size());
        } else {
            log.info("No hay lotes por vencer dentro del plazo configurado");
        }
    }

    // ============================================================
    // NOTIFICACIÓN DE BLOQUEO SANITARIO (INMEDIATA)
    // ============================================================
    public void notificarBloqueoSanitario(BloqueoSanitario bloqueo) {
        if (!configService.isNotifBloqueoActivo()) {
            log.debug("Notificaciones de bloqueo desactivadas");
            return;
        }

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy");

        EmailService.BloqueoInfo info = new EmailService.BloqueoInfo(
                bloqueo.getNumeroBloqueo(),
                bloqueo.getNumeroLote(),
                bloqueo.getSku(),
                bloqueo.getNombreProducto(),
                bloqueo.getFechaVencimiento() != null
                        ? bloqueo.getFechaVencimiento().format(fmt) : "—",
                bloqueo.getStockAfectado() != null ? bloqueo.getStockAfectado() : 0,
                bloqueo.getTipoAlerta(),
                bloqueo.getNumeroOficioIsp(),
                bloqueo.getFechaAlerta() != null
                        ? bloqueo.getFechaAlerta().format(fmt) : "—",
                bloqueo.getBloqueadoPorNombre(),
                bloqueo.getJustificacion()
        );

        emailService.enviarAlertaBloqueoSanitario(info);
    }
}