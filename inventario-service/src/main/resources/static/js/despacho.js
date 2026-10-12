/* ============================================================
   DESPACHO - Sistema Bodega DSM (con FEFO)
   ============================================================ */

const API = '/api';

if (!window.requireAuth()) throw new Error('Sin autenticación');
Layout.init('despacho');

const token = window.getToken();
const usuario = window.getUsuario();

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
    }
};

document.addEventListener('DOMContentLoaded', async () => {
    await Promise.all([
        cargarCentrosCosto(),
        cargarBodegas()
    ]);
    await cargarPedidosPendientes();
    configurarBuscadorProductos();
    configurarBuscadorPedidos();
});

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

function cambiarTab(tab) {
    state.tabActiva = tab;
    
    document.querySelectorAll('.tab').forEach(t => t.classList.remove('active'));
    document.getElementById('tab' + tab.charAt(0).toUpperCase() + tab.slice(1)).classList.add('active');
    
    document.getElementById('contenidoPedidos').style.display = tab === 'pedidos' ? 'block' : 'none';
    document.getElementById('contenidoNuevo').style.display = tab === 'nuevo' ? 'block' : 'none';
    document.getElementById('contenidoHistorial').style.display = tab === 'historial' ? 'block' : 'none';
    
    if (tab === 'pedidos') cargarPedidosPendientes();
    if (tab === 'historial') cargarHistorial();
}

/* ============================================================
   PEDIDOS PENDIENTES
   ============================================================ */
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
    const prioridadFilter = document.getElementById('prioridadFilter').value;
    const search = document.getElementById('searchPedidos').value.toLowerCase();
    
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
                <button class="btn btn-primary" onclick="prepararPedido(${p.id})">
                    📤 Preparar
                </button>
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
            <div class="form-card" style="margin-bottom:15px;">
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
            
            <div class="form-card">
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

/* ============================================================
   BUSCADOR DE PRODUCTOS
   ============================================================ */
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

/* ============================================================
   MODAL PRODUCTO
   ============================================================ */
function abrirModalProducto(articulo, cantidadSugerida = 1) {
    state.productoActual = articulo;
    state.lotesSugeridos = [];
    
    document.getElementById('prodSku').value = articulo.sku;
    document.getElementById('prodNombre').value = articulo.nombreTecnico;
    document.getElementById('prodCantidad').value = cantidadSugerida;
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

/* ============================================================
   GUARDAR DESPACHO
   ============================================================ */
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

/* ============================================================
   HISTORIAL
   ============================================================ */
async function cargarHistorial() {
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

document.addEventListener('DOMContentLoaded', () => {
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
});

/* ============================================================
   HELPERS
   ============================================================ */
function mostrarLoading(show) {
    document.getElementById('loadingOverlay').classList.toggle('active', show);
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

function debounce(func, wait) {
    let timeout;
    return function executedFunction(...args) {
        const later = () => { clearTimeout(timeout); func(...args); };
        clearTimeout(timeout);
        timeout = setTimeout(later, wait);
    };
}