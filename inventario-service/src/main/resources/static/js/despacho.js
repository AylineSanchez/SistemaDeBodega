/* ============================================================
   DESPACHO - Sistema Bodega DSM (con FEFO, Cámara QR,
   Centros de Costo y Nuevo Pedido para Enc. Dependencia)
   ============================================================ */

const API = '/api';

if (!window.requireAuth()) throw new Error('Sin autenticación');
Layout.init('despacho');

const token = window.getToken();
const usuario = window.getUsuario();

// ⚡ Permisos según rol
const puedeCrearDespacho = ['JEFATURA', 'OPERATIVO'].includes(usuario.rol);
const puedeVerHistorial = ['JEFATURA', 'OPERATIVO', 'AUDITOR'].includes(usuario.rol);
const esEncargadoDependencia = usuario.rol === 'ENCARGADO_DEPENDENCIA';

let state = {
    tabActiva: 'pedidos',
    centrosCosto: [],
    bodegas: [],
    pedidos: [],
    productosDespacho: [],
    productoActual: null,
    lotesSugeridos: [],
    pedidoPreparando: null,
    historial: {
        page: 0, size: 20, totalPages: 0, totalElements: 0,
        q: '', estado: ''
    },
    qrScanner: null,
    qrScannerActivo: false
};

// Cache de centros para gestión
let centrosCostoCache = [];

// ⚡ Estado del nuevo pedido
let productosPedido = [];
let productoPedidoActual = null;

document.addEventListener('DOMContentLoaded', async () => {
    aplicarPermisosUI();

    if (esEncargadoDependencia) {
        document.getElementById('infoBannerEncDep').style.display = 'flex';
        await cargarPedidosPendientes();
        configurarBuscadorPedidos();
    } else {
        await Promise.all([
            cargarCentrosCosto(),
            cargarBodegas()
        ]);
        await cargarPedidosPendientes();
        configurarBuscadorProductos();
        configurarBuscadorPedidos();
        configurarQrInput();
        configurarAutoFormatoRut();
        configurarFormCentro();
    }

    configurarBuscadorHistorial();
});

// ============================================================
// PERMISOS
// ============================================================
function aplicarPermisosUI() {
    if (!puedeCrearDespacho) {
        const tabNuevo = document.getElementById('tabNuevo');
        if (tabNuevo) tabNuevo.style.display = 'none';

        const btnGestion = document.getElementById('btnGestionCentros');
        if (btnGestion) btnGestion.style.display = 'none';
    }

    if (!puedeVerHistorial) {
        const tabHistorial = document.getElementById('tabHistorial');
        if (tabHistorial) tabHistorial.style.display = 'none';
    }

    // ⚡ Enc. Dependencia: mostrar "Nuevo Pedido" y renombrar "Pedidos Pendientes"
    if (esEncargadoDependencia) {
        const tabNuevoPedido = document.getElementById('tabNuevoPedido');
        if (tabNuevoPedido) tabNuevoPedido.style.display = 'block';

        const tabPedidos = document.getElementById('tabPedidos');
        if (tabPedidos) tabPedidos.textContent = '📋 Mis Pedidos';
    }
}

// ============================================================
// AUTO-FORMATO DE RUT
// ============================================================
function configurarAutoFormatoRut() {
    const input = document.getElementById('receptorRut');
    if (!input) return;

    input.addEventListener('input', (e) => {
        const cursorPos = e.target.selectionStart;
        const valorAntes = e.target.value;
        const valorFormateado = formatearRut(valorAntes);

        if (valorFormateado !== valorAntes) {
            e.target.value = valorFormateado;
            const delta = valorFormateado.length - valorAntes.length;
            const nuevaPos = Math.max(0, cursorPos + delta);
            e.target.setSelectionRange(nuevaPos, nuevaPos);
        }
    });

    input.addEventListener('blur', (e) => {
        e.target.value = formatearRut(e.target.value);
    });
}

function formatearRut(valor) {
    if (!valor) return '';
    const limpio = valor.replace(/[^0-9kK]/g, '').toUpperCase();
    if (!limpio || limpio.length <= 1) return limpio;
    const cuerpo = limpio.slice(0, -1);
    const dv = limpio.slice(-1);
    const cuerpoFormateado = cuerpo.replace(/\B(?=(\d{3})+(?!\d))/g, '.');
    return `${cuerpoFormateado}-${dv}`;
}

// ============================================================
// INPUT DE QR (LECTOR FÍSICO)
// ============================================================
function configurarQrInput() {
    const input = document.getElementById('qrInput');
    if (!input) return;

    input.addEventListener('keypress', (e) => {
        if (e.key === 'Enter') {
            e.preventDefault();
            buscarPorQr();
        }
    });
}

async function buscarPorQr() {
    const input = document.getElementById('qrInput');
    const codigo = input.value.trim();

    if (!codigo) {
        Toast.warning('Escanee o escriba un código QR', 'Código vacío');
        return;
    }

    await procesarCodigoQr(codigo);
    input.value = '';
    input.focus();
}

// ============================================================
// CÁMARA QR (html5-qrcode)
// ============================================================
function abrirCamaraQr() {
    if (typeof Html5Qrcode === 'undefined') {
        Toast.error(
            'No se pudo cargar la librería de cámara. Verifique su conexión a internet.',
            'Error de librería'
        );
        return;
    }

    if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
        Toast.error(
            'Su navegador no soporta el acceso a la cámara. Use un lector QR físico o actualice su navegador.',
            'Cámara no disponible'
        );
        return;
    }

    const modal = document.getElementById('modalQrCamera');
    const reader = document.getElementById('qrReader');

    modal.classList.add('active');

    reader.innerHTML = `
        <div class="qr-camera-loading">
            <div class="spinner-qr"></div>
            <p>Iniciando cámara...</p>
        </div>
    `;

    const config = {
        fps: 10,
        qrbox: { width: 250, height: 250 },
        aspectRatio: 1.0,
        disableFlip: false,
        formatsToSupport: [Html5QrcodeSupportedFormats.QR_CODE]
    };

    state.qrScanner = new Html5Qrcode('qrReader', { verbose: false });

    Html5Qrcode.getCameras()
        .then(cameras => {
            if (!cameras || cameras.length === 0) {
                throw new Error('No se encontraron cámaras disponibles');
            }

            let camaraId = cameras[0].id;
            const camaraTrasera = cameras.find(c =>
                c.label.toLowerCase().includes('back') ||
                c.label.toLowerCase().includes('trasera') ||
                c.label.toLowerCase().includes('rear')
            );
            if (camaraTrasera) camaraId = camaraTrasera.id;

            return state.qrScanner.start(
                camaraId,
                config,
                onQrDetectado,
                onQrError
            );
        })
        .then(() => {
            state.qrScannerActivo = true;
        })
        .catch(err => {
            console.error('Error al iniciar cámara:', err);
            mostrarErrorCamara(reader, err.message || 'No se pudo acceder a la cámara');
        });
}

function onQrDetectado(textoDecodificado) {
    if (!state.qrScannerActivo) return;
    state.qrScannerActivo = false;

    if (navigator.vibrate) navigator.vibrate(150);

    reproducirBeep();

    cerrarCamaraQr().then(() => {
        setTimeout(() => {
            procesarCodigoQr(textoDecodificado);
        }, 300);
    });
}

function onQrError(err) {
    if (err && !err.includes('NotFoundException') && !err.includes('No MultiFormat Readers')) {
        console.warn('Error escaneando QR:', err);
    }
}

async function cerrarCamaraQr() {
    const modal = document.getElementById('modalQrCamera');

    if (state.qrScanner) {
        try {
            if (state.qrScannerActivo) {
                await state.qrScanner.stop();
            }
            state.qrScanner.clear();
        } catch (e) {
            console.warn('Error al cerrar scanner:', e);
        }
        state.qrScanner = null;
    }

    state.qrScannerActivo = false;
    modal.classList.remove('active');

    const reader = document.getElementById('qrReader');
    if (reader) reader.innerHTML = '';
}

function mostrarErrorCamara(reader, mensaje) {
    reader.innerHTML = `
        <div class="qr-camera-error">
            <div class="icon">📷❌</div>
            <h4>No se pudo acceder a la cámara</h4>
            <p>${escapeHtml(mensaje)}</p>
            <p style="margin-top:15px;font-size:0.85em;">
                <strong>Posibles causas:</strong><br>
                • La cámara está siendo usada por otra aplicación<br>
                • No diste permiso para acceder a la cámara<br>
                • Tu dispositivo no tiene cámara disponible
            </p>
        </div>
    `;
}

function reproducirBeep() {
    try {
        const AudioContext = window.AudioContext || window.webkitAudioContext;
        if (!AudioContext) return;

        const audioCtx = new AudioContext();
        const oscillator = audioCtx.createOscillator();
        const gainNode = audioCtx.createGain();

        oscillator.connect(gainNode);
        gainNode.connect(audioCtx.destination);

        oscillator.frequency.value = 1200;
        oscillator.type = 'sine';

        gainNode.gain.setValueAtTime(0.3, audioCtx.currentTime);
        gainNode.gain.exponentialRampToValueAtTime(0.01, audioCtx.currentTime + 0.15);

        oscillator.start();
        oscillator.stop(audioCtx.currentTime + 0.15);
    } catch (e) { /* silencioso */ }
}

// ============================================================
// PROCESAR CÓDIGO QR
// ============================================================
async function procesarCodigoQr(codigo) {
    mostrarLoading(true);
    try {
        const res = await fetch(`${API}/qr/info/${encodeURIComponent(codigo)}`, {
            headers: { 'Authorization': `Bearer ${token}` }
        });
        const data = await res.json();

        if (!res.ok || !data.success) {
            Toast.error(
                data.message || `Código QR no encontrado: ${codigo}`,
                'Producto no encontrado'
            );
            return;
        }

        const qrInfo = data.data;

        const bodegaId = document.getElementById('bodegaId').value;
        if (!bodegaId) {
            Toast.warning(
                'Seleccione primero la Bodega de Origen antes de escanear',
                'Bodega requerida'
            );
            return;
        }

        if (qrInfo.stockActual <= 0) {
            Toast.warning(
                `El producto "${qrInfo.nombreArticulo}" no tiene stock disponible en esta bodega`,
                'Sin stock'
            );
            return;
        }

        const resArt = await fetch(`${API}/articulos/${qrInfo.articuloId}`, {
            headers: { 'Authorization': `Bearer ${token}` }
        });
        const dataArt = await resArt.json();

        if (!dataArt.success) {
            Toast.error('No se pudo cargar la información del producto', 'Error');
            return;
        }

        Toast.success(
            `${qrInfo.nombreArticulo} (stock: ${qrInfo.stockActual})`,
            '✓ Producto encontrado'
        );

        abrirModalProducto(dataArt.data, 1);

    } catch (e) {
        console.error(e);
        Toast.error('Error al buscar el código QR', 'Error');
    } finally {
        mostrarLoading(false);
    }
}

// ============================================================
// CARGAR CENTROS Y BODEGAS
// ============================================================
async function cargarCentrosCosto() {
    try {
        const res = await fetch(`${API}/centros-costo`, {
            headers: { 'Authorization': `Bearer ${token}` }
        });
        const data = await res.json();
        if (data.success) {
            state.centrosCosto = data.data;
            const select = document.getElementById('centroCostoId');
            state.centrosCosto.forEach(c => {
                const opt = document.createElement('option');
                opt.value = c.id;
                opt.textContent = `${c.codigo} - ${c.nombre}`;
                select.appendChild(opt);
            });
        }
    } catch (e) { console.error(e); }
}

async function cargarBodegas() {
    try {
        const res = await fetch(`${API}/bodegas`, {
            headers: { 'Authorization': `Bearer ${token}` }
        });
        const data = await res.json();
        if (data.success) {
            state.bodegas = data.data;
            const select = document.getElementById('bodegaId');
            state.bodegas.forEach(b => {
                const opt = document.createElement('option');
                opt.value = b.id;
                opt.textContent = `${b.codigo} - ${b.nombre}`;
                select.appendChild(opt);
            });
        }
    } catch (e) { console.error(e); }
}

// ============================================================
// TABS
// ============================================================
function cambiarTab(tab) {
    state.tabActiva = tab;

    document.querySelectorAll('.tab').forEach(t => t.classList.remove('active'));

    const tabIds = {
        'pedidos': 'tabPedidos',
        'nuevopedido': 'tabNuevoPedido',
        'nuevo': 'tabNuevo',
        'historial': 'tabHistorial'
    };
    const tabBtn = document.getElementById(tabIds[tab]);
    if (tabBtn) tabBtn.classList.add('active');

    document.getElementById('contenidoPedidos').style.display = tab === 'pedidos' ? 'block' : 'none';

    const contenidoNuevoPedido = document.getElementById('contenidoNuevoPedido');
    if (contenidoNuevoPedido) {
        contenidoNuevoPedido.style.display = tab === 'nuevopedido' ? 'block' : 'none';
    }

    document.getElementById('contenidoNuevo').style.display = tab === 'nuevo' ? 'block' : 'none';
    document.getElementById('contenidoHistorial').style.display = tab === 'historial' ? 'block' : 'none';

    if (tab === 'pedidos') cargarPedidosPendientes();
    if (tab === 'nuevopedido') inicializarNuevoPedido();
    if (tab === 'historial') cargarHistorial();
}

// ============================================================
// PEDIDOS PENDIENTES
// ============================================================
async function cargarPedidosPendientes() {
    try {
        const res = await fetch(`${API}/pedidos?estado=PENDIENTE&size=100`, {
            headers: { 'Authorization': `Bearer ${token}` }
        });
        const data = await res.json();

        if (!data.success) return;

        state.pedidos = data.data.content;
        renderizarPedidos();
    } catch (e) {
        console.error(e);
    }
}

function renderizarPedidos() {
    const container = document.getElementById('listaPedidos');
    if (!container) return;

    const prioridadFilter = document.getElementById('prioridadFilter')?.value || '';
    const search = (document.getElementById('searchPedidos')?.value || '').toLowerCase();

    let filtrados = state.pedidos.filter(p => {
        if (prioridadFilter && p.prioridad !== prioridadFilter) return false;
        if (search) {
            const q = search;
            if (!p.numeroPedido.toLowerCase().includes(q) &&
                !p.centroNombre.toLowerCase().includes(q) &&
                !p.solicitanteNombre.toLowerCase().includes(q)) return false;
        }
        return true;
    });

    if (filtrados.length === 0) {
        container.innerHTML = `
            <div style="text-align:center;padding:40px;color:#95a5a6;background:white;border-radius:12px;">
                <div style="font-size:3em;">📭</div>
                <h3>No hay pedidos pendientes</h3>
                <p style="font-size:0.9em;">Prueba con otros filtros</p>
            </div>
        `;
        return;
    }

    container.innerHTML = filtrados.map(p => `
        <div class="pedido-card ${p.prioridad === 'URGENTE' ? 'urgente' : ''}">
            <div class="pedido-info">
                <span class="pedido-numero">${p.numeroPedido}</span>
                <span><strong>${escapeHtml(p.centroNombre)}</strong></span>
                <span style="color:#7f8c8d;font-size:0.9em;">${escapeHtml(p.solicitanteNombre)}</span>
                <span style="color:#95a5a6;font-size:0.85em;">
                    📅 ${formatFecha(p.fechaSolicitud)}
                </span>
                <span class="badge-prioridad ${p.prioridad.toLowerCase()}">
                    ${p.prioridad === 'URGENTE' ? '🔴 Urgente' : '🟢 Normal'}
                </span>
                <span style="font-size:0.85em;color:#555;">
                    ${p.totalLineas} líneas · ${p.totalUnidadesSolicitadas} unidades
                </span>
            </div>
            <div>
                ${puedeCrearDespacho
                    ? `<button class="btn btn-primary" onclick="prepararPedido(${p.id})">
                         📤 Preparar
                       </button>`
                    : `<span style="color:#95a5a6;font-size:0.85em;">Solo lectura</span>`
                }
            </div>
        </div>
    `).join('');
}

function configurarBuscadorPedidos() {
    document.getElementById('searchPedidos')?.addEventListener('input', debounce(() => {
        renderizarPedidos();
    }, 300));

    document.getElementById('prioridadFilter')?.addEventListener('change', () => {
        renderizarPedidos();
    });
}

async function prepararPedido(id) {
    try {
        const res = await fetch(`${API}/pedidos/${id}`, {
            headers: { 'Authorization': `Bearer ${token}` }
        });
        const data = await res.json();

        if (!data.success) {
            Toast.error('No se pudo cargar el pedido', 'Error');
            return;
        }

        state.pedidoPreparando = data.data;
        state.productosDespacho = [];

        document.getElementById('prepararTitle').textContent =
            `Preparar Pedido ${data.data.numeroPedido}`;

        const content = document.getElementById('prepararContent');
        content.innerHTML = `
            <div class="card-form" style="margin-bottom:15px;">
                <h3>📋 Pedido a Preparar</h3>
                <div class="form-row">
                    <div><strong>Centro:</strong> ${escapeHtml(data.data.centroNombre)}</div>
                    <div><strong>Solicitante:</strong> ${escapeHtml(data.data.solicitanteNombre)}</div>
                    <div><strong>Prioridad:</strong>
                        <span class="badge-prioridad ${data.data.prioridad.toLowerCase()}">
                            ${data.data.prioridad}
                        </span>
                    </div>
                </div>
            </div>

            <div class="card-form">
                <h3>📦 Productos Solicitados</h3>
                <table class="product-table">
                    <thead>
                        <tr>
                            <th>SKU</th>
                            <th>Producto</th>
                            <th>Solicitado</th>
                            <th>Despachado</th>
                            <th style="text-align:right;">Acción</th>
                        </tr>
                    </thead>
                    <tbody>
                        ${data.data.detalles.map((d, i) => `
                            <tr>
                                <td><span class="sku-cell">${d.sku}</span></td>
                                <td>${escapeHtml(d.nombreProducto)}</td>
                                <td><strong>${d.cantidadSolicitada}</strong> ${d.unidadMedidaBase}</td>
                                <td>${d.cantidadDespachada}</td>
                                <td style="text-align:right;">
                                    <button class="btn btn-sm btn-primary"
                                            onclick="abrirProductoDesdePedido(${i})">
                                        Agregar
                                    </button>
                                </td>
                            </tr>
                        `).join('')}
                    </tbody>
                </table>
            </div>
        `;

        document.getElementById('modalPreparar').classList.add('active');
    } catch (e) {
        console.error(e);
    }
}

function abrirProductoDesdePedido(idx) {
    const detalle = state.pedidoPreparando.detalles[idx];
    fetch(`${API}/articulos/${detalle.articuloId}`, {
        headers: { 'Authorization': `Bearer ${token}` }
    })
    .then(r => r.json())
    .then(data => {
        if (data.success) {
            abrirModalProducto(data.data, detalle.cantidadSolicitada);
        }
    });
}

function cerrarModalPreparar() {
    document.getElementById('modalPreparar').classList.remove('active');
    state.pedidoPreparando = null;
}

function generarDespachoDesdePedido() {
    if (!state.pedidoPreparando || state.productosDespacho.length === 0) {
        Toast.warning('Agregue al menos un producto', 'Sin productos');
        return;
    }

    cambiarTab('nuevo');

    document.getElementById('centroCostoId').value = state.pedidoPreparando.centroCostoId;
    document.getElementById('receptorNombre').value = state.pedidoPreparando.solicitanteNombre;
    document.getElementById('receptorRut').value = state.pedidoPreparando.solicitanteRut;
    document.getElementById('receptorCargo').value = state.pedidoPreparando.solicitanteCargo || '';

    renderizarProductosDespacho();
    cerrarModalPreparar();

    Toast.info('Productos cargados. Complete el despacho.', 'Pedido preparado');
}

// ============================================================
// NUEVO PEDIDO (Enc. Dependencia)
// ============================================================
function inicializarNuevoPedido() {
    // Pre-cargar datos del usuario actual
    const usuario = window.getUsuario();

    document.getElementById('pedidoSolicitanteNombre').value = usuario.nombreCompleto || '';
    document.getElementById('pedidoSolicitanteRut').value = usuario.rut || '';

    // Cargar centros de costo (solo activos)
    cargarCentrosCostoPedido();

    // Configurar buscador de productos
    configurarBuscadorProductosPedido();

    // Configurar auto-formato de RUT
    configurarAutoFormatoRutPedido();

    // Resetear lista
    productosPedido = [];
    renderizarProductosPedido();
}

async function cargarCentrosCostoPedido() {
    try {
        const res = await fetch(`${API}/centros-costo`, {
            headers: { 'Authorization': `Bearer ${token}` }
        });
        const data = await res.json();

        if (!data.success) return;

        const select = document.getElementById('pedidoCentroCostoId');
        select.innerHTML = '<option value="">Seleccione...</option>';

        data.data.forEach(c => {
            const opt = document.createElement('option');
            opt.value = c.id;
            opt.textContent = `${c.codigo} - ${c.nombre}`;
            select.appendChild(opt);
        });

        // Si el usuario tiene dependencia, pre-seleccionar
        const usuario = window.getUsuario();
        if (usuario.dependencia) {
            const match = data.data.find(c => c.nombre.toLowerCase().includes(usuario.dependencia.toLowerCase()));
            if (match) select.value = match.id;
        }

    } catch (e) { console.error(e); }
}

function configurarBuscadorProductosPedido() {
    const input = document.getElementById('pedidoProductoSearch');
    const suggestions = document.getElementById('pedidoProductoSuggestions');

    if (!input) return;

    input.addEventListener('input', debounce(async (e) => {
        const q = e.target.value.trim();

        if (q.length < 2) {
            suggestions.classList.remove('active');
            return;
        }

        const res = await fetch(`${API}/articulos?q=${encodeURIComponent(q)}&size=15`, {
            headers: { 'Authorization': `Bearer ${token}` }
        });
        const data = await res.json();

        if (!data.success) return;

        // ⚡ Solo mostrar productos con stock > 0
        const productos = data.data.content.filter(p => (p.stockTotal || 0) > 0);

        if (productos.length === 0) {
            suggestions.innerHTML = `<div class="producto-option" style="color:#95a5a6;">No hay productos con stock disponible</div>`;
        } else {
            suggestions.innerHTML = productos.map(p => `
                <div class="producto-option" onclick="seleccionarProductoPedido(${p.id})">
                    <div><span class="sku">${p.sku}</span> — ${escapeHtml(p.nombreTecnico)}</div>
                    <div style="font-size:0.8em;color:#7f8c8d;margin-top:3px;">
                        ${p.categoriaCodigo} · Stock disponible: <strong>${p.stockTotal}</strong> ${p.unidadMedidaBase}
                    </div>
                </div>
            `).join('');
        }

        suggestions.classList.add('active');
        window._productosPedidoResultado = productos;

    }, 300));

    document.addEventListener('click', (e) => {
        if (!e.target.closest('#pedidoProductoSearch') && !e.target.closest('#pedidoProductoSuggestions')) {
            suggestions.classList.remove('active');
        }
    });
}

function seleccionarProductoPedido(id) {
    const productos = window._productosPedidoResultado || [];
    const articulo = productos.find(p => p.id === id);
    if (!articulo) return;

    document.getElementById('pedidoProductoSuggestions').classList.remove('active');
    document.getElementById('pedidoProductoSearch').value = '';

    productoPedidoActual = articulo;

    document.getElementById('pedProdSku').value = articulo.sku;
    document.getElementById('pedProdNombre').value = articulo.nombreTecnico;
    document.getElementById('pedProdStock').value = `${articulo.stockTotal} ${articulo.unidadMedidaBase}`;
    document.getElementById('pedProdCantidad').value = 1;
    document.getElementById('pedProdCantidad').max = articulo.stockTotal;
    document.getElementById('pedProdObservaciones').value = '';

    document.getElementById('modalCantidadPedido').classList.add('active');
    setTimeout(() => document.getElementById('pedProdCantidad').focus(), 150);
}

function cerrarModalCantidadPedido() {
    document.getElementById('modalCantidadPedido').classList.remove('active');
    productoPedidoActual = null;
}

function confirmarProductoPedido() {
    if (!productoPedidoActual) return;

    const cantidad = parseInt(document.getElementById('pedProdCantidad').value);

    if (!cantidad || cantidad < 1) {
        Toast.warning('Ingrese una cantidad válida', 'Cantidad inválida');
        return;
    }

    if (cantidad > productoPedidoActual.stockTotal) {
        Toast.error(
            `Solo hay ${productoPedidoActual.stockTotal} ${productoPedidoActual.unidadMedidaBase} disponibles`,
            'Cantidad excede el stock'
        );
        return;
    }

    const existente = productosPedido.findIndex(p => p.articuloId === productoPedidoActual.id);

    if (existente >= 0) {
        productosPedido[existente].cantidadSolicitada += cantidad;
        if (productosPedido[existente].cantidadSolicitada > productoPedidoActual.stockTotal) {
            productosPedido[existente].cantidadSolicitada = productoPedidoActual.stockTotal;
            Toast.warning('Ajustado al stock máximo disponible', 'Cantidad ajustada');
        }
    } else {
        productosPedido.push({
            articuloId: productoPedidoActual.id,
            sku: productoPedidoActual.sku,
            nombreProducto: productoPedidoActual.nombreTecnico,
            unidadMedidaBase: productoPedidoActual.unidadMedidaBase,
            categoriaCodigo: productoPedidoActual.categoriaCodigo,
            stockDisponible: productoPedidoActual.stockTotal,
            cantidadSolicitada: cantidad,
            observaciones: document.getElementById('pedProdObservaciones').value.trim() || null
        });
    }

    cerrarModalCantidadPedido();
    renderizarProductosPedido();

    Toast.success(`${productoPedidoActual.nombreTecnico} agregado al pedido`, '✓ Agregado');
}

function renderizarProductosPedido() {
    const container = document.getElementById('productosPedido');
    if (!container) return;

    if (productosPedido.length === 0) {
        container.innerHTML = `
            <div style="text-align:center;padding:30px;color:#95a5a6;">
                <div style="font-size:2em;margin-bottom:8px;">📦</div>
                <p>No hay productos agregados</p>
                <p style="font-size:0.85em;">Use el buscador para agregar productos</p>
            </div>
        `;
        return;
    }

    container.innerHTML = productosPedido.map((p, idx) => `
        <div class="producto-despacho-row">
            <div>
                <span class="sku">${escapeHtml(p.sku)}</span>
                <div class="nombre">${escapeHtml(p.nombreProducto)}</div>
            </div>
            <div style="text-align:center;">
                <strong>${p.cantidadSolicitada}</strong> ${p.unidadMedidaBase}
            </div>
            <div class="lote-info">
                Stock disponible: ${p.stockDisponible}
            </div>
            <div style="text-align:center;font-size:0.85em;color:#7f8c8d;">
                ${p.observaciones ? escapeHtml(p.observaciones) : '—'}
            </div>
            <div>
                <button class="btn-icon delete" onclick="eliminarProductoPedido(${idx})" title="Eliminar">
                    🗑️
                </button>
            </div>
        </div>
    `).join('');
}

function eliminarProductoPedido(idx) {
    productosPedido.splice(idx, 1);
    renderizarProductosPedido();
    Toast.info('Producto eliminado', 'Actualizado');
}

async function guardarPedido() {
    const centroCostoId = document.getElementById('pedidoCentroCostoId').value;
    const prioridad = document.getElementById('pedidoPrioridad').value;
    const solicitanteNombre = document.getElementById('pedidoSolicitanteNombre').value.trim();
    const solicitanteRut = document.getElementById('pedidoSolicitanteRut').value.trim();
    const solicitanteCargo = document.getElementById('pedidoSolicitanteCargo').value.trim();

    if (!centroCostoId) {
        Toast.warning('Seleccione el centro de costo', 'Campo requerido');
        return;
    }
    if (!solicitanteNombre || !solicitanteRut) {
        Toast.warning('Complete los datos del solicitante', 'Campos requeridos');
        return;
    }
    if (productosPedido.length === 0) {
        Toast.warning('Agregue al menos un producto al pedido', 'Sin productos');
        return;
    }

    const payload = {
        centroCostoId: parseInt(centroCostoId),
        solicitanteNombre,
        solicitanteRut,
        solicitanteCargo: solicitanteCargo || null,
        prioridad,
        fechaRequerida: document.getElementById('pedidoFechaRequerida').value || null,
        observaciones: document.getElementById('pedidoObservaciones').value.trim() || null,
        detalles: productosPedido.map(p => ({
            articuloId: p.articuloId,
            cantidadSolicitada: p.cantidadSolicitada,
            observaciones: p.observaciones
        }))
    };

    try {
        const res = await fetch(`${API}/pedidos`, {
            method: 'POST',
            headers: {
                'Authorization': `Bearer ${token}`,
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(payload)
        });

        const data = await res.json();

        if (!res.ok || !data.success) {
            Toast.error(data.message || 'Error al crear pedido', 'Error');
            return;
        }

        Toast.success(
            `Pedido ${data.data.numeroPedido} enviado a bodega`,
            '✓ Pedido creado'
        );

        limpiarFormularioPedido();
        cambiarTab('pedidos');

    } catch (e) {
        console.error(e);
        Toast.error('Error de conexión', 'Error');
    }
}

function limpiarFormularioPedido() {
    document.getElementById('pedidoCentroCostoId').value = '';
    document.getElementById('pedidoPrioridad').value = 'NORMAL';
    document.getElementById('pedidoFechaRequerida').value = '';
    document.getElementById('pedidoSolicitanteCargo').value = '';
    document.getElementById('pedidoObservaciones').value = '';

    productosPedido = [];
    renderizarProductosPedido();
}

function configurarAutoFormatoRutPedido() {
    const input = document.getElementById('pedidoSolicitanteRut');
    if (!input) return;

    input.addEventListener('input', (e) => {
        const cursorPos = e.target.selectionStart;
        const valorAntes = e.target.value;
        const valorFormateado = formatearRut(valorAntes);

        if (valorFormateado !== valorAntes) {
            e.target.value = valorFormateado;
            const delta = valorFormateado.length - valorAntes.length;
            const nuevaPos = Math.max(0, cursorPos + delta);
            e.target.setSelectionRange(nuevaPos, nuevaPos);
        }
    });

    input.addEventListener('blur', (e) => {
        e.target.value = formatearRut(e.target.value);
    });
}

// ============================================================
// BUSCADOR DE PRODUCTOS (para despacho)
// ============================================================
let searchTimeout = null;
let productosResultado = [];

function configurarBuscadorProductos() {
    const input = document.getElementById('productoSearch');
    const suggestions = document.getElementById('productoSuggestions');

    if (!input) return;

    input.addEventListener('input', (e) => {
        clearTimeout(searchTimeout);
        const q = e.target.value.trim();

        if (q.length < 2) {
            suggestions.classList.remove('active');
            return;
        }

        searchTimeout = setTimeout(async () => {
            await buscarProductos(q);
        }, 300);
    });

    document.addEventListener('click', (e) => {
        if (!e.target.closest('.producto-search')) {
            suggestions.classList.remove('active');
        }
    });
}

async function buscarProductos(q) {
    try {
        const res = await fetch(`${API}/articulos?q=${encodeURIComponent(q)}&size=15`, {
            headers: { 'Authorization': `Bearer ${token}` }
        });
        const data = await res.json();

        if (!data.success) return;

        productosResultado = data.data.content;
        const suggestions = document.getElementById('productoSuggestions');

        if (productosResultado.length === 0) {
            suggestions.innerHTML = `<div class="producto-option" style="color:#95a5a6;">No se encontraron productos</div>`;
        } else {
            suggestions.innerHTML = productosResultado.map(p => `
                <div class="producto-option" onclick="seleccionarProducto(${p.id})">
                    <div><span class="sku">${p.sku}</span> — ${escapeHtml(p.nombreTecnico)}</div>
                    <div style="font-size:0.8em;color:#7f8c8d;margin-top:3px;">
                        ${p.categoriaCodigo} · Stock total: ${p.stockTotal} ${p.unidadMedidaBase}
                    </div>
                </div>
            `).join('');
        }

        suggestions.classList.add('active');
    } catch (e) { console.error(e); }
}

function seleccionarProducto(id) {
    const articulo = productosResultado.find(p => p.id === id);
    if (!articulo) return;

    document.getElementById('productoSuggestions').classList.remove('active');
    document.getElementById('productoSearch').value = '';

    abrirModalProducto(articulo);
}

// ============================================================
// MODAL PRODUCTO (para despacho)
// ============================================================
function abrirModalProducto(articulo, cantidadSugerida = 1) {
    state.productoActual = articulo;
    state.lotesSugeridos = [];

    document.getElementById('prodSku').value = articulo.sku;
    document.getElementById('prodNombre').value = articulo.nombreTecnico;
    document.getElementById('prodCantidad').value = cantidadSugerida;
    document.getElementById('prodCantidad').max = articulo.stockTotal;
    document.getElementById('prodStock').value = `${articulo.stockTotal} ${articulo.unidadMedidaBase}`;
    document.getElementById('prodLote').innerHTML = '<option value="">Seleccione bodega primero...</option>';
    document.getElementById('prodVencimiento').value = '';
    document.getElementById('prodDias').value = '';
    document.getElementById('prodObservaciones').value = '';
    document.getElementById('fefoSuggestion').innerHTML = '';

    document.getElementById('modalProducto').classList.add('active');

    const bodegaId = document.getElementById('bodegaId').value;
    if (bodegaId && articulo.requiereLote) {
        cargarLotesFefo(articulo.id, parseInt(bodegaId), cantidadSugerida);
    }
}

function cerrarModalProducto() {
    document.getElementById('modalProducto').classList.remove('active');
    state.productoActual = null;
    state.lotesSugeridos = [];
}

async function cargarLotesFefo(articuloId, bodegaId, cantidad) {
    try {
        const res = await fetch(
            `${API}/despachos/sugerir-lotes?articuloId=${articuloId}&bodegaId=${bodegaId}&cantidad=${cantidad}`,
            { headers: { 'Authorization': `Bearer ${token}` } }
        );
        const data = await res.json();

        if (!data.success) {
            const div = document.getElementById('fefoSuggestion');
            div.innerHTML = `
                <div class="fefo-suggestion danger">
                    ⚠️ <strong>STOCK INSUFICIENTE:</strong> ${data.message}
                </div>
            `;
            document.getElementById('prodLote').innerHTML = '<option value="">Sin lotes disponibles</option>';
            return;
        }

        state.lotesSugeridos = data.data;

        const primerLote = data.data[0];
        const dias = primerLote.diasParaVencer;

        let alertaClass = 'fefo-suggestion';
        let alertaMsg = '';

        if (dias <= 120) {
            alertaClass += ' warning';
            alertaMsg = `⚠️ Lote próximo a vencer (${dias} días). Priorizar despacho.`;
        } else if (dias <= 180) {
            alertaClass += ' warning';
            alertaMsg = `📅 Lote vence en ${dias} días. Considere priorizar.`;
        } else {
            alertaMsg = `✅ FEFO aplicado correctamente. Lote más próximo a vencer seleccionado.`;
        }

        document.getElementById('fefoSuggestion').innerHTML = `
            <div class="${alertaClass}">
                ${alertaMsg}
                <br>
                <small>FEFO: Primer lote = <strong>${primerLote.numeroLote}</strong>
                (vence ${formatFecha(primerLote.fechaVencimiento)})</small>
            </div>
        `;

        const select = document.getElementById('prodLote');
        select.innerHTML = data.data.map((l, i) => `
            <option value="${l.loteId}" ${i === 0 ? 'selected' : ''}>
                ${l.numeroLote} — vence ${formatFecha(l.fechaVencimiento)} (${l.diasParaVencer} días)
                — Disponible: ${l.cantidadDisponible}
            </option>
        `).join('');

        actualizarInfoLote();
        select.addEventListener('change', actualizarInfoLote);

    } catch (e) {
        console.error(e);
    }
}

function actualizarInfoLote() {
    const loteId = parseInt(document.getElementById('prodLote').value);
    const lote = state.lotesSugeridos.find(l => l.loteId === loteId);

    if (lote) {
        document.getElementById('prodVencimiento').value = formatFecha(lote.fechaVencimiento);
        document.getElementById('prodDias').value = lote.diasParaVencer + ' días';
    }
}

function confirmarProducto() {
    const articulo = state.productoActual;
    if (!articulo) return;

    const cantidad = parseInt(document.getElementById('prodCantidad').value);
    const loteId = document.getElementById('prodLote').value;
    const lote = state.lotesSugeridos.find(l => l.loteId === parseInt(loteId));

    if (!cantidad || cantidad < 1) {
        Toast.warning('Ingrese una cantidad válida', 'Cantidad inválida');
        return;
    }

    if (cantidad > articulo.stockTotal) {
        Toast.error(
            `Solo hay ${articulo.stockTotal} ${articulo.unidadMedidaBase} disponibles.`,
            'Cantidad excede el stock'
        );
        document.getElementById('prodCantidad').focus();
        return;
    }

    if (articulo.requiereLote && state.lotesSugeridos.length > 0) {
        const loteFefo = state.lotesSugeridos[0];
        if (lote && lote.loteId !== loteFefo.loteId) {
            Toast.error(
                `BLOQUEO DE DESPACHO (FEFO): Debe despachar primero el lote ${loteFefo.numeroLote} ` +
                `que vence el ${formatFecha(loteFefo.fechaVencimiento)}.`,
                'Bloqueo FEFO'
            );
            return;
        }
    }

    const detalle = {
        articuloId: articulo.id,
        sku: articulo.sku,
        nombreProducto: articulo.nombreTecnico,
        categoriaCodigo: articulo.categoriaCodigo,
        unidadMedidaBase: articulo.unidadMedidaBase,
        requiereLote: articulo.requiereLote,
        cantidad: cantidad,
        loteId: lote ? lote.loteId : null,
        numeroLote: lote ? lote.numeroLote : null,
        fechaVencimiento: lote ? lote.fechaVencimiento : null,
        observaciones: document.getElementById('prodObservaciones').value.trim() || null
    };

    state.productosDespacho.push(detalle);
    cerrarModalProducto();
    renderizarProductosDespacho();

    Toast.success(`${articulo.nombreTecnico} agregado`, '✓ Agregado');
}

function renderizarProductosDespacho() {
    const container = document.getElementById('productosDespacho');
    if (!container) return;

    if (state.productosDespacho.length === 0) {
        container.innerHTML = `
            <div style="text-align:center;padding:30px;color:#95a5a6;">
                <div style="font-size:2em;margin-bottom:8px;">📦</div>
                <p>No hay productos agregados</p>
            </div>
        `;
        return;
    }

    container.innerHTML = state.productosDespacho.map((d, idx) => `
        <div class="producto-despacho-row">
            <div>
                <span class="sku">${d.sku}</span>
                <div class="nombre">${escapeHtml(d.nombreProducto)}</div>
            </div>
            <div style="text-align:center;">
                <strong>${d.cantidad}</strong> ${d.unidadMedidaBase}
            </div>
            <div class="lote-info">
                ${d.numeroLote ? `Lote: ${d.numeroLote}` : '—'}
                ${d.fechaVencimiento ? `<br>Vence: ${formatFecha(d.fechaVencimiento)}` : ''}
            </div>
            <div style="text-align:center;font-size:0.85em;color:#7f8c8d;">
                ${d.loteId ? '🔒 FEFO OK' : 'Sin lote'}
            </div>
            <div>
                <button class="btn-icon delete" onclick="eliminarProductoDespacho(${idx})" title="Eliminar">
                    🗑️
                </button>
            </div>
        </div>
    `).join('');
}

function eliminarProductoDespacho(idx) {
    state.productosDespacho.splice(idx, 1);
    renderizarProductosDespacho();
    Toast.info('Producto eliminado', 'Actualizado');
}

// ============================================================
// GUARDAR DESPACHO
// ============================================================
async function guardarDespacho(confirmar) {
    const centroCostoId = document.getElementById('centroCostoId').value;
    const bodegaId = document.getElementById('bodegaId').value;
    const receptorNombre = document.getElementById('receptorNombre').value.trim();
    const receptorRut = document.getElementById('receptorRut').value.trim();

    if (!centroCostoId) {
        Toast.warning('Seleccione el centro de costo', 'Campo requerido');
        return;
    }
    if (!bodegaId) {
        Toast.warning('Seleccione la bodega de origen', 'Campo requerido');
        return;
    }
    if (!receptorNombre || !receptorRut) {
        Toast.warning('Complete los datos del receptor', 'Campos requeridos');
        return;
    }
    if (state.productosDespacho.length === 0) {
        Toast.warning('Agregue al menos un producto', 'Sin productos');
        return;
    }

    const payload = {
        pedidoId: state.pedidoPreparando?.id || null,
        centroCostoId: parseInt(centroCostoId),
        bodegaId: parseInt(bodegaId),
        receptorNombre,
        receptorRut,
        receptorCargo: document.getElementById('receptorCargo').value.trim() || null,
        guiaInterna: document.getElementById('guiaInterna').value.trim() || null,
        observaciones: document.getElementById('observaciones').value.trim() || null,
        detalles: state.productosDespacho.map(d => ({
            articuloId: d.articuloId,
            cantidad: d.cantidad,
            loteId: d.loteId,
            observaciones: d.observaciones
        }))
    };

    try {
        const res = await fetch(`${API}/despachos`, {
            method: 'POST',
            headers: {
                'Authorization': `Bearer ${token}`,
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(payload)
        });

        const data = await res.json();

        if (!res.ok || !data.success) {
            Toast.error(data.message || 'Error al crear despacho', 'Error');
            return;
        }

        const despacho = data.data;
        Toast.success(`Despacho ${despacho.numeroDespacho} guardado`, '✓ Guardado');

        if (confirmar) {
            const resConf = await fetch(`${API}/despachos/${despacho.id}/confirmar`, {
                method: 'POST',
                headers: { 'Authorization': `Bearer ${token}` }
            });

            const dataConf = await resConf.json();

            if (!resConf.ok || !dataConf.success) {
                Toast.error(dataConf.message || 'Error al confirmar', 'Error');
                return;
            }

            Toast.success('Despacho confirmado. Stock actualizado.', '✓ Confirmado');
        }

        limpiarFormularioDespacho();
        cambiarTab('pedidos');

    } catch (e) {
        console.error(e);
        Toast.error('Error de conexión', 'Error');
    }
}

function limpiarFormularioDespacho() {
    document.getElementById('centroCostoId').value = '';
    document.getElementById('bodegaId').value = '';
    document.getElementById('guiaInterna').value = '';
    document.getElementById('receptorNombre').value = '';
    document.getElementById('receptorRut').value = '';
    document.getElementById('receptorCargo').value = '';
    document.getElementById('observaciones').value = '';

    state.productosDespacho = [];
    state.pedidoPreparando = null;
    renderizarProductosDespacho();
}

// ============================================================
// HISTORIAL
// ============================================================
function configurarBuscadorHistorial() {
    document.getElementById('searchHistorial')?.addEventListener('input', debounce((e) => {
        state.historial.q = e.target.value.trim();
        state.historial.page = 0;
        cargarHistorial();
    }, 400));

    document.getElementById('estadoFilter')?.addEventListener('change', (e) => {
        state.historial.estado = e.target.value;
        state.historial.page = 0;
        cargarHistorial();
    });
}

async function cargarHistorial() {
    if (!puedeVerHistorial) return;

    mostrarLoading(true);
    try {
        const params = new URLSearchParams({
            page: state.historial.page,
            size: state.historial.size
        });
        if (state.historial.q) params.append('q', state.historial.q);
        if (state.historial.estado) params.append('estado', state.historial.estado);

        const res = await fetch(`${API}/despachos?${params}`, {
            headers: { 'Authorization': `Bearer ${token}` }
        });
        const data = await res.json();

        if (!data.success) return;

        state.historial.totalPages = data.data.totalPages;
        state.historial.totalElements = data.data.totalElements;

        renderizarHistorial(data.data.content);
        renderizarPaginacionHistorial();
    } catch (e) {
        console.error(e);
    } finally {
        mostrarLoading(false);
    }
}

function renderizarHistorial(despachos) {
    const tbody = document.getElementById('historialBody');
    if (!tbody) return;

    if (!despachos || despachos.length === 0) {
        tbody.innerHTML = `
            <tr>
                <td colspan="8">
                    <div class="empty-state">
                        <div class="icon">📭</div>
                        <h3>No hay despachos</h3>
                    </div>
                </td>
            </tr>
        `;
        return;
    }

    tbody.innerHTML = despachos.map(d => `
        <tr>
            <td><span class="sku-cell">${d.numeroDespacho}</span></td>
            <td>
                <strong>${escapeHtml(d.centroNombre)}</strong>
                <small style="display:block;color:#7f8c8d;">${d.centroTipo || ''}</small>
            </td>
            <td>
                ${escapeHtml(d.receptorNombre)}
                <small style="display:block;color:#7f8c8d;">${d.receptorRut}</small>
            </td>
            <td><span class="badge">${d.bodegaCodigo}</span></td>
            <td>${formatFecha(d.fechaDespacho)}</td>
            <td style="text-align:center;">${d.totalLineas}</td>
            <td>
                <span class="estado-badge estado-${d.estado}">
                    ${getEstadoIcon(d.estado)} ${d.estado}
                </span>
            </td>
            <td class="actions-cell">
                <button class="btn-icon view" onclick="verDetalle(${d.id})" title="Ver">👁️</button>
            </td>
        </tr>
    `).join('');
}

function getEstadoIcon(estado) {
    switch (estado) {
        case 'BORRADOR': return '📝';
        case 'CONFIRMADO': return '✅';
        case 'ANULADO': return '❌';
        default: return '•';
    }
}

function renderizarPaginacionHistorial() {
    const info = document.getElementById('historialInfo');
    const botones = document.getElementById('historialPagination');
    if (!info || !botones) return;

    const page = state.historial.page;
    const size = state.historial.size;
    const total = state.historial.totalElements;

    const inicio = total === 0 ? 0 : page * size + 1;
    const fin = Math.min((page + 1) * size, total);
    info.textContent = `Mostrando ${inicio}-${fin} de ${total} despachos`;

    let html = '';
    html += `<button ${page === 0 ? 'disabled' : ''} onclick="cambiarPagina(${page - 1})">◀</button>`;

    const totalPages = state.historial.totalPages;
    let start = Math.max(0, page - 2);
    let end = Math.min(totalPages - 1, page + 2);

    for (let i = start; i <= end; i++) {
        html += `<button class="${i === page ? 'active' : ''}" onclick="cambiarPagina(${i})">${i + 1}</button>`;
    }

    html += `<button ${page >= totalPages - 1 ? 'disabled' : ''} onclick="cambiarPagina(${page + 1})">▶</button>`;

    botones.innerHTML = html;
}

function cambiarPagina(pagina) {
    if (pagina < 0 || pagina >= state.historial.totalPages) return;
    state.historial.page = pagina;
    cargarHistorial();
}

function verDetalle(id) {
    fetch(`${API}/despachos/${id}`, {
        headers: { 'Authorization': `Bearer ${token}` }
    })
    .then(r => r.json())
    .then(data => {
        if (data.success) {
            const d = data.data;
            alert(`Despacho ${d.numeroDespacho}\n\n` +
                  `Centro: ${d.centroNombre}\n` +
                  `Receptor: ${d.receptorNombre}\n` +
                  `Fecha: ${d.fechaDespacho}\n` +
                  `Productos: ${d.totalLineas} líneas, ${d.totalUnidades} unidades`);
        }
    });
}

// ============================================================
// GESTIÓN DE CENTROS DE COSTO
// ============================================================
function configurarFormCentro() {
    const form = document.getElementById('formCentro');
    if (!form) return;

    form.addEventListener('submit', async (e) => {
        e.preventDefault();
        await guardarCentro();
    });
}

function abrirGestionCentros() {
    document.getElementById('modalCentros').classList.add('active');

    const searchInput = document.getElementById('searchCentros');
    const filtroTipo = document.getElementById('filtroTipoCentro');
    const filtroEstado = document.getElementById('filtroEstadoCentro');

    if (!searchInput.dataset.listener) {
        searchInput.addEventListener('input', debounce(renderizarTablaCentros, 300));
        filtroTipo.addEventListener('change', renderizarTablaCentros);
        filtroEstado.addEventListener('change', renderizarTablaCentros);
        searchInput.dataset.listener = 'true';
    }

    cargarCentrosCostoTodos();
}

function cerrarGestionCentros() {
    document.getElementById('modalCentros').classList.remove('active');
    recargarSelectCentros();
}

async function cargarCentrosCostoTodos() {
    mostrarLoading(true);
    try {
        const res = await fetch(`${API}/centros-costo/todos`, {
            headers: { 'Authorization': `Bearer ${token}` }
        });
        const data = await res.json();

        if (!data.success) {
            Toast.error('No se pudieron cargar los centros de costo', 'Error');
            return;
        }

        centrosCostoCache = data.data;
        renderizarTablaCentros();

    } catch (e) {
        console.error(e);
        Toast.error('Error de conexión', 'Error');
    } finally {
        mostrarLoading(false);
    }
}

function renderizarTablaCentros() {
    const tbody = document.getElementById('tablaCentrosBody');
    if (!tbody) return;

    const search = (document.getElementById('searchCentros')?.value || '').toLowerCase();
    const filtroTipo = document.getElementById('filtroTipoCentro')?.value || '';
    const filtroEstado = document.getElementById('filtroEstadoCentro')?.value || '';

    let filtrados = centrosCostoCache.filter(c => {
        if (search) {
            const q = search;
            if (!c.codigo.toLowerCase().includes(q) &&
                !c.nombre.toLowerCase().includes(q) &&
                !(c.tipo || '').toLowerCase().includes(q)) return false;
        }
        if (filtroTipo && c.tipo !== filtroTipo) return false;
        if (filtroEstado === 'ACTIVO' && !c.activo) return false;
        if (filtroEstado === 'INACTIVO' && c.activo) return false;
        return true;
    });

    if (filtrados.length === 0) {
        tbody.innerHTML = `
            <tr>
                <td colspan="6" style="text-align:center;padding:30px;color:#95a5a6;">
                    <div style="font-size:2em;margin-bottom:8px;">📭</div>
                    <p>No hay centros de costo con estos filtros</p>
                </td>
            </tr>
        `;
        return;
    }

    tbody.innerHTML = filtrados.map(c => `
        <tr>
            <td><span class="sku-cell">${escapeHtml(c.codigo)}</span></td>
            <td>
                <strong>${escapeHtml(c.nombre)}</strong>
                ${c.descripcion ? `<small style="display:block;color:#95a5a6;">${escapeHtml(c.descripcion)}</small>` : ''}
            </td>
            <td>
                <span class="badge" style="background:#eaf2f8;color:#2980b9;">
                    ${getTipoLabel(c.tipo)}
                </span>
            </td>
            <td style="font-size:0.85em;color:#7f8c8d;">
                ${c.responsableNombre ? escapeHtml(c.responsableNombre) : '—'}
                ${c.responsableRut ? `<small style="display:block;">${escapeHtml(c.responsableRut)}</small>` : ''}
            </td>
            <td>
                ${c.activo
                    ? '<span class="badge-estado badge-activo">✅ Activo</span>'
                    : '<span class="badge-estado badge-inactivo">⚪ Inactivo</span>'}
            </td>
            <td class="actions-cell">
                <button class="btn-icon edit" title="Editar"
                        onclick="editarCentro(${c.id})">✏️</button>
                ${c.activo
                    ? `<button class="btn-icon delete" title="Desactivar"
                               onclick="desactivarCentro(${c.id}, '${escapeAttr(c.nombre)}')">🚫</button>`
                    : `<button class="btn-icon" title="Reactivar"
                               style="color:#27ae60;"
                               onclick="reactivarCentro(${c.id}, '${escapeAttr(c.nombre)}')">♻️</button>`
                }
            </td>
        </tr>
    `).join('');
}

function getTipoLabel(tipo) {
    const labels = {
        'FARMACIA': '🏥 Farmacia',
        'SAR': '🚑 SAR',
        'DENTAL': '🦷 Dental',
        'VACUNATORIO': '💉 Vacunatorio',
        'LABORATORIO': '🔬 Laboratorio',
        'BODEGA': '📦 Bodega',
        'OTRO': '📌 Otro'
    };
    return labels[tipo] || tipo || '—';
}

function nuevoCentro() {
    document.getElementById('formCentroTitle').textContent = '➕ Nuevo Centro de Costo';
    document.getElementById('formCentro').reset();
    document.getElementById('centroId').value = '';
    document.getElementById('modalFormCentro').classList.add('active');
    setTimeout(() => document.getElementById('centroCodigo').focus(), 150);
}

function editarCentro(id) {
    const centro = centrosCostoCache.find(c => c.id === id);
    if (!centro) return;

    document.getElementById('formCentroTitle').textContent = '✏️ Editar Centro de Costo';
    document.getElementById('centroId').value = centro.id;
    document.getElementById('centroCodigo').value = centro.codigo;
    document.getElementById('centroNombre').value = centro.nombre;
    document.getElementById('centroDescripcion').value = centro.descripcion || '';
    document.getElementById('centroTipo').value = centro.tipo || 'OTRO';
    document.getElementById('centroResponsableNombre').value = centro.responsableNombre || '';
    document.getElementById('centroResponsableRut').value = centro.responsableRut || '';

    document.getElementById('modalFormCentro').classList.add('active');
    setTimeout(() => document.getElementById('centroCodigo').focus(), 150);
}

function cerrarFormCentro() {
    document.getElementById('modalFormCentro').classList.remove('active');
}

async function guardarCentro() {
    const id = document.getElementById('centroId').value;
    const btn = document.getElementById('btnGuardarCentro');

    const payload = {
        codigo: document.getElementById('centroCodigo').value.trim().toUpperCase(),
        nombre: document.getElementById('centroNombre').value.trim(),
        descripcion: document.getElementById('centroDescripcion').value.trim() || null,
        tipo: document.getElementById('centroTipo').value || 'OTRO',
        responsableNombre: document.getElementById('centroResponsableNombre').value.trim() || null,
        responsableRut: document.getElementById('centroResponsableRut').value.trim() || null
    };

    if (!payload.codigo || !payload.nombre || !payload.tipo) {
        Toast.warning('Complete los campos obligatorios', 'Campos faltantes');
        return;
    }

    btn.disabled = true;
    btn.textContent = 'Guardando...';

    try {
        const url = id ? `${API}/centros-costo/${id}` : `${API}/centros-costo`;
        const method = id ? 'PUT' : 'POST';

        const res = await fetch(url, {
            method,
            headers: {
                'Authorization': `Bearer ${token}`,
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(payload)
        });

        const data = await res.json();

        if (!res.ok || !data.success) {
            Toast.error(data.message || 'Error al guardar', 'Error');
            return;
        }

        Toast.success(
            id ? 'Centro de costo actualizado' : 'Centro de costo creado',
            '✓ Éxito'
        );

        cerrarFormCentro();
        await cargarCentrosCostoTodos();

    } catch (e) {
        console.error(e);
        Toast.error('Error de conexión', 'Error');
    } finally {
        btn.disabled = false;
        btn.textContent = '💾 Guardar';
    }
}

function desactivarCentro(id, nombre) {
    abrirConfirm(
        '🚫 Desactivar Centro de Costo',
        `¿Está seguro de desactivar "${nombre}"? No aparecerá en los nuevos despachos.`,
        async () => {
            try {
                const res = await fetch(`${API}/centros-costo/${id}`, {
                    method: 'DELETE',
                    headers: { 'Authorization': `Bearer ${token}` }
                });
                const data = await res.json();
                if (data.success) {
                    Toast.success('Centro de costo desactivado', '✓ Éxito');
                    await cargarCentrosCostoTodos();
                } else {
                    Toast.error(data.message, 'Error');
                }
            } catch (e) {
                Toast.error('Error de conexión', 'Error');
            } finally {
                cerrarConfirm();
            }
        }
    );
}

async function reactivarCentro(id, nombre) {
    try {
        const res = await fetch(`${API}/centros-costo/${id}/reactivar`, {
            method: 'POST',
            headers: { 'Authorization': `Bearer ${token}` }
        });
        const data = await res.json();
        if (data.success) {
            Toast.success(`Centro "${nombre}" reactivado`, '✓ Éxito');
            await cargarCentrosCostoTodos();
        } else {
            Toast.error(data.message, 'Error');
        }
    } catch (e) {
        Toast.error('Error de conexión', 'Error');
    }
}

async function recargarSelectCentros() {
    try {
        const res = await fetch(`${API}/centros-costo`, {
            headers: { 'Authorization': `Bearer ${token}` }
        });
        const data = await res.json();

        if (!data.success) return;

        const select = document.getElementById('centroCostoId');
        if (!select) return;

        const valorActual = select.value;

        select.innerHTML = '<option value="">Seleccione...</option>';
        data.data.forEach(c => {
            const opt = document.createElement('option');
            opt.value = c.id;
            opt.textContent = `${c.codigo} - ${c.nombre}`;
            select.appendChild(opt);
        });

        if (valorActual) select.value = valorActual;

    } catch (e) {
        console.error(e);
    }
}

// ============================================================
// MODAL DE CONFIRMACIÓN
// ============================================================
function abrirConfirm(titulo, mensaje, onConfirm) {
    document.getElementById('confirmTitle').textContent = titulo;
    document.getElementById('confirmMessage').textContent = mensaje;

    const btn = document.getElementById('btnConfirm');
    const nuevoBtn = btn.cloneNode(true);
    btn.parentNode.replaceChild(nuevoBtn, btn);

    nuevoBtn.addEventListener('click', onConfirm);
    document.getElementById('modalConfirm').classList.add('active');
}

function cerrarConfirm() {
    document.getElementById('modalConfirm').classList.remove('active');
}

// ============================================================
// HELPERS
// ============================================================
function mostrarLoading(show) {
    const overlay = document.getElementById('loadingOverlay');
    if (overlay) overlay.classList.toggle('active', show);
}

function formatFecha(fechaStr) {
    if (!fechaStr) return '—';
    const fecha = new Date(fechaStr);
    return fecha.toLocaleString('es-CL', {
        day: '2-digit', month: '2-digit', year: 'numeric'
    });
}

function escapeHtml(str) {
    if (str == null) return '';
    return String(str)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#039;');
}

function escapeAttr(str) {
    if (str == null) return '';
    return String(str)
        .replace(/&/g, '&amp;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#039;');
}

function debounce(func, wait) {
    let timeout;
    return function executedFunction(...args) {
        const later = () => { clearTimeout(timeout); func(...args); };
        clearTimeout(timeout);
        timeout = setTimeout(later, wait);
    };
}