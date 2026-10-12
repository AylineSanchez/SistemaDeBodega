package cl.dsm.inventario.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "maestro_articulos")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Articulo {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    // ============================================================
    // 1. IDENTIFICACIÓN BÁSICA Y CLASIFICACIÓN
    // ============================================================
    @Column(nullable = false, unique = true, length = 50)
    private String sku;
    
    @Column(name = "nombre_tecnico", nullable = false, length = 200)
    private String nombreTecnico;
    
    @Column(length = 150)
    private String presentacion;
    
    @Column(length = 100)
    private String concentracion;
    
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "categoria_id", nullable = false)
    private Categoria categoria;
    
    @Column(length = 100)
    private String subcategoria;
    
    @Column(columnDefinition = "TEXT")
    private String descripcion;
    
    // ============================================================
    // 2. DATOS LOGÍSTICOS
    // ============================================================
    @Column(name = "unidad_medida_base", nullable = false, length = 30)
    private String unidadMedidaBase;
    
    @Column(name = "unidad_compra", length = 30)
    private String unidadCompra;
    
    @Column(name = "factor_conversion", nullable = false)
    @Builder.Default
    private Integer factorConversion = 1;
    
    @Column(name = "stock_minimo", nullable = false)
    @Builder.Default
    private Integer stockMinimo = 0;
    
    @Column(name = "stock_maximo")
    private Integer stockMaximo;
    
    @Column(name = "stock_seguridad", nullable = false)
    @Builder.Default
    private Integer stockSeguridad = 0;
    
    @Column(name = "punto_reorden")
    private Integer puntoReorden;
    
    // ============================================================
    // 3. DATOS SANITARIOS
    // ============================================================
    @Column(name = "requiere_lote", nullable = false)
    @Builder.Default
    private Boolean requiereLote = false;
    
    @Column(name = "requiere_vencimiento", nullable = false)
    @Builder.Default
    private Boolean requiereVencimiento = false;
    
    @Column(name = "registro_isp", length = 50)
    private String registroIsp;
    
    @Column(name = "es_dispositivo_medico", nullable = false)
    @Builder.Default
    private Boolean esDispositivoMedico = false;
    
    @Column(name = "es_insumo_critico", nullable = false)
    @Builder.Default
    private Boolean esInsumoCritico = false;
    
    @Column(name = "condiciones_almacenamiento", length = 255)
    private String condicionesAlmacenamiento;
    
    // ============================================================
    // 4. DATOS DE COMPRAS PÚBLICAS
    // ============================================================
    @Column(name = "codigo_unspsc", length = 20)
    private String codigoUnspsc;
    
    @Column(name = "tipo_proveedor_habitual", length = 50)
    private String tipoProveedorHabitual;
    
    @Column(name = "codigo_cenabast", length = 50)
    private String codigoCenabast;
    
    @Column(length = 150)
    private String fabricante;
    
    @Column(name = "pais_origen", length = 80)
    private String paisOrigen;
    
    // ============================================================
    // 5. CONTROL
    // ============================================================
    @Column(name = "precio_promedio_ponderado", precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal precioPromedioPonderado = BigDecimal.ZERO;
    
    @Column(nullable = false)
    @Builder.Default
    private Boolean activo = true;
    
    @Column(columnDefinition = "TEXT")
    private String observaciones;
    
    @Column(name = "origen_importacion", length = 50)
    private String origenImportacion;
    
    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private LocalDateTime fechaCreacion;
    
    @Column(name = "fecha_actualizacion", nullable = false)
    private LocalDateTime fechaActualizacion;
    
    @Column(name = "creado_por", length = 12)
    private String creadoPor;
    
    @Column(name = "actualizado_por", length = 12)
    private String actualizadoPor;
    
    // ============================================================
    // RELACIONES
    // ============================================================
    @OneToMany(mappedBy = "articulo", fetch = FetchType.LAZY,
               cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<StockPorBodega> stocks = new ArrayList<>();
    
    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        this.fechaCreacion = now;
        this.fechaActualizacion = now;
        if (this.activo == null) this.activo = true;
        if (this.requiereLote == null) this.requiereLote = false;
        if (this.requiereVencimiento == null) this.requiereVencimiento = false;
        if (this.esDispositivoMedico == null) this.esDispositivoMedico = false;
        if (this.esInsumoCritico == null) this.esInsumoCritico = false;
        if (this.factorConversion == null) this.factorConversion = 1;
        if (this.stockMinimo == null) this.stockMinimo = 0;
        if (this.stockSeguridad == null) this.stockSeguridad = 0;
    }
    
    @PreUpdate
    public void preUpdate() {
        this.fechaActualizacion = LocalDateTime.now();
    }
}