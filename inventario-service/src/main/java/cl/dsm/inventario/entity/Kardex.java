package cl.dsm.inventario.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "kardex")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Kardex {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "articulo_id", nullable = false)
    private Articulo articulo;
    
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "bodega_id", nullable = false)
    private Bodega bodega;
    
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "lote_id")
    private Lote lote;
    
    @Column(name = "tipo_movimiento", nullable = false, length = 30)
    private String tipoMovimiento;
    // INGRESO_RECEPCION | EGRESO_DESPACHO | AJUSTE_POSITIVO | AJUSTE_NEGATIVO
    // MERMA_VENCIMIENTO | MERMA_DAÑO | TRASPASO_ENTRADA | TRASPASO_SALIDA
    // DEVOLUCION_PROVEEDOR | BLOQUEO_ISP | DESBLOQUEO_ISP
    
    @Column(name = "documento_tipo", length = 30)
    private String documentoTipo;
    
    @Column(name = "documento_numero", length = 50)
    private String documentoNumero;
    
    @Column(name = "documento_referencia", length = 50)
    private String documentoReferencia;
    
    @Column(nullable = false)
    private Integer cantidad;
    
    @Column(name = "stock_anterior", nullable = false)
    private Integer stockAnterior;
    
    @Column(name = "stock_nuevo", nullable = false)
    private Integer stockNuevo;
    
    @Column(name = "valor_unitario", precision = 12, scale = 2)
    private BigDecimal valorUnitario;
    
    @Column(name = "valor_total", precision = 12, scale = 2)
    private BigDecimal valorTotal;
    
    @Column(columnDefinition = "TEXT")
    private String observaciones;
    
    @Column(name = "usuario_rut", length = 12)
    private String usuarioRut;
    
    @Column(name = "usuario_nombre", length = 150)
    private String usuarioNombre;
    
    @Column(name = "ip_address", length = 45)
    private String ipAddress;
    
    @Column(name = "fecha_movimiento", nullable = false, updatable = false)
    private LocalDateTime fechaMovimiento;
    
    @PrePersist
    public void prePersist() {
        this.fechaMovimiento = LocalDateTime.now();
    }
}