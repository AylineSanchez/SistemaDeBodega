package cl.dsm.inventario.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "trazabilidad_dm")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TrazabilidadDM {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    // ============================================================
    // VINCULACIÓN
    // ============================================================
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "articulo_id", nullable = false)
    private Articulo articulo;
    
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "lote_id")
    private Lote lote;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_id")
    private StockPorBodega stock;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recepcion_detalle_id")
    private RecepcionDetalle recepcionDetalle;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "despacho_detalle_id")
    private DespachoDetalle despachoDetalle;
    
    // ============================================================
    // DATOS NT 226 (RF-067)
    // ============================================================
    @Column(name = "nombre_producto", nullable = false, length = 200)
    private String nombreProducto;
    
    @Column(name = "rut_proveedor", length = 12)
    private String rutProveedor;
    
    @Column(name = "nombre_proveedor", length = 200)
    private String nombreProveedor;
    
    @Column(name = "numero_factura", length = 50)
    private String numeroFactura;
    
    @Column(name = "numero_guia", length = 50)
    private String numeroGuia;
    
    @Column(length = 100)
    private String modelo;
    
    @Column(name = "numero_lote", length = 50)
    private String numeroLote;
    
    @Column(name = "numero_serie", length = 50)
    private String numeroSerie;
    
    @Column(name = "fecha_vencimiento")
    private LocalDate fechaVencimiento;
    
    // ============================================================
    // DATOS NT 247 (RF-068)
    // ============================================================
    @Column(name = "estado_dm", length = 50)
    private String estadoDm;
    // NUEVO | USADO | VENCIDO | DAÑADO
    
    @Column(name = "numero_registro_sanitario", length = 50)
    private String numeroRegistroSanitario;
    
    @Column(name = "forma_adquisicion", length = 50)
    private String formaAdquisicion;
    
    @Column(name = "fecha_recepcion_conforme")
    private LocalDate fechaRecepcionConforme;
    
    @Column(name = "numero_inventario", length = 50)
    private String numeroInventario;
    
    // ============================================================
    // DATOS DE PACIENTE (para implantables) - RF-068
    // ============================================================
    @Column(name = "paciente_run", length = 12)
    private String pacienteRun;
    
    @Column(name = "paciente_nombre", length = 150)
    private String pacienteNombre;
    
    @Column(name = "fecha_implantacion")
    private LocalDate fechaImplantacion;
    
    @Column(name = "profesional_implantador", length = 150)
    private String profesionalImplantador;
    
    @Column(length = 255)
    private String procedimiento;
    
    // ============================================================
    // ESTADO
    // ============================================================
    @Column(nullable = false, length = 30)
    @Builder.Default
    private String estado = "EN_BODEGA";
    // EN_BODEGA | DESPACHADO | IMPLANTADO | VENCIDO | MERMA | BLOQUEADO
    
    @Column(columnDefinition = "TEXT")
    private String observaciones;
    
    @Column(nullable = false)
    @Builder.Default
    private Boolean activo = true;
    
    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private LocalDateTime fechaCreacion;
    
    @Column(name = "fecha_actualizacion", nullable = false)
    private LocalDateTime fechaActualizacion;
    
    @Column(name = "creado_por", length = 12)
    private String creadoPor;
    
    @Column(name = "actualizado_por", length = 12)
    private String actualizadoPor;
    
    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        this.fechaCreacion = now;
        this.fechaActualizacion = now;
        if (this.activo == null) this.activo = true;
        if (this.estado == null) this.estado = "EN_BODEGA";
    }
    
    @PreUpdate
    public void preUpdate() {
        this.fechaActualizacion = LocalDateTime.now();
    }
}