/* ============================================================
   RECEPCIÓN DE MERCADERÍA - Sistema Bodega DSM
   ============================================================ */

const API = '/api';

if (!window.requireAuth()) throw new Error('Sin autenticación');
Layout.init('recepcion');

const token = window.getToken();
const usuario = window.getUsuario();

let state = {
    modo: 'formulario',
    detalles: [],
    productoActual: null,
    bodegas: [],
    categorias: [],
    historial: {
        page: 0,
        size: 20,
        totalPages: 0,
        totalElements: 0,
        q: '',
        estado: ''
    }
};

document.addEventListener('DOMContentLoaded', async () => {
    await cargarBodegas();
    await cargarCategorias();
    await inicializarFormulario();
    configurarBuscadorProductos();
});

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
    } catch (e) {
        console.error('Error cargando bodegas', e);
    }
}

async function cargarCategorias() {
    try {
        const res = await fetch(`${API}/categorias`, {
            headers: { 'Authorization': `Bearer ${token}` }
        });
        const data = await res.json();
        if (data.success) {
            state.categorias = data.data;
        }
    } catch (e) {
        console.error('Error cargando categorías', e);
    }
}

async function inicializarFormulario() {
    const hoy = new Date().toISOString().split('T')[0];
    document.getElementById('fechaEmision').value = hoy;
    document.getElementById('fechaRecepcion').value = hoy;
}

/* ============================================================
   BUSCADOR DE PRODUCTOS (autocomplete)
   ============================================================ */
let searchTimeout = null;
let productosResultado = [];

function configurarBuscadorProductos() {
    const input = document.getElementById('productoSearch');
    const suggestions = document.getElementById('productoSuggestions');
    
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
            suggestions.innerHTML = `
                <div class="producto-option" style="color:#95a5a6;">
                    No se encontraron productos
                </div>
            `;
        } else {
            suggestions.innerHTML = productosResultado.map(p => `
                <div class="producto-option" onclick="seleccionarProducto(${p.id})">
                    <div><span class="sku">${p.sku}</span> — ${escapeHtml(p.nombreTecnico)}</div>
                    <div style="font-size:0.8em;color:#7f8c8d;margin-top:3px;">
                        ${p.categoriaCodigo} · ${p.unidadMedidaBase}
                        ${p.requiereLote ? ' · 📦 Requiere Lote' : ''}
                    </div>
                </div>
            `).join('');
        }
        
        suggestions.classList.add('active');
        
    } catch (e) {
        console.error('Error buscando productos', e);
    }
}

function seleccionarProducto(articuloId) {
    const articulo = productosResultado.find(p => p.id === articuloId);
    if (!articulo) return;
    
    document.getElementById('productoSuggestions').classList.remove('active');
    document.getElementById('productoSearch').value = '';
    
    abrirModalProducto(articulo);
}

function agregarProductoManual() {
    Toast.info('Usa el buscador para seleccionar un producto del maestro', 'Info');
}

/* ============================================================
   MODAL AGREGAR PRODUCTO
   ============================================================ */
function abrirModalProducto(articulo) {
    state.productoActual = articulo;
    
    document.getElementById('prodSku').value = articulo.sku;
    document.getElementById('prodNombre').value = articulo.nombreTecnico;
    document.getElementById('prodCantidad').value = 1;
    document.getElementById('prodValorUnitario').value = 0;
    document.getElementById('prodMontoTotal').value = 0;
    document.getElementById('prodLote').value = '';
    document.getElementById('prodVencimiento').value = '';
    document.getElementById('prodRegistroIsp').value = articulo.registroIsp || '';
    document.getElementById('prodUbicacion').value = '';
    document.getElementById('prodEstante').value = '';
    document.getElementById('prodNivel').value = '';
    document.getElementById('prodObservaciones').value = '';
    
    const alertLote = document.getElementById('prodAlertLote');
    if (articulo.requiereLote || articulo.requiereVencimiento) {
        alertLote.style.display = 'block';
    } else {
        alertLote.style.display = 'none';
    }
    
    document.getElementById('prodCantidad').addEventListener('input', recalcularMontoProducto);
    document.getElementById('prodValorUnitario').addEventListener('input', recalcularMontoProducto);
    
    document.getElementById('modalProducto').classList.add('active');
}

function cerrarModalProducto() {
    document.getElementById('modalProducto').classList.remove('active');
    state.productoActual = null;
}

function recalcularMontoProducto() {
    const cantidad = parseInt(document.getElementById('prodCantidad').value) || 0;
    const valorUnit = parseFloat(document.getElementById('prodValorUnitario').value) || 0;
    const montoTotal = cantidad * valorUnit;
    document.getElementById('prodMontoTotal').value = montoTotal.toFixed(2);
}

function confirmarProducto() {
    const articulo = state.productoActual;
    if (!articulo) return;
    
    const cantidad = parseInt(document.getElementById('prodCantidad').value);
    const lote = document.getElementById('prodLote').value.trim();
    const vencimiento = document.getElementById('prodVencimiento').value;
    
    if (!cantidad || cantidad < 1) {
        Toast.warning('Ingrese una cantidad válida', 'Cantidad inválida');
        return;
    }
    
    if (articulo.requiereLote && !lote) {
        Toast.error(`El artículo ${articulo.sku} requiere N° de lote`, 'Lote requerido');
        return;
    }
    
    if (articulo.requiereVencimiento && !vencimiento) {
        Toast.error(`El artículo ${articulo.sku} requiere fecha de vencimiento`, 'Vencimiento requerido');
        return;
    }
    
    const existente = state.detalles.findIndex(d =>
        d.articuloId === articulo.id && 
        (d.numeroLote || '') === (lote || '')
    );
    
    const detalle = {
        articuloId: articulo.id,
        sku: articulo.sku,
        nombreProducto: articulo.nombreTecnico,
        categoriaCodigo: articulo.categoriaCodigo,
        categoriaColor: articulo.categoriaColor,
        unidadMedidaBase: articulo.unidadMedidaBase,
        requiereLote: articulo.requiereLote,
        requiereVencimiento: articulo.requiereVencimiento,
        cantidadRecibida: cantidad,
        valorUnitario: parseFloat(document.getElementById('prodValorUnitario').value) || 0,
        montoTotal: parseFloat(document.getElementById('prodMontoTotal').value) || 0,
        numeroLote: lote || null,
        fechaVencimiento: vencimiento || null,
        registroIsp: document.getElementById('prodRegistroIsp').value.trim() || null,
        ubicacionFisica: document.getElementById('prodUbicacion').value.trim() || null,
        estante: document.getElementById('prodEstante').value.trim() || null,
        nivel: document.getElementById('prodNivel').value.trim() || null,
        observaciones: document.getElementById('prodObservaciones').value.trim() || null
    };
    
    if (existente >= 0) {
        state.detalles[existente].cantidadRecibida += cantidad;
        state.detalles[existente].montoTotal = 
            state.detalles[existente].cantidadRecibida * state.detalles[existente].valorUnitario;
        Toast.info('Se sumó la cantidad al producto existente', 'Actualizado');
    } else {
        state.detalles.push(detalle);
        Toast.success(`${articulo.nombreTecnico} agregado`, '✓ Agregado');
    }
    
    cerrarModalProducto();
    renderizarDetalles();
    recalcularTotales();
}

/* ============================================================
   TABLA DE DETALLES
   ============================================================ */
function renderizarDetalles() {
    const tbody = document.getElementById('detallesBody');
    
    if (state.detalles.length === 0) {
        tbody.innerHTML = `
            <tr>
                <td colspan="8" style="text-align:center;padding:30px;color:#95a5a6;">
                    <div style="font-size:2em;margin-bottom:8px;">📦</div>
                    <p>No hay productos agregados</p>
                    <p style="font-size:0.85em;">Use el buscador o agregue manualmente</p>
                </td>
            </tr>
        `;
        return;
    }
    
    tbody.innerHTML = state.detalles.map((d, idx) => `
        <tr>
            <td><span class="sku-cell">${d.sku}</span></td>
            <td>
                ${escapeHtml(d.nombreProducto)}
                ${d.categoriaCodigo ? `<small style="display:block;color:#95a5a6;">${d.categoriaCodigo}</small>` : ''}
            </td>
            <td>
                <strong>${d.cantidadRecibida}</strong>
                <small style="color:#95a5a6;">${d.unidadMedidaBase}</small>
            </td>
            <td>
                ${d.numeroLote 
                    ? `<span style="font-family:monospace;">${d.numeroLote}</span>`
                    : '<span style="color:#95a5a6;">—</span>'}
            </td>
            <td>
                ${d.fechaVencimiento 
                    ? formatFecha(d.fechaVencimiento)
                    : '<span style="color:#95a5a6;">—</span>'}
            </td>
            <td>$${d.valorUnitario.toLocaleString('es-CL')}</td>
            <td><strong>$${d.montoTotal.toLocaleString('es-CL')}</strong></td>
            <td class="actions-cell">
                <button class="btn-icon delete" onclick="eliminarDetalle(${idx})" title="Eliminar">
                    🗑️
                </button>
            </td>
        </tr>
    `).join('');
}

function eliminarDetalle(idx) {
    state.detalles.splice(idx, 1);
    renderizarDetalles();
    recalcularTotales();
    Toast.info('Producto eliminado', 'Actualizado');
}

/* ============================================================
   TOTALES
   ============================================================ */
function recalcularTotales() {
    const totalUnidades = state.detalles.reduce((sum, d) => sum + d.cantidadRecibida, 0);
    const totalLineas = state.detalles.length;
    const sumaLineas = state.detalles.reduce((sum, d) => sum + d.montoTotal, 0);
    
    let montoNeto = parseFloat(document.getElementById('montoNeto').value) || 0;
    if (montoNeto === 0 && sumaLineas > 0) {
        montoNeto = sumaLineas;
        document.getElementById('montoNeto').value = sumaLineas.toFixed(2);
    }
    
    const iva = montoNeto * 0.19;
    const total = montoNeto + iva;
    
    document.getElementById('montoIva').value = iva.toFixed(2);
    document.getElementById('montoTotal').value = total.toFixed(2);
    
    document.getElementById('totalUnidades').textContent = totalUnidades;
    document.getElementById('totalLineas').textContent = totalLineas;
    document.getElementById('sumaLineas').textContent = '$' + sumaLineas.toLocaleString('es-CL');
    document.getElementById('montoTotalDisplay').textContent = '$' + total.toLocaleString('es-CL');
}

/* ============================================================
   GUARDAR RECEPCIÓN
   ============================================================ */
async function guardarRecepcion(confirmar) {
    const tipoDocumento = document.getElementById('tipoDocumento').value;
    const numeroDocumento = document.getElementById('numeroDocumento').value.trim();
    const rutProveedor = document.getElementById('rutProveedor').value.trim();
    const nombreProveedor = document.getElementById('nombreProveedor').value.trim();
    const fechaEmision = document.getElementById('fechaEmision').value;
    const fechaRecepcion = document.getElementById('fechaRecepcion').value;
    const bodegaId = document.getElementById('bodegaId').value;
    
    if (!tipoDocumento) {
        Toast.warning('Seleccione el tipo de documento', 'Campo requerido');
        return;
    }
    if (!numeroDocumento) {
        Toast.warning('Ingrese el número de documento', 'Campo requerido');
        return;
    }
    if (!rutProveedor) {
        Toast.warning('Ingrese el RUT del proveedor', 'Campo requerido');
        return;
    }
    if (!nombreProveedor) {
        Toast.warning('Ingrese el nombre del proveedor', 'Campo requerido');
        return;
    }
    if (!fechaEmision || !fechaRecepcion) {
        Toast.warning('Complete las fechas', 'Campo requerido');
        return;
    }
    if (!bodegaId) {
        Toast.warning('Seleccione la bodega de destino', 'Campo requerido');
        return;
    }
    if (state.detalles.length === 0) {
        Toast.warning('Agregue al menos un producto', 'Sin productos');
        return;
    }
    
    const payload = {
        tipoDocumento,
        numeroDocumento,
        numeroOrdenCompra: document.getElementById('numeroOrdenCompra').value.trim() || null,
        numeroGuiaDespacho: document.getElementById('numeroGuiaDespacho').value.trim() || null,
        rutProveedor,
        nombreProveedor,
        telefonoProveedor: document.getElementById('telefonoProveedor').value.trim() || null,
        direccionProveedor: document.getElementById('direccionProveedor').value.trim() || null,
        tipoProveedor: document.getElementById('tipoProveedor').value || null,
        fechaEmision,
        fechaRecepcion,
        bodegaId: parseInt(bodegaId),
        receptorRut: usuario.rut,
        receptorNombre: usuario.nombreCompleto,
        montoNeto: parseFloat(document.getElementById('montoNeto').value) || 0,
        montoIva: parseFloat(document.getElementById('montoIva').value) || 0,
        montoTotal: parseFloat(document.getElementById('montoTotal').value) || 0,
        observaciones: document.getElementById('observaciones').value.trim() || null,
        detalles: state.detalles
    };
    
    const btn = event.target;
    const textoOriginal = btn.innerHTML;
    btn.disabled = true;
    btn.innerHTML = '💾 Guardando...';
    
    try {
        const res = await fetch(`${API}/recepciones`, {
            method: 'POST',
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
        
        const recepcionCreada = data.data;
        Toast.success(`Recepción ${recepcionCreada.numeroRecepcion} guardada`, '✓ Guardada');
        
        if (confirmar) {
            const resConf = await fetch(`${API}/recepciones/${recepcionCreada.id}/confirmar`, {
                method: 'POST',
                headers: { 'Authorization': `Bearer ${token}` }
            });
            
            const dataConf = await resConf.json();
            
            if (!resConf.ok || !dataConf.success) {
                Toast.error(dataConf.message || 'Error al confirmar', 'Error al confirmar');
                return;
            }
            
            Toast.success('Recepción confirmada. Stock actualizado.', '✓ Confirmada');
        }
        
        limpiarFormulario();
        mostrarHistorial();
        
    } catch (e) {
        console.error(e);
        Toast.error('Error de conexión', 'Error');
    } finally {
        btn.disabled = false;
        btn.innerHTML = textoOriginal;
    }
}

function limpiarFormulario() {
    const hoy = new Date().toISOString().split('T')[0];
    
    document.getElementById('tipoDocumento').value = '';
    document.getElementById('numeroDocumento').value = '';
    document.getElementById('numeroOrdenCompra').value = '';
    document.getElementById('numeroGuiaDespacho').value = '';
    document.getElementById('rutProveedor').value = '';
    document.getElementById('nombreProveedor').value = '';
    document.getElementById('telefonoProveedor').value = '';
    document.getElementById('direccionProveedor').value = '';
    document.getElementById('tipoProveedor').value = '';
    document.getElementById('fechaEmision').value = hoy;
    document.getElementById('fechaRecepcion').value = hoy;
    document.getElementById('bodegaId').value = '';
    document.getElementById('montoNeto').value = 0;
    document.getElementById('montoIva').value = 0;
    document.getElementById('montoTotal').value = 0;
    document.getElementById('observaciones').value = '';
    
    state.detalles = [];
    renderizarDetalles();
    recalcularTotales();
}

function cancelarFormulario() {
    abrirConfirm(
        'Cancelar recepción',
        '¿Está seguro de cancelar? Se perderán los datos ingresados.',
        () => {
            limpiarFormulario();
            cerrarConfirm();
            Toast.info('Formulario cancelado', 'Cancelado');
        }
    );
}

function nuevaRecepcion() {
    limpiarFormulario();
    document.getElementById('formularioRecepcion').style.display = 'block';
    document.getElementById('historialRecepciones').style.display = 'none';
    state.modo = 'formulario';
    window.scrollTo({ top: 0, behavior: 'smooth' });
}

/* ============================================================
   HISTORIAL DE RECEPCIONES
   ============================================================ */
function mostrarHistorial() {
    document.getElementById('formularioRecepcion').style.display = 'none';
    document.getElementById('historialRecepciones').style.display = 'block';
    state.modo = 'historial';
    cargarHistorial();
}

async function cargarHistorial() {
    mostrarLoading(true);
    try {
        const params = new URLSearchParams({
            page: state.historial.page,
            size: state.historial.size
        });
        if (state.historial.q) params.append('q', state.historial.q);
        if (state.historial.estado) params.append('estado', state.historial.estado);
        
        const res = await fetch(`${API}/recepciones?${params}`, {
            headers: { 'Authorization': `Bearer ${token}` }
        });
        
        const data = await res.json();
        
        if (!data.success) {
            Toast.error(data.message || 'Error al cargar historial', 'Error');
            return;
        }
        
        state.historial.totalPages = data.data.totalPages;
        state.historial.totalElements = data.data.totalElements;
        
        renderizarHistorial(data.data.content);
        renderizarPaginacionHistorial();
        
    } catch (e) {
        console.error(e);
        Toast.error('Error de conexión', 'Error');
    } finally {
        mostrarLoading(false);
    }
}

function renderizarHistorial(recepciones) {
    const tbody = document.getElementById('historialBody');
    
    if (!recepciones || recepciones.length === 0) {
        tbody.innerHTML = `
            <tr>
                <td colspan="9">
                    <div class="empty-state">
                        <div class="icon">📭</div>
                        <h3>No hay recepciones</h3>
                        <p>Prueba con otros filtros o crea una nueva recepción.</p>
                    </div>
                </td>
            </tr>
        `;
        return;
    }
    
    tbody.innerHTML = recepciones.map(r => `
        <tr>
            <td><span class="sku-cell">${r.numeroRecepcion}</span></td>
            <td>
                <strong>${r.tipoDocumento}</strong>
                <small style="display:block;color:#7f8c8d;">${r.numeroDocumento}</small>
            </td>
            <td>
                ${escapeHtml(r.nombreProveedor)}
                <small style="display:block;color:#7f8c8d;">${r.rutProveedor}</small>
            </td>
            <td>
                <span class="badge">${r.bodegaCodigo}</span>
                <small style="display:block;color:#7f8c8d;">${escapeHtml(r.bodegaNombre)}</small>
            </td>
            <td>${formatFecha(r.fechaRecepcion)}</td>
            <td style="text-align:center;">${r.totalLineas}</td>
            <td><strong>$${(r.montoTotal || 0).toLocaleString('es-CL')}</strong></td>
            <td>
                <span class="estado-badge estado-${r.estado}">
                    ${getEstadoIcon(r.estado)} ${r.estado}
                </span>
            </td>
            <td class="actions-cell">
                <button class="btn-icon view" title="Ver detalle"
                        onclick="verDetalleRecepcion(${r.id})">👁️</button>
                ${r.estado === 'BORRADOR' 
                    ? `<button class="btn-icon" title="Confirmar" style="color:#27ae60;"
                               onclick="confirmarRecepcion(${r.id}, '${r.numeroRecepcion}')">✅</button>
                       <button class="btn-icon delete" title="Anular"
                               onclick="anularRecepcion(${r.id}, '${r.numeroRecepcion}')">❌</button>`
                    : ''
                }
            </td>
        </tr>
    `).join('');
}

function getEstadoIcon(estado) {
    switch (estado) {
        case 'BORRADOR': return '📝';
        case 'CONFIRMADA': return '✅';
        case 'ANULADA': return '❌';
        case 'PARCIAL': return '⚠️';
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
    info.textContent = `Mostrando ${inicio}-${fin} de ${total} recepciones`;
    
    let html = '';
    html += `<button ${page === 0 ? 'disabled' : ''} onclick="cambiarPaginaHistorial(${page - 1})">◀</button>`;
    
    const totalPages = state.historial.totalPages;
    let start = Math.max(0, page - 2);
    let end = Math.min(totalPages - 1, page + 2);
    
    for (let i = start; i <= end; i++) {
        html += `<button class="${i === page ? 'active' : ''}"
                         onclick="cambiarPaginaHistorial(${i})">${i + 1}</button>`;
    }
    
    html += `<button ${page >= totalPages - 1 ? 'disabled' : ''} onclick="cambiarPaginaHistorial(${page + 1})">▶</button>`;
    
    botones.innerHTML = html;
}

function cambiarPaginaHistorial(pagina) {
    if (pagina < 0 || pagina >= state.historial.totalPages) return;
    state.historial.page = pagina;
    cargarHistorial();
}

function limpiarFiltrosHistorial() {
    document.getElementById('searchHistorial').value = '';
    document.getElementById('estadoFilter').value = '';
    state.historial.q = '';
    state.historial.estado = '';
    state.historial.page = 0;
    cargarHistorial();
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
   ACCIONES DE HISTORIAL
   ============================================================ */
async function verDetalleRecepcion(id) {
    try {
        const res = await fetch(`${API}/recepciones/${id}`, {
            headers: { 'Authorization': `Bearer ${token}` }
        });
        const data = await res.json();
        
        if (!data.success) {
            Toast.error('No se pudo cargar el detalle', 'Error');
            return;
        }
        
        const r = data.data;
        
        const content = document.getElementById('detalleContent');
        content.innerHTML = `
            <div class="form-card" style="margin-bottom:15px;">
                <h3>📄 Datos del Documento</h3>
                <div class="form-row">
                    <div><strong>N° Recepción:</strong> ${r.numeroRecepcion}</div>
                    <div><strong>Tipo:</strong> ${r.tipoDocumento}</div>
                    <div><strong>N° Documento:</strong> ${r.numeroDocumento}</div>
                    <div><strong>OC:</strong> ${r.numeroOrdenCompra || '—'}</div>
                </div>
                <div class="form-row">
                    <div><strong>Fecha Emisión:</strong> ${formatFecha(r.fechaEmision)}</div>
                    <div><strong>Fecha Recepción:</strong> ${formatFecha(r.fechaRecepcion)}</div>
                    <div><strong>Estado:</strong> 
                        <span class="estado-badge estado-${r.estado}">${r.estado}</span>
                    </div>
                </div>
            </div>
            
            <div class="form-card" style="margin-bottom:15px;">
                <h3>🏢 Proveedor</h3>
                <div class="form-row">
                    <div><strong>RUT:</strong> ${r.rutProveedor}</div>
                    <div><strong>Nombre:</strong> ${escapeHtml(r.nombreProveedor)}</div>
                    <div><strong>Tipo:</strong> ${r.tipoProveedor || '—'}</div>
                </div>
            </div>
            
            <div class="form-card">
                <h3>📦 Productos (${r.totalLineas})</h3>
                <table class="detalles-table">
                    <thead>
                        <tr>
                            <th>SKU</th>
                            <th>Producto</th>
                            <th>Cantidad</th>
                            <th>Lote</th>
                            <th>Vencimiento</th>
                            <th>Monto</th>
                        </tr>
                    </thead>
                    <tbody>
                        ${r.detalles.map(d => `
                            <tr>
                                <td><span class="sku-cell">${d.sku}</span></td>
                                <td>${escapeHtml(d.nombreProducto)}</td>
                                <td><strong>${d.cantidadRecibida}</strong> ${d.unidadMedidaBase}</td>
                                <td>${d.numeroLote || '—'}</td>
                                <td>${d.fechaVencimiento ? formatFecha(d.fechaVencimiento) : '—'}</td>
                                <td>$${(d.montoTotal || 0).toLocaleString('es-CL')}</td>
                            </tr>
                        `).join('')}
                    </tbody>
                </table>
                <div class="totales-card" style="margin-top:15px;">
                    <div class="totales-row">
                        <span>Total unidades:</span>
                        <strong>${r.totalUnidades}</strong>
                    </div>
                    <div class="totales-row total">
                        <span>MONTO TOTAL:</span>
                        <strong>$${(r.montoTotal || 0).toLocaleString('es-CL')}</strong>
                    </div>
                </div>
            </div>
        `;
        
        document.getElementById('btnImprimirDetalle').dataset.recepcionId = id;
        document.getElementById('modalDetalle').classList.add('active');
        
    } catch (e) {
        console.error(e);
        Toast.error('Error de conexión', 'Error');
    }
}

function cerrarModalDetalle() {
    document.getElementById('modalDetalle').classList.remove('active');
}

function imprimirDetalle() {
    window.print();
}

function confirmarRecepcion(id, numero) {
    abrirConfirm(
        'Confirmar recepción',
        `¿Confirma la recepción ${numero}? Se actualizará el stock y se registrará en el Kardex.`,
        async () => {
            try {
                const res = await fetch(`${API}/recepciones/${id}/confirmar`, {
                    method: 'POST',
                    headers: { 'Authorization': `Bearer ${token}` }
                });
                const data = await res.json();
                
                if (data.success) {
                    Toast.success('Recepción confirmada. Stock actualizado.', '✓ Éxito');
                    cargarHistorial();
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

function anularRecepcion(id, numero) {
    abrirConfirm(
        'Anular recepción',
        `¿Anular la recepción ${numero}? Esta acción no se puede deshacer.`,
        async () => {
            try {
                const res = await fetch(`${API}/recepciones/${id}/anular?motivo=Anulada por el usuario`, {
                    method: 'POST',
                    headers: { 'Authorization': `Bearer ${token}` }
                });
                const data = await res.json();
                
                if (data.success) {
                    Toast.success('Recepción anulada', '✓ Éxito');
                    cargarHistorial();
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

/* ============================================================
   MODAL CONFIRMACIÓN
   ============================================================ */
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

/* ============================================================
   HELPERS
   ============================================================ */
function mostrarLoading(show) {
    document.getElementById('loadingOverlay').classList.toggle('active', show);
}

function formatFecha(fechaStr) {
    if (!fechaStr) return '—';
    const fecha = new Date(fechaStr + 'T00:00:00');
    return fecha.toLocaleDateString('es-CL', {
        day: '2-digit',
        month: '2-digit',
        year: 'numeric'
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
        const later = () => {
            clearTimeout(timeout);
            func(...args);
        };
        clearTimeout(timeout);
        timeout = setTimeout(later, wait);
    };
}