/* ============================================================
   TRAZABILIDAD NT 226 - Sistema Bodega DSM
   ============================================================ */

const API = '/api';

if (!window.requireAuth()) throw new Error('Sin autenticación');
Layout.init('trazabilidad');

const token = window.getToken();
const usuario = window.getUsuario();

let state = {
    tabActiva: 'bloqueo',
    loteActual: null,
    productoActual: null,
    lotesDisponibles: [],
    lotesAgrupados: [],
    bloqueos: {
        page: 0, size: 20, totalPages: 0, totalElements: 0,
        q: '', estado: ''
    },
    trazabilidad: {
        page: 0, size: 20, totalPages: 0, totalElements: 0,
        q: '', estadoDm: ''
    }
};

// ============================================================
// HELPERS DE FECHA (ZONA HORARIA LOCAL)
// ============================================================

/**
 * Devuelve la fecha actual en formato YYYY-MM-DD usando
 * la zona horaria LOCAL del navegador (no UTC).
 * 
 * ⚠️ NO usar new Date().toISOString() porque convierte a UTC
 * y en Chile (UTC-3/-4) puede devolver el día siguiente.
 */
function getFechaLocalISO() {
    const d = new Date();
    const year = d.getFullYear();
    const month = String(d.getMonth() + 1).padStart(2, '0');
    const day = String(d.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
}

// ============================================================
// INICIALIZACIÓN
// ============================================================
document.addEventListener('DOMContentLoaded', async () => {
    // ⚡ FIX: usar fecha local, no UTC
    document.getElementById('fechaAlerta').value = getFechaLocalISO();

    await cargarLotesDisponibles();
    configurarFiltrosBloqueos();
    configurarFiltrosTrazabilidad();
});

// ============================================================
// TABS
// ============================================================
function cambiarTab(tab) {
    state.tabActiva = tab;

    document.querySelectorAll('.tab').forEach(t => t.classList.remove('active'));
    document.getElementById('tab' + tab.charAt(0).toUpperCase() + tab.slice(1)).classList.add('active');

    document.getElementById('contenidoBloqueo').style.display = tab === 'bloqueo' ? 'block' : 'none';
    document.getElementById('contenidoBloqueados').style.display = tab === 'bloqueados' ? 'block' : 'none';
    document.getElementById('contenidoTrazabilidad').style.display = tab === 'trazabilidad' ? 'block' : 'none';

    if (tab === 'bloqueados') cargarBloqueos();
    if (tab === 'trazabilidad') cargarTrazabilidad();
}

// ============================================================
// CARGA DE LOTES DISPONIBLES (AGRUPADOS POR N° LOTE)
// ============================================================
async function cargarLotesDisponibles() {
    const select = document.getElementById('loteSelect');
    select.innerHTML = '<option value="">Cargando lotes...</option>';

    try {
        const resArt = await fetch(`${API}/articulos?size=5000&incluirInactivos=false`, {
            headers: { 'Authorization': `Bearer ${token}` }
        });
        const dataArt = await resArt.json();

        if (!dataArt.success) {
            select.innerHTML = '<option value="">Error al cargar artículos</option>';
            return;
        }

        const articulosConLote = dataArt.data.content.filter(a => a.requiereLote);

        const todosLosLotes = [];
        for (const art of articulosConLote) {
            try {
                const resStock = await fetch(`${API}/articulos/${art.id}/stocks`, {
                    headers: { 'Authorization': `Bearer ${token}` }
                });
                const dataStock = await resStock.json();

                if (dataStock.success && dataStock.data) {
                    dataStock.data.forEach(s => {
                        if (s.numeroLote && !s.bloqueado) {
                            todosLosLotes.push({
                                loteId: s.loteId,
                                numeroLote: s.numeroLote,
                                sku: art.sku,
                                nombreProducto: art.nombreTecnico,
                                fechaVencimiento: s.fechaVencimiento,
                                stockActual: s.stockActual,
                                bodegaCodigo: s.bodegaCodigo,
                                bodegaNombre: s.bodegaNombre,
                                bloqueado: s.bloqueado || false,
                                articuloId: art.id,
                                unidadMedida: art.unidadMedidaBase
                            });
                        }
                    });
                }
            } catch (e) {
                console.warn(`Error cargando lotes para artículo ${art.id}:`, e);
            }
        }

        state.lotesDisponibles = todosLosLotes;

        // ⚡ Agrupar por numeroLote
        const mapa = new Map();
        todosLosLotes.forEach(l => {
            const key = l.numeroLote;
            if (!mapa.has(key)) {
                mapa.set(key, {
                    numeroLote: key,
                    productos: []
                });
            }
            mapa.get(key).productos.push(l);
        });

        state.lotesAgrupados = Array.from(mapa.values());

        if (state.lotesAgrupados.length === 0) {
            select.innerHTML = '<option value="">No hay lotes disponibles</option>';
        } else {
            select.innerHTML = '<option value="">Seleccione un lote...</option>' +
                state.lotesAgrupados.map((l, i) => {
                    const count = l.productos.length;
                    const label = count > 1
                        ? `Lote ${l.numeroLote} (${count} productos)`
                        : `Lote ${l.numeroLote}`;
                    return `<option value="${i}">${label}</option>`;
                }).join('');
        }

        select.addEventListener('change', (e) => {
            const idx = e.target.value;
            if (idx === '') {
                limpiarSeleccionLote();
                return;
            }
            seleccionarLote(parseInt(idx));
        });

    } catch (e) {
        console.error('Error cargando lotes:', e);
        select.innerHTML = '<option value="">Error de conexión</option>';
    }
}

function limpiarSeleccionLote() {
    state.loteActual = null;
    state.productoActual = null;

    document.getElementById('productoSelect').innerHTML = '<option value="">Seleccione un lote primero</option>';
    document.getElementById('productoSelect').disabled = true;
    document.getElementById('vencimientoLote').value = '';
    document.getElementById('stockLote').value = '';
}

/**
 * ⚡ Al seleccionar un lote, carga sus productos en el segundo select.
 */
function seleccionarLote(idx) {
    const loteAgrupado = state.lotesAgrupados[idx];
    if (!loteAgrupado) return;

    state.loteActual = loteAgrupado;
    state.productoActual = null;

    const selectProducto = document.getElementById('productoSelect');

    selectProducto.innerHTML = '<option value="">Seleccione un producto...</option>' +
        loteAgrupado.productos.map((p, i) => {
            const bodegaInfo = p.bodegaCodigo ? ` - ${p.bodegaCodigo}` : '';
            return `<option value="${i}">${p.nombreProducto} (${p.sku})${bodegaInfo}</option>`;
        }).join('');

    selectProducto.disabled = false;

    document.getElementById('vencimientoLote').value = '';
    document.getElementById('stockLote').value = '';

    selectProducto.onchange = (e) => {
        const prodIdx = e.target.value;
        if (prodIdx === '') {
            state.productoActual = null;
            document.getElementById('vencimientoLote').value = '';
            document.getElementById('stockLote').value = '';
            return;
        }
        seleccionarProducto(parseInt(prodIdx));
    };
}

/**
 * ⚡ Al seleccionar un producto, autocompleta vencimiento y stock.
 */
function seleccionarProducto(idx) {
    if (!state.loteActual) return;

    const producto = state.loteActual.productos[idx];
    if (!producto) return;

    state.productoActual = producto;

    document.getElementById('vencimientoLote').value = formatFecha(producto.fechaVencimiento);
    document.getElementById('stockLote').value = producto.stockActual + ' ' +
        (producto.unidadMedida ? formatUnidad(producto.unidadMedida) : 'unidades');
}

// ============================================================
// CREAR BLOQUEO
// ============================================================
async function crearBloqueo() {
    if (!state.productoActual) {
        Toast.warning('Seleccione un lote y un producto', 'Datos incompletos');
        return;
    }

    const tipoAlerta = document.getElementById('tipoAlerta').value;
    const fechaAlerta = document.getElementById('fechaAlerta').value;
    const justificacion = document.getElementById('justificacion').value.trim();

    if (!fechaAlerta) {
        Toast.warning('Ingrese la fecha de la alerta', 'Campo requerido');
        return;
    }

    if (!justificacion || justificacion.length < 10) {
        Toast.warning('La justificación debe tener al menos 10 caracteres', 'Justificación requerida');
        return;
    }

    if (state.productoActual.bloqueado) {
        Toast.error('Este lote ya está bloqueado', 'Lote bloqueado');
        return;
    }

    const payload = {
        loteId: state.productoActual.loteId,
        tipoAlerta,
        numeroOficioIsp: document.getElementById('numeroOficioIsp').value.trim() || null,
        fechaAlerta,
        descripcionAlerta: document.getElementById('descripcionAlerta').value.trim() || null,
        justificacion
    };

    try {
        const res = await fetch(`${API}/trazabilidad/bloqueos`, {
            method: 'POST',
            headers: {
                'Authorization': `Bearer ${token}`,
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(payload)
        });

        const data = await res.json();

        if (!res.ok || !data.success) {
            Toast.error(data.message || 'Error al crear bloqueo', 'Error');
            return;
        }

        Toast.success(
            `Lote ${state.productoActual.numeroLote} bloqueado. ${data.data.stockAfectado} unidades afectadas.`,
            '✓ Bloqueo creado'
        );

        limpiarFormularioBloqueo();
        await cargarLotesDisponibles();
        cambiarTab('bloqueados');

    } catch (e) {
        console.error(e);
        Toast.error('Error de conexión', 'Error');
    }
}

function limpiarFormularioBloqueo() {
    document.getElementById('loteSelect').value = '';
    document.getElementById('productoSelect').innerHTML = '<option value="">Seleccione un lote primero</option>';
    document.getElementById('productoSelect').disabled = true;
    document.getElementById('vencimientoLote').value = '';
    document.getElementById('stockLote').value = '';
    document.getElementById('tipoAlerta').value = 'ALERTA_ISP';
    document.getElementById('numeroOficioIsp').value = '';
    // ⚡ FIX: usar fecha local
    document.getElementById('fechaAlerta').value = getFechaLocalISO();
    document.getElementById('descripcionAlerta').value = '';
    document.getElementById('justificacion').value = '';

    state.loteActual = null;
    state.productoActual = null;
}

// ============================================================
// LISTAR BLOQUEOS
// ============================================================
function configurarFiltrosBloqueos() {
    document.getElementById('searchBloqueos')?.addEventListener('input', debounce((e) => {
        state.bloqueos.q = e.target.value.trim();
        state.bloqueos.page = 0;
        cargarBloqueos();
    }, 400));

    document.getElementById('estadoBloqueoFilter')?.addEventListener('change', (e) => {
        state.bloqueos.estado = e.target.value;
        state.bloqueos.page = 0;
        cargarBloqueos();
    });
}

async function cargarBloqueos() {
    mostrarLoading(true);
    try {
        const params = new URLSearchParams({
            page: state.bloqueos.page,
            size: state.bloqueos.size
        });
        if (state.bloqueos.q) params.append('q', state.bloqueos.q);
        if (state.bloqueos.estado) params.append('estado', state.bloqueos.estado);

        const res = await fetch(`${API}/trazabilidad/bloqueos?${params}`, {
            headers: { 'Authorization': `Bearer ${token}` }
        });
        const data = await res.json();

        if (!data.success) return;

        state.bloqueos.totalPages = data.data.totalPages;
        state.bloqueos.totalElements = data.data.totalElements;

        renderizarBloqueos(data.data.content);
        renderizarPaginacionBloqueos();

    } catch (e) {
        console.error(e);
    } finally {
        mostrarLoading(false);
    }
}

function renderizarBloqueos(bloqueos) {
    const container = document.getElementById('listaBloqueos');

    if (!bloqueos || bloqueos.length === 0) {
        container.innerHTML = `
            <div class="empty-state">
                <div class="icon">✅</div>
                <h3>No hay bloqueos</h3>
                <p>No hay bloqueos sanitarios con estos filtros.</p>
            </div>
        `;
        return;
    }

    container.innerHTML = bloqueos.map(b => `
        <div class="blocked-item ${b.estado === 'LEVANTADO' ? 'desbloqueado' : ''}">
            <div class="blocked-info">
                <span><strong>N° Bloqueo:</strong> ${b.numeroBloqueo}</span>
                <span><strong>Lote:</strong> ${b.numeroLote}</span>
                <span><strong>SKU:</strong> ${b.sku}</span>
                <span><strong>Producto:</strong> ${escapeHtml(b.nombreProducto)}</span>
                <span><strong>Bloqueado:</strong> ${formatFecha(b.fechaBloqueo)}</span>
                <span><strong>Stock afectado:</strong> ${b.stockAfectado} unidades</span>
                <span class="badge-alerta ${b.tipoAlerta}">${formatTipoAlerta(b.tipoAlerta)}</span>
                <span class="estado-badge estado-${b.estado}">
                    ${b.estado === 'ACTIVO' ? '🔴 ACTIVO' : '🟢 LEVANTADO'}
                </span>
            </div>
            <div style="display:flex;gap:8px;">
                <button class="btn btn-secondary btn-sm" onclick="verDetalleBloqueo(${b.id})">
                    👁️ Ver
                </button>
                ${b.estado === 'ACTIVO' 
                    ? `<button class="btn btn-success btn-sm" 
                               onclick="abrirLevantar(${b.id}, '${b.numeroLote}')">
                         🟢 Levantar
                       </button>`
                    : ''}
            </div>
        </div>
    `).join('');
}

function formatTipoAlerta(tipo) {
    const map = {
        'ALERTA_ISP': '🚨 Alerta ISP',
        'RETIRO_MERCADO': '📦 Retiro Mercado',
        'DEFECTO_FABRICACION': '⚠️ Defecto',
        'CONTAMINACION': '☣️ Contaminación',
        'OTRO': '📝 Otro'
    };
    return map[tipo] || tipo;
}

function renderizarPaginacionBloqueos() {
    const info = document.getElementById('bloqueosInfo');
    const botones = document.getElementById('bloqueosPagination');

    const page = state.bloqueos.page;
    const size = state.bloqueos.size;
    const total = state.bloqueos.totalElements;

    const inicio = total === 0 ? 0 : page * size + 1;
    const fin = Math.min((page + 1) * size, total);
    info.textContent = `Mostrando ${inicio}-${fin} de ${total} bloqueos`;

    let html = '';
    html += `<button ${page === 0 ? 'disabled' : ''} onclick="cambiarPaginaBloqueos(${page - 1})">◀</button>`;

    const totalPages = state.bloqueos.totalPages;
    let start = Math.max(0, page - 2);
    let end = Math.min(totalPages - 1, page + 2);

    for (let i = start; i <= end; i++) {
        html += `<button class="${i === page ? 'active' : ''}" onclick="cambiarPaginaBloqueos(${i})">${i + 1}</button>`;
    }

    html += `<button ${page >= totalPages - 1 ? 'disabled' : ''} onclick="cambiarPaginaBloqueos(${page + 1})">▶</button>`;

    botones.innerHTML = html;
}

function cambiarPaginaBloqueos(pagina) {
    if (pagina < 0 || pagina >= state.bloqueos.totalPages) return;
    state.bloqueos.page = pagina;
    cargarBloqueos();
}

// ============================================================
// VER DETALLE BLOQUEO
// ============================================================
async function verDetalleBloqueo(id) {
    try {
        const res = await fetch(`${API}/trazabilidad/bloqueos/${id}`, {
            headers: { 'Authorization': `Bearer ${token}` }
        });
        const data = await res.json();

        if (!data.success) return;

        const b = data.data;

        document.getElementById('detalleBloqueoContent').innerHTML = `
            <div class="data-card">
                <h4 style="margin-bottom:15px;color:#2c3e50;">Información del Lote</h4>
                <div class="data-grid">
                    <div class="data-item">
                        <span class="data-label">N° Bloqueo</span>
                        <span class="data-value">${b.numeroBloqueo}</span>
                    </div>
                    <div class="data-item">
                        <span class="data-label">Lote</span>
                        <span class="data-value"><strong>${b.numeroLote}</strong></span>
                    </div>
                    <div class="data-item">
                        <span class="data-label">SKU</span>
                        <span class="data-value">${b.sku}</span>
                    </div>
                    <div class="data-item">
                        <span class="data-label">Producto</span>
                        <span class="data-value">${escapeHtml(b.nombreProducto)}</span>
                    </div>
                    <div class="data-item">
                        <span class="data-label">Vencimiento</span>
                        <span class="data-value">${formatFecha(b.fechaVencimiento)}</span>
                    </div>
                    <div class="data-item">
                        <span class="data-label">Registro ISP</span>
                        <span class="data-value">${b.registroIsp || '—'}</span>
                    </div>
                </div>
            </div>

            <div class="data-card">
                <h4 style="margin-bottom:15px;color:#2c3e50;">Información de la Alerta</h4>
                <div class="data-grid">
                    <div class="data-item">
                        <span class="data-label">Tipo Alerta</span>
                        <span class="data-value">
                            <span class="badge-alerta ${b.tipoAlerta}">${formatTipoAlerta(b.tipoAlerta)}</span>
                        </span>
                    </div>
                    <div class="data-item">
                        <span class="data-label">N° Oficio ISP</span>
                        <span class="data-value">${b.numeroOficioIsp || '—'}</span>
                    </div>
                    <div class="data-item">
                        <span class="data-label">Fecha Alerta</span>
                        <span class="data-value">${formatFecha(b.fechaAlerta)}</span>
                    </div>
                    <div class="data-item">
                        <span class="data-label">Bloqueado Por</span>
                        <span class="data-value">${escapeHtml(b.bloqueadoPorNombre)}</span>
                    </div>
                    <div class="data-item">
                        <span class="data-label">Fecha Bloqueo</span>
                        <span class="data-value">${formatFecha(b.fechaBloqueo)}</span>
                    </div>
                </div>

                <div style="margin-top:15px;">
                    <span class="data-label">Justificación</span>
                    <p style="margin-top:5px;color:#555;line-height:1.5;">${escapeHtml(b.justificacion)}</p>
                </div>
            </div>

            <div class="data-card">
                <h4 style="margin-bottom:15px;color:#2c3e50;">Bodegas Afectadas (${b.bodegasAfectadas})</h4>
                ${b.bodegas && b.bodegas.length > 0 
                    ? `<table class="product-table">
                        <thead>
                            <tr>
                                <th>Bodega</th>
                                <th>Cantidad Bloqueada</th>
                                <th>Estado</th>
                            </tr>
                        </thead>
                        <tbody>
                            ${b.bodegas.map(bb => `
                                <tr>
                                    <td><strong>${bb.bodegaCodigo}</strong> — ${escapeHtml(bb.bodegaNombre)}</td>
                                    <td>${bb.cantidadBloqueada} unidades</td>
                                    <td>
                                        <span class="estado-badge estado-${bb.estado}">
                                            ${bb.estado}
                                        </span>
                                    </td>
                                </tr>
                            `).join('')}
                        </tbody>
                       </table>`
                    : '<p style="color:#95a5a6;">No hay bodegas afectadas.</p>'
                }
            </div>
        `;

        document.getElementById('modalDetalleBloqueo').classList.add('active');

    } catch (e) {
        console.error(e);
    }
}

function cerrarModalDetalleBloqueo() {
    document.getElementById('modalDetalleBloqueo').classList.remove('active');
}

// ============================================================
// LEVANTAR BLOQUEO
// ============================================================
let bloqueoALevantar = null;

function abrirLevantar(id, numeroLote) {
    bloqueoALevantar = id;
    document.getElementById('levantarLote').textContent = numeroLote;
    document.getElementById('motivoLevantamiento').value = '';
    document.getElementById('modalLevantar').classList.add('active');
}

function cerrarModalLevantar() {
    document.getElementById('modalLevantar').classList.remove('active');
    bloqueoALevantar = null;
}

async function confirmarLevantamiento() {
    if (!bloqueoALevantar) return;

    const motivo = document.getElementById('motivoLevantamiento').value.trim();

    if (!motivo || motivo.length < 10) {
        Toast.warning('Ingrese un motivo (mínimo 10 caracteres)', 'Motivo requerido');
        return;
    }

    try {
        const res = await fetch(
            `${API}/trazabilidad/bloqueos/${bloqueoALevantar}/levantar?motivo=${encodeURIComponent(motivo)}`,
            {
                method: 'POST',
                headers: { 'Authorization': `Bearer ${token}` }
            }
        );

        const data = await res.json();

        if (!res.ok || !data.success) {
            Toast.error(data.message || 'Error al levantar', 'Error');
            return;
        }

        Toast.success('Bloqueo levantado. El lote puede despacharse.', '✓ Levantado');
        cerrarModalLevantar();
        cargarBloqueos();

    } catch (e) {
        console.error(e);
        Toast.error('Error de conexión', 'Error');
    }
}

// ============================================================
// TRAZABILIDAD DM
// ============================================================
function configurarFiltrosTrazabilidad() {
    document.getElementById('searchTrazabilidad')?.addEventListener('input', debounce((e) => {
        state.trazabilidad.q = e.target.value.trim();
        state.trazabilidad.page = 0;
        cargarTrazabilidad();
    }, 400));

    document.getElementById('estadoDmFilter')?.addEventListener('change', (e) => {
        state.trazabilidad.estadoDm = e.target.value;
        state.trazabilidad.page = 0;
        cargarTrazabilidad();
    });
}

async function cargarTrazabilidad() {
    mostrarLoading(true);
    try {
        const params = new URLSearchParams({
            page: state.trazabilidad.page,
            size: state.trazabilidad.size
        });
        if (state.trazabilidad.q) params.append('q', state.trazabilidad.q);
        if (state.trazabilidad.estadoDm) params.append('estadoDm', state.trazabilidad.estadoDm);

        const res = await fetch(`${API}/trazabilidad/dm?${params}`, {
            headers: { 'Authorization': `Bearer ${token}` }
        });
        const data = await res.json();

        if (!data.success) return;

        state.trazabilidad.totalPages = data.data.totalPages;
        state.trazabilidad.totalElements = data.data.totalElements;

        renderizarTrazabilidad(data.data.content);
        renderizarPaginacionTrazabilidad();

    } catch (e) {
        console.error(e);
    } finally {
        mostrarLoading(false);
    }
}

function renderizarTrazabilidad(items) {
    const tbody = document.getElementById('trazabilidadBody');

    if (!items || items.length === 0) {
        tbody.innerHTML = `
            <tr>
                <td colspan="8">
                    <div class="empty-state">
                        <div class="icon">📄</div>
                        <h3>No hay registros de trazabilidad</h3>
                        <p>Los registros se crean al recepcionar dispositivos médicos.</p>
                    </div>
                </td>
            </tr>
        `;
        return;
    }

    tbody.innerHTML = items.map(t => `
        <tr>
            <td><span class="sku-cell">${t.sku}</span></td>
            <td>${escapeHtml(t.nombreProducto)}</td>
            <td>
                ${t.numeroLote ? `<span style="font-family:monospace;">${t.numeroLote}</span>` : '—'}
                ${t.numeroSerie ? `<small style="display:block;color:#95a5a6;">Serie: ${t.numeroSerie}</small>` : ''}
            </td>
            <td>${formatFecha(t.fechaVencimiento)}</td>
            <td>
                ${t.nombreProveedor || '—'}
                ${t.rutProveedor ? `<small style="display:block;color:#95a5a6;">${t.rutProveedor}</small>` : ''}
            </td>
            <td>${t.numeroRegistroSanitario || '—'}</td>
            <td>
                <span class="estado-badge" style="background:#eaf2f8;color:#2980b9;">
                    ${t.estadoDm || 'N/A'}
                </span>
            </td>
            <td class="actions-cell">
                <button class="btn-icon view" onclick="verDetalleTrazabilidad(${t.id})">👁️</button>
            </td>
        </tr>
    `).join('');
}

function renderizarPaginacionTrazabilidad() {
    const info = document.getElementById('trazabilidadInfo');
    const botones = document.getElementById('trazabilidadPagination');

    const page = state.trazabilidad.page;
    const size = state.trazabilidad.size;
    const total = state.trazabilidad.totalElements;

    const inicio = total === 0 ? 0 : page * size + 1;
    const fin = Math.min((page + 1) * size, total);
    info.textContent = `Mostrando ${inicio}-${fin} de ${total} registros`;

    let html = '';
    html += `<button ${page === 0 ? 'disabled' : ''} onclick="cambiarPaginaTrazabilidad(${page - 1})">◀</button>`;

    const totalPages = state.trazabilidad.totalPages;
    let start = Math.max(0, page - 2);
    let end = Math.min(totalPages - 1, page + 2);

    for (let i = start; i <= end; i++) {
        html += `<button class="${i === page ? 'active' : ''}" onclick="cambiarPaginaTrazabilidad(${i})">${i + 1}</button>`;
    }

    html += `<button ${page >= totalPages - 1 ? 'disabled' : ''} onclick="cambiarPaginaTrazabilidad(${page + 1})">▶</button>`;

    botones.innerHTML = html;
}

function cambiarPaginaTrazabilidad(pagina) {
    if (pagina < 0 || pagina >= state.trazabilidad.totalPages) return;
    state.trazabilidad.page = pagina;
    cargarTrazabilidad();
}

async function verDetalleTrazabilidad(id) {
    try {
        const res = await fetch(`${API}/trazabilidad/dm/${id}`, {
            headers: { 'Authorization': `Bearer ${token}` }
        });
        const data = await res.json();

        if (!data.success) return;

        const t = data.data;

        alert(`Detalle de Trazabilidad:

Producto: ${t.nombreProducto}
SKU: ${t.sku}
Lote: ${t.numeroLote || '—'}
Vencimiento: ${formatFecha(t.fechaVencimiento)}
Proveedor: ${t.nombreProveedor || '—'}
Registro Sanitario: ${t.numeroRegistroSanitario || '—'}
Estado: ${t.estado}`);

    } catch (e) {
        console.error(e);
    }
}

function exportarTrazabilidad() {
    Toast.info('Exportando registros de trazabilidad...', '📄 Exportar');

    fetch(`${API}/trazabilidad/dm/exportar?filtro=${encodeURIComponent(state.trazabilidad.q || '')}`, {
        headers: { 'Authorization': `Bearer ${token}` }
    })
    .then(r => r.json())
    .then(data => {
        if (data.success) {
            const headers = ['SKU', 'Producto', 'Lote', 'Vencimiento', 'Proveedor', 'RUT', 'Reg. Sanitario', 'Estado DM'];
            const rows = data.data.map(t => [
                t.sku,
                t.nombreProducto,
                t.numeroLote || '',
                t.fechaVencimiento || '',
                t.nombreProveedor || '',
                t.rutProveedor || '',
                t.numeroRegistroSanitario || '',
                t.estadoDm || ''
            ]);

            let csvContent = '';
            csvContent += headers.join(',') + '\n';
            rows.forEach(row => {
                csvContent += row.map(c => '"' + String(c).replace(/"/g, '""') + '"').join(',') + '\n';
            });

            const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
            const url = URL.createObjectURL(blob);
            const link = document.createElement('a');
            link.href = url;
            link.download = 'trazabilidad_' + getFechaLocalISO() + '.csv';
            link.click();
            URL.revokeObjectURL(url);

            Toast.success(data.data.length + ' registros exportados', '✓ Exportado');
        }
    })
    .catch(e => {
        Toast.error('Error al exportar', 'Error');
        console.error(e);
    });
}

// ============================================================
// HELPERS
// ============================================================
function mostrarLoading(show) {
    document.getElementById('loadingOverlay').classList.toggle('active', show);
}

function formatFecha(fechaStr) {
    if (!fechaStr) return '—';
    const fecha = new Date(fechaStr.length === 10 ? fechaStr + 'T00:00:00' : fechaStr);
    return fecha.toLocaleDateString('es-CL', {
        day: '2-digit', month: '2-digit', year: 'numeric'
    });
}

function formatUnidad(unidad) {
    if (!unidad) return 'unidades';
    const map = {
        'UNIDAD': 'unidad', 'UNIDADES': 'unidades',
        'CAJA': 'caja', 'CAJAS': 'cajas',
        'PAQUETE': 'paquete', 'PAQUETES': 'paquetes',
        'BOLSA': 'bolsa', 'BOLSAS': 'bolsas',
        'FRASCO': 'frasco', 'FRASCOS': 'frascos',
        'KILO': 'kilo', 'KILOS': 'kilos'
    };
    return map[unidad.toUpperCase()] || unidad.toLowerCase();
}

function escapeHtml(str) {
    if (str == null) return '';
    return String(str)
        .replace(/&/g, '&')
        .replace(/</g, '<')
        .replace(/>/g, '>')
        .replace(/"/g, '"')
        .replace(/'/g, ',');
}

function debounce(func, wait) {
    let timeout;
    return function executedFunction(...args) {
        const later = () => { clearTimeout(timeout); func(...args); };
        clearTimeout(timeout);
        timeout = setTimeout(later, wait);
    };
}