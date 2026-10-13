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
    todosLosProductos: [],
    productosFiltrados: [],
    comboAbierto: false,
    historial: {
        page: 0,
        size: 20,
        totalPages: 0,
        totalElements: 0,
        q: '',
        estado: ''
    }
};

// ============================================================
// HELPERS DE FECHA (ZONA HORARIA LOCAL)
// ============================================================
function getFechaLocalISO() {
    const d = new Date();
    const year = d.getFullYear();
    const month = String(d.getMonth() + 1).padStart(2, '0');
    const day = String(d.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
}

document.addEventListener('DOMContentLoaded', async () => {
    await cargarBodegas();
    await cargarCategorias();
    await cargarTodosLosProductos();
    await inicializarFormulario();
    configurarCombobox();
    configurarAutoFormatoRut();
});

async function cargarBodegas() {
    try {
        const res = await fetch(`${API}/bodegas`, {
            headers: { 'Authorization': `Bearer ${token}` }
        });
        const data = await res.json();
        if (data.success) {
            state.bodegas = data.data;
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

async function cargarTodosLosProductos() {
    try {
        const res = await fetch(`${API}/articulos?size=5000&incluirInactivos=false`, {
            headers: { 'Authorization': `Bearer ${token}` }
        });
        const data = await res.json();
        if (data.success) {
            state.todosLosProductos = data.data.content || [];
            state.productosFiltrados = [...state.todosLosProductos];
        }
    } catch (e) {
        console.error('Error cargando productos', e);
    }
}

async function inicializarFormulario() {
    const hoy = getFechaLocalISO();
    document.getElementById('fechaEmision').value = hoy;
    document.getElementById('fechaRecepcion').value = hoy;
}

/* ============================================================
   AUTO-FORMATO DE RUT DEL PROVEEDOR
   ============================================================ */
function configurarAutoFormatoRut() {
    const rutInput = document.getElementById('rutProveedor');

    rutInput.addEventListener('input', (e) => {
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

    rutInput.addEventListener('blur', (e) => {
        e.target.value = formatearRut(e.target.value);
    });

    rutInput.addEventListener('paste', (e) => {
        setTimeout(() => {
            rutInput.value = formatearRut(rutInput.value);
        }, 0);
    });
}

/* ============================================================
   COMBOBOX DE PRODUCTOS
   ============================================================ */
function configurarCombobox() {
    const input = document.getElementById('productoSearch');

    input.addEventListener('focus', () => {
        abrirCombo();
    });

    input.addEventListener('input', (e) => {
        const q = e.target.value.trim().toLowerCase();
        if (q === '') {
            state.productosFiltrados = [...state.todosLosProductos];
        } else {
            state.productosFiltrados = state.todosLosProductos.filter(p =>
                (p.sku || '').toLowerCase().includes(q) ||
                (p.nombreTecnico || '').toLowerCase().includes(q)
            );
        }
        renderizarCombo();
        abrirCombo();
    });

    input.addEventListener('keydown', (e) => {
        if (e.key === 'Enter') {
            e.preventDefault();
            if (state.productosFiltrados.length > 0) {
                seleccionarProducto(state.productosFiltrados[0].id);
            }
        } else if (e.key === 'Escape') {
            cerrarCombo();
        }
    });

    document.addEventListener('click', (e) => {
        if (!e.target.closest('#productoCombobox')) {
            cerrarCombo();
        }
    });
}

function abrirCombo() {
    const list = document.getElementById('productoComboList');
    renderizarCombo();
    list.classList.add('active');
    state.comboAbierto = true;
}

function cerrarCombo() {
    const list = document.getElementById('productoComboList');
    list.classList.remove('active');
    state.comboAbierto = false;
}

function renderizarCombo() {
    const list = document.getElementById('productoComboList');

    if (state.productosFiltrados.length === 0) {
        list.innerHTML = `<div class="combo-empty">No se encontraron productos</div>`;
        return;
    }

    const mostrar = state.productosFiltrados.slice(0, 100);

    list.innerHTML = mostrar.map(p => `
        <div class="combo-item" data-id="${p.id}" onclick="seleccionarProducto(${p.id})">
            <div>
                <span class="sku">${escapeHtml(p.sku)}</span>
                — <span class="nombre">${escapeHtml(p.nombreTecnico)}</span>
            </div>
            <div class="meta">
                ${escapeHtml(p.categoriaCodigo || '')} · ${formatUnidad(p.unidadMedidaBase)}
                ${p.requiereLote ? ' · 📦 Requiere Lote' : ''}
            </div>
        </div>
    `).join('');

    if (state.productosFiltrados.length > 100) {
        list.innerHTML += `<div class="combo-empty">... y ${state.productosFiltrados.length - 100} más. Refine la búsqueda.</div>`;
    }
}

function seleccionarProducto(articuloId) {
    const articulo = state.todosLosProductos.find(p => p.id === articuloId);
    if (!articulo) return;

    cerrarCombo();
    document.getElementById('productoSearch').value = '';
    state.productosFiltrados = [...state.todosLosProductos];

    abrirModalProducto(articulo);
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
    document.getElementById('prodEstante').value = '';
    document.getElementById('prodNivel').value = '';
    document.getElementById('prodObservaciones').value = '';

    document.getElementById('loteRequired').style.display = articulo.requiereLote ? 'inline' : 'none';
    document.getElementById('vencRequired').style.display = articulo.requiereVencimiento ? 'inline' : 'none';

    const selectUbicacion = document.getElementById('prodUbicacion');
    selectUbicacion.innerHTML = '<option value="">Seleccione bodega...</option>';
    state.bodegas.forEach(b => {
        const opt = document.createElement('option');
        opt.value = b.id;
        opt.textContent = `${b.codigo} - ${b.nombre}`;
        selectUbicacion.appendChild(opt);
    });

    const alertLote = document.getElementById('prodAlertLote');
    if (articulo.requiereLote || articulo.requiereVencimiento) {
        alertLote.style.display = 'block';
    } else {
        alertLote.style.display = 'none';
    }

    const inputCantidad = document.getElementById('prodCantidad');
    const inputValor = document.getElementById('prodValorUnitario');
    [inputCantidad, inputValor].forEach(el => {
        const clone = el.cloneNode(true);
        el.parentNode.replaceChild(clone, el);
    });
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
    const ubicacionSelect = document.getElementById('prodUbicacion').value;

    if (!cantidad || cantidad < 1) {
        Toast.warning('Ingrese una cantidad válida', 'Cantidad inválida');
        return;
    }

    if (!ubicacionSelect) {
        Toast.warning('Seleccione la bodega de almacenamiento', 'Bodega requerida');
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

    const bodegaSeleccionada = state.bodegas.find(b => String(b.id) === String(ubicacionSelect));
    const ubicacionFisica = bodegaSeleccionada
        ? `${bodegaSeleccionada.codigo} - ${bodegaSeleccionada.nombre}`
        : null;

    const existente = state.detalles.findIndex(d =>
        d.articuloId === articulo.id &&
        (d.numeroLote || '') === (lote || '') &&
        String(d.bodegaId) === String(ubicacionSelect)
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
        ubicacionFisica: ubicacionFisica,
        bodegaId: parseInt(ubicacionSelect),
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
                <td colspan="9" style="text-align:center;padding:30px;color:#95a5a6;">
                    <div style="font-size:2em;margin-bottom:8px;">📦</div>
                    <p>No hay productos agregados</p>
                    <p style="font-size:0.85em;">Use el buscador para seleccionar un producto</p>
                </td>
            </tr>
        `;
        return;
    }

    tbody.innerHTML = state.detalles.map((d, idx) => `
        <tr>
            <td><span class="sku-cell">${escapeHtml(d.sku)}</span></td>
            <td>
                ${escapeHtml(d.nombreProducto)}
                ${d.categoriaCodigo ? `<small style="display:block;color:#95a5a6;">${escapeHtml(d.categoriaCodigo)}</small>` : ''}
            </td>
            <td>
                <strong>${d.cantidadRecibida}</strong>
                <small style="color:#95a5a6;">${formatUnidad(d.unidadMedidaBase)}</small>
            </td>
            <td>
                ${d.numeroLote
                    ? `<span style="font-family:monospace;">${escapeHtml(d.numeroLote)}</span>`
                    : '<span style="color:#95a5a6;">—</span>'}
            </td>
            <td>
                ${d.fechaVencimiento
                    ? formatFecha(d.fechaVencimiento)
                    : '<span style="color:#95a5a6;">—</span>'}
            </td>
            <td style="font-size:0.85em;color:#7f8c8d;">
                ${d.ubicacionFisica ? escapeHtml(d.ubicacionFisica.split(' - ')[0]) : '—'}
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
   TOTALES (⚡ CORREGIDO: siempre automáticos)
   ============================================================ */
function recalcularTotales() {
    const totalUnidades = state.detalles.reduce((sum, d) => sum + d.cantidadRecibida, 0);
    const totalLineas = state.detalles.length;
    const sumaLineas = state.detalles.reduce((sum, d) => sum + d.montoTotal, 0);

    // ⚡ Monto Neto SIEMPRE es la suma de las líneas (no editable)
    const montoNeto = sumaLineas;
    const iva = montoNeto * 0.19;
    const total = montoNeto + iva;

    document.getElementById('montoNeto').value = montoNeto.toFixed(2);
    document.getElementById('montoIva').value = iva.toFixed(2);
    document.getElementById('montoTotal').value = total.toFixed(2);

    document.getElementById('totalUnidades').textContent = totalUnidades;
    document.getElementById('totalLineas').textContent = totalLineas;
    document.getElementById('sumaLineas').textContent = formatMonto(sumaLineas);
    document.getElementById('montoTotalDisplay').textContent = formatMonto(total);
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
    if (state.detalles.length === 0) {
        Toast.warning('Agregue al menos un producto', 'Sin productos');
        return;
    }

    const bodegaId = state.detalles[0].bodegaId;

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
        bodegaId: bodegaId,
        receptorRut: usuario.rut,
        receptorNombre: usuario.nombreCompleto,
        montoNeto: parseFloat(document.getElementById('montoNeto').value) || 0,
        montoIva: parseFloat(document.getElementById('montoIva').value) || 0,
        montoTotal: parseFloat(document.getElementById('montoTotal').value) || 0,
        observaciones: document.getElementById('observaciones').value.trim() || null,
        detalles: state.detalles.map(d => ({
            articuloId: d.articuloId,
            cantidadRecibida: d.cantidadRecibida,
            valorUnitario: d.valorUnitario,
            montoTotal: d.montoTotal,
            numeroLote: d.numeroLote,
            fechaVencimiento: d.fechaVencimiento,
            registroIsp: d.registroIsp,
            ubicacionFisica: d.ubicacionFisica,
            estante: d.estante,
            nivel: d.nivel,
            observaciones: d.observaciones,
            bodegaId: d.bodegaId
        }))
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

            const detallesResp = dataConf.data?.detalles || [];

            const qrs = detallesResp
                .filter(d => d.codigoQr)
                .map(d => ({
                    codigoQr: d.codigoQr,
                    qrImageUrl: `/api/qr/${d.codigoQr}`,
                    sku: d.sku,
                    nombreArticulo: d.nombreProducto,
                    numeroLote: d.numeroLote,
                    fechaVencimiento: d.fechaVencimiento,
                    bodegaCodigo: d.bodegaCodigo || recepcionCreada.bodegaCodigo,
                    bodegaNombre: d.bodegaNombre || recepcionCreada.bodegaNombre,
                    stockActual: d.cantidadRecibida,
                    unidadMedida: d.unidadMedidaBase
                }));

            if (qrs.length > 0) {
                const datosQR = {
                    nombreArticulo: `Recepción ${recepcionCreada.numeroRecepcion}`,
                    qrs: qrs
                };
                sessionStorage.setItem('qrsParaImprimir', JSON.stringify(datosQR));

                setTimeout(() => {
                    window.open('/qr-print.html', '_blank');
                }, 800);
            } else if (detallesResp.length > 0) {
                setTimeout(() => reimprimirQrs(recepcionCreada.id), 500);
            }
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
    const hoy = getFechaLocalISO();

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
    document.getElementById('montoNeto').value = '0.00';
    document.getElementById('montoIva').value = '0.00';
    document.getElementById('montoTotal').value = '0.00';
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
            <td><span class="sku-cell">${escapeHtml(r.numeroRecepcion)}</span></td>
            <td>
                <strong>${escapeHtml(r.tipoDocumento)}</strong>
                <small style="display:block;color:#7f8c8d;">${escapeHtml(r.numeroDocumento)}</small>
            </td>
            <td>
                ${escapeHtml(r.nombreProveedor)}
                <small style="display:block;color:#7f8c8d;">${escapeHtml(r.rutProveedor)}</small>
            </td>
            <td>
                <span class="badge">${escapeHtml(r.bodegaCodigo || '')}</span>
                <small style="display:block;color:#7f8c8d;">${escapeHtml(r.bodegaNombre || '')}</small>
            </td>
            <td>${formatFecha(r.fechaRecepcion)}</td>
            <td style="text-align:center;">${r.totalLineas}</td>
            <td><strong>${formatMonto(r.montoTotal || 0)}</strong></td>
            <td>
                <span class="estado-badge estado-${r.estado}">
                    ${getEstadoIcon(r.estado)} ${r.estado}
                </span>
            </td>
            <td class="actions-cell">
                <button class="btn-icon view" title="Ver detalle"
                        onclick="verDetalleRecepcion(${r.id})">👁️</button>
                ${r.estado === 'CONFIRMADA'
                    ? `<button class="btn-icon" title="Reimprimir QRs" style="color:#8e44ad;"
                               onclick="reimprimirQrs(${r.id})">🔳</button>`
                    : ''
                }
                ${r.estado === 'BORRADOR'
                    ? `<button class="btn-icon" title="Confirmar" style="color:#27ae60;"
                               onclick="confirmarRecepcion(${r.id})">✅</button>
                       <button class="btn-icon delete" title="Anular"
                               onclick="anularRecepcion(${r.id})">❌</button>`
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
                    <div><strong>N° Recepción:</strong> ${escapeHtml(r.numeroRecepcion)}</div>
                    <div><strong>Tipo:</strong> ${escapeHtml(r.tipoDocumento)}</div>
                    <div><strong>N° Documento:</strong> ${escapeHtml(r.numeroDocumento)}</div>
                    <div><strong>OC:</strong> ${escapeHtml(r.numeroOrdenCompra || '—')}</div>
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
                    <div><strong>RUT:</strong> ${escapeHtml(r.rutProveedor)}</div>
                    <div><strong>Nombre:</strong> ${escapeHtml(r.nombreProveedor)}</div>
                    <div><strong>Tipo:</strong> ${escapeHtml(r.tipoProveedor || '—')}</div>
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
                            <th>Bodega</th>
                            <th>Monto</th>
                        </tr>
                    </thead>
                    <tbody>
                        ${r.detalles.map(d => `
                            <tr>
                                <td><span class="sku-cell">${escapeHtml(d.sku)}</span></td>
                                <td>${escapeHtml(d.nombreProducto)}</td>
                                <td><strong>${d.cantidadRecibida}</strong> ${formatUnidad(d.unidadMedidaBase)}</td>
                                <td>${escapeHtml(d.numeroLote || '—')}</td>
                                <td>${d.fechaVencimiento ? formatFecha(d.fechaVencimiento) : '—'}</td>
                                <td>${escapeHtml(d.ubicacionFisica || '—')}</td>
                                <td>${formatMonto(d.montoTotal || 0)}</td>
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
                        <strong>${formatMonto(r.montoTotal || 0)}</strong>
                    </div>
                </div>
            </div>
        `;

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
    const modal = document.getElementById('modalDetalle');
    if (!modal.classList.contains('active')) {
        Toast.warning('Abra el detalle primero', 'Sin detalle');
        return;
    }

    setTimeout(() => {
        window.print();
    }, 200);
}

function confirmarRecepcion(id) {
    abrirConfirm(
        'Confirmar recepción',
        '¿Confirma la recepción? Se actualizará el stock y se registrará en el Kardex.',
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

function anularRecepcion(id) {
    abrirConfirm(
        'Anular recepción',
        '¿Anular la recepción? Esta acción no se puede deshacer.',
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

async function reimprimirQrs(recepcionId) {
    try {
        mostrarLoading(true);
        const res = await fetch(`${API}/recepciones/${recepcionId}`, {
            headers: { 'Authorization': `Bearer ${token}` }
        });
        const data = await res.json();

        if (!data.success) {
            Toast.error('No se pudo cargar la recepción', 'Error');
            return;
        }

        const r = data.data;

        if (r.estado !== 'CONFIRMADA') {
            Toast.warning('Solo se pueden reimprimir QRs de recepciones confirmadas', 'Estado inválido');
            return;
        }

        const qrs = (r.detalles || [])
            .filter(d => d.codigoQr)
            .map(d => ({
                codigoQr: d.codigoQr,
                qrImageUrl: `/api/qr/${d.codigoQr}`,
                sku: d.sku,
                nombreArticulo: d.nombreProducto,
                numeroLote: d.numeroLote,
                fechaVencimiento: d.fechaVencimiento,
                bodegaCodigo: d.bodegaCodigo || r.bodegaCodigo,
                bodegaNombre: d.bodegaNombre || r.bodegaNombre,
                stockActual: d.cantidadRecibida,
                unidadMedida: d.unidadMedidaBase
            }));

        if (qrs.length === 0) {
            Toast.warning('No se encontraron códigos QR para esta recepción', 'Sin QRs');
            return;
        }

        const datosQR = {
            nombreArticulo: `Recepción ${r.numeroRecepcion}`,
            qrs: qrs
        };
        sessionStorage.setItem('qrsParaImprimir', JSON.stringify(datosQR));

        Toast.success(`${qrs.length} QRs listos para imprimir`, '✓ QRs cargados');

        setTimeout(() => {
            window.open('/qr-print.html', '_blank');
        }, 500);

    } catch (e) {
        console.error(e);
        Toast.error('Error de conexión', 'Error');
    } finally {
        mostrarLoading(false);
    }
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