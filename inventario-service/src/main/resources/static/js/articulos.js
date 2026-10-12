/* ============================================================
   LISTA DE ARTÍCULOS - Sistema Bodega DSM
   ============================================================ */

const API = '/api';

if (!window.requireAuth()) throw new Error('Sin autenticación');
Layout.init('inventario');

const token = window.getToken();
const usuario = window.getUsuario();

let state = {
    page: 0,
    size: 20,
    totalPages: 0,
    totalElements: 0,
    q: '',
    categoriaId: '',
    bodegaId: '',
    estado: '',
    incluirInactivos: false
};

let categorias = [];
let bodegas = [];
let qrsArticuloActual = null;

/* ============================================================
   INICIALIZACIÓN
   ============================================================ */
document.addEventListener('DOMContentLoaded', async () => {
    await cargarCategorias();
    await cargarBodegas();
    await cargarArticulos();

    // Filtros
    document.getElementById('searchInput').addEventListener('input', debounce(() => {
        state.q = document.getElementById('searchInput').value.trim();
        state.page = 0;
        cargarArticulos();
    }, 400));

    document.getElementById('categoriaFilter').addEventListener('change', (e) => {
        state.categoriaId = e.target.value;
        state.page = 0;
        cargarArticulos();
    });

    document.getElementById('bodegaFilter').addEventListener('change', (e) => {
        state.bodegaId = e.target.value;
        state.page = 0;
        cargarArticulos();
    });

    document.getElementById('estadoFilter').addEventListener('change', (e) => {
        state.estado = e.target.value;
        state.page = 0;
        cargarArticulos();
    });

    document.getElementById('incluirInactivos').addEventListener('change', (e) => {
        state.incluirInactivos = e.target.checked;
        state.page = 0;
        cargarArticulos();
    });

    // Tamaño de página
    document.getElementById('pageSize').addEventListener('change', (e) => {
        state.size = parseInt(e.target.value);
        state.page = 0;
        cargarArticulos();
    });
});

/* ============================================================
   CARGA DE DATOS DE REFERENCIA
   ============================================================ */
async function cargarCategorias() {
    try {
        const res = await fetch(`${API}/categorias`, {
            headers: { 'Authorization': `Bearer ${token}` }
        });
        const data = await res.json();
        if (data.success) {
            categorias = data.data;
            const select = document.getElementById('categoriaFilter');
            select.innerHTML = '<option value="">Todas las categorías</option>';
            categorias.forEach(c => {
                const opt = document.createElement('option');
                opt.value = c.id;
                opt.textContent = `${c.nombre} (${c.totalArticulos || 0})`;
                select.appendChild(opt);
            });
        }
    } catch (e) {
        console.error(e);
        Toast.error('Error al cargar categorías', 'Error');
    }
}

async function cargarBodegas() {
    try {
        const res = await fetch(`${API}/bodegas`, {
            headers: { 'Authorization': `Bearer ${token}` }
        });
        const data = await res.json();
        if (data.success) {
            bodegas = data.data;
            const select = document.getElementById('bodegaFilter');
            select.innerHTML = '<option value="">Todas las bodegas</option>';
            bodegas.forEach(b => {
                const opt = document.createElement('option');
                opt.value = b.id;
                opt.textContent = `${b.codigo} - ${b.nombre}`;
                select.appendChild(opt);
            });
        }
    } catch (e) {
        console.error(e);
        Toast.error('Error al cargar bodegas', 'Error');
    }
}

/* ============================================================
   CARGA DE ARTÍCULOS
   ============================================================ */
async function cargarArticulos() {
    mostrarLoading(true);
    try {
        const params = new URLSearchParams({
            page: state.page,
            size: state.size,
            incluirInactivos: state.incluirInactivos
        });
        if (state.q) params.append('q', state.q);
        if (state.categoriaId) params.append('categoriaId', state.categoriaId);
        if (state.bodegaId) params.append('bodegaId', state.bodegaId);   // ⚡ Nuevo: enviar al backend
        if (state.estado) params.append('estadoStock', state.estado);   // ⚡ Nuevo: enviar al backend

        const res = await fetch(`${API}/articulos?${params}`, {
            headers: { 'Authorization': `Bearer ${token}` }
        });

        if (res.status === 401) {
            window.cerrarSesion();
            return;
        }

        const data = await res.json();

        if (!data.success) {
            Toast.error(data.message || 'Error al cargar artículos', 'Error');
            return;
        }

        state.totalPages = data.data.totalPages;
        state.totalElements = data.data.totalElements;

        renderizarTabla(data.data.content);
        renderizarPaginacion();

    } catch (e) {
        console.error(e);
        Toast.error('Error de conexión', 'Error');
    } finally {
        mostrarLoading(false);
    }
}

/* ============================================================
   RENDERIZADO
   ============================================================ */
function renderizarTabla(articulos) {
    const tbody = document.getElementById('tablaBody');

    if (!articulos || articulos.length === 0) {
        tbody.innerHTML = `
            <tr>
                <td colspan="7">
                    <div class="empty-state">
                        <div class="icon">📭</div>
                        <h3>No hay artículos</h3>
                        <p>Prueba con otros filtros o agrega un nuevo artículo.</p>
                    </div>
                </td>
            </tr>
        `;
        return;
    }

    tbody.innerHTML = articulos.map(a => {
        const stockClass = getStockClass(a.estadoStock);
        const estadoBadge = getEstadoBadge(a.estadoStock);

        return `
            <tr>
                <td><span class="sku-cell">${escapeHtml(a.sku)}</span></td>
                <td class="name-cell">
                    ${escapeHtml(a.nombreTecnico)}
                    ${a.presentacion ? `<small>${escapeHtml(a.presentacion)}</small>` : ''}
                </td>
                <td>
                    <span class="badge-categoria"
                          style="background-color: ${a.categoriaColor || '#d35400'};">
                        ${escapeHtml(a.categoriaCodigo)}
                    </span>
                </td>
                <td class="stock-cell">
                    <div class="stock-main">
                        <span class="stock-number ${stockClass}">${a.stockTotal}</span>
                        <span class="stock-unit">${formatUnidad(a.unidadMedidaBase)}</span>
                    </div>
                    <div class="stock-range">
                        Mín ${a.stockMinimo} · Máx ${a.stockMaximo || '∞'}
                    </div>
                </td>
                <td>${estadoBadge}</td>
                <td style="text-align:center;">
                    ${a.bodegasCount > 0
                        ? `<span class="bodega-count" title="${(a.stocks || []).map(s => s.bodegaCodigo).join(', ')}">${a.bodegasCount}</span>`
                        : '<span style="color:#95a5a6;">—</span>'}
                </td>
                <td class="actions-cell">
                    <button class="btn-icon view" title="Ver detalle"
                            onclick="verDetalle(${a.id})">👁️</button>
                    ${a.bodegasCount > 0
                        ? `<button class="btn-icon qr" title="Ver códigos QR"
                                   onclick="verQrs(${a.id}, '${escapeAttr(a.nombreTecnico)}')">🔳</button>`
                        : ''
                    }
                    <button class="btn-icon edit" title="Editar"
                            onclick="editar(${a.id})">✏️</button>
                    ${a.activo
                        ? `<button class="btn-icon delete" title="Desactivar"
                                   onclick="desactivar(${a.id}, '${escapeAttr(a.nombreTecnico)}')">🗑️</button>`
                        : `<button class="btn-icon" title="Reactivar" style="color:#27ae60;"
                                   onclick="reactivar(${a.id})">♻️</button>`
                    }
                </td>
            </tr>
        `;
    }).join('');
}

function getStockClass(estado) {
    switch (estado) {
        case 'QUIEBRE': return 'quiebre';
        case 'CRITICO': return 'critico';
        case 'BAJO':    return 'bajo';
        default:        return 'normal';
    }
}

function getEstadoBadge(estado) {
    const map = {
        'QUIEBRE':  { class: 'stock-quiebre', text: '🔴 QUIEBRE' },
        'CRITICO':  { class: 'stock-critico', text: '🟠 CRÍTICO' },
        'BAJO':     { class: 'stock-bajo',    text: '🟡 BAJO' },
        'NORMAL':   { class: 'stock-normal',  text: '🟢 NORMAL' }
    };
    const e = map[estado] || map['NORMAL'];
    return `<span class="stock-badge ${e.class}">${e.text}</span>`;
}

/* ============================================================
   PAGINACIÓN MEJORADA
   ============================================================ */
function renderizarPaginacion() {
    const info = document.getElementById('paginationInfo');
    const botones = document.getElementById('paginationButtons');

    const inicio = state.totalElements === 0 ? 0 : state.page * state.size + 1;
    const fin = Math.min((state.page + 1) * state.size, state.totalElements);

    info.innerHTML = `Mostrando <strong>${inicio}-${fin}</strong> de <strong>${state.totalElements}</strong> artículos`;

    const totalPages = state.totalPages;
    const current = state.page;

    if (totalPages <= 1) {
        botones.innerHTML = '';
        return;
    }

    let html = '';

    // Botón primera página
    html += `<button ${current === 0 ? 'disabled' : ''}
                     onclick="cambiarPagina(0)"
                     title="Primera página">«</button>`;

    // Botón anterior
    html += `<button ${current === 0 ? 'disabled' : ''}
                     onclick="cambiarPagina(${current - 1})"
                     title="Página anterior">◀</button>`;

    // Números de página (máx 5 visibles)
    let start = Math.max(0, current - 2);
    let end = Math.min(totalPages - 1, current + 2);

    if (start > 0) {
        html += `<button onclick="cambiarPagina(0)">1</button>`;
        if (start > 1) {
            html += `<button class="ellipsis">…</button>`;
        }
    }

    for (let i = start; i <= end; i++) {
        html += `<button class="${i === current ? 'active' : ''}"
                         onclick="cambiarPagina(${i})">${i + 1}</button>`;
    }

    if (end < totalPages - 1) {
        if (end < totalPages - 2) {
            html += `<button class="ellipsis">…</button>`;
        }
        html += `<button onclick="cambiarPagina(${totalPages - 1})">${totalPages}</button>`;
    }

    // Botón siguiente
    html += `<button ${current >= totalPages - 1 ? 'disabled' : ''}
                     onclick="cambiarPagina(${current + 1})"
                     title="Página siguiente">▶</button>`;

    // Botón última página
    html += `<button ${current >= totalPages - 1 ? 'disabled' : ''}
                     onclick="cambiarPagina(${totalPages - 1})"
                     title="Última página">»</button>`;

    botones.innerHTML = html;
}

/* ============================================================
   ACCIONES
   ============================================================ */
function cambiarPagina(pagina) {
    if (pagina < 0 || pagina >= state.totalPages) return;
    state.page = pagina;
    cargarArticulos();
    window.scrollTo({ top: 0, behavior: 'smooth' });
}

function verDetalle(id) {
    window.location.href = `/articulo-form.html?id=${id}&mode=view`;
}

function editar(id) {
    window.location.href = `/articulo-form.html?id=${id}`;
}

function desactivar(id, nombre) {
    abrirConfirm(
        'Desactivar artículo',
        `¿Está seguro de desactivar "${nombre}"? El artículo seguirá existiendo pero no aparecerá en los listados.`,
        async () => {
            try {
                const res = await fetch(`${API}/articulos/${id}`, {
                    method: 'DELETE',
                    headers: { 'Authorization': `Bearer ${token}` }
                });
                const data = await res.json();
                if (data.success) {
                    Toast.success('Artículo desactivado', '✓ Éxito');
                    cargarArticulos();
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

async function reactivar(id) {
    try {
        const res = await fetch(`${API}/articulos/${id}/reactivar`, {
            method: 'POST',
            headers: { 'Authorization': `Bearer ${token}` }
        });
        const data = await res.json();
        if (data.success) {
            Toast.success('Artículo reactivado', '✓ Éxito');
            cargarArticulos();
        } else {
            Toast.error(data.message, 'Error');
        }
    } catch (e) {
        Toast.error('Error de conexión', 'Error');
    }
}

function limpiarFiltros() {
    document.getElementById('searchInput').value = '';
    document.getElementById('categoriaFilter').value = '';
    document.getElementById('bodegaFilter').value = '';
    document.getElementById('estadoFilter').value = '';
    document.getElementById('incluirInactivos').checked = false;

    state.q = '';
    state.categoriaId = '';
    state.bodegaId = '';
    state.estado = '';
    state.incluirInactivos = false;
    state.page = 0;

    cargarArticulos();
}

/* ============================================================
   QR - VER Y GENERAR
   ============================================================ */
/* ============================================================
   QR - VER Y GENERAR
   ============================================================ */
async function verQrs(articuloId, nombreArticulo) {
    try {
        const res = await fetch(`${API}/qr/articulo/${articuloId}`, {
            headers: { 'Authorization': `Bearer ${token}` }
        });
        const data = await res.json();

        if (!data.success) {
            Toast.error('No se pudieron cargar los QRs', 'Error');
            return;
        }

        qrsArticuloActual = {
            articuloId,
            nombreArticulo,
            qrs: data.data
        };

        const content = document.getElementById('modalQrsContent');
        const countBadge = document.getElementById('qrModalCount');

        document.getElementById('modalQrsDesc').textContent = nombreArticulo;

        if (data.data.length === 0) {
            countBadge.style.display = 'none';
            content.innerHTML = `
                <div class="qr-empty">
                    <div class="icon">📭</div>
                    <h3>Sin códigos QR</h3>
                    <p>Este artículo no está asignado a ninguna bodega aún.</p>
                </div>
            `;
        } else {
            countBadge.style.display = 'inline-flex';
            countBadge.textContent = `${data.data.length} ${data.data.length === 1 ? 'QR' : 'QRs'}`;

            content.innerHTML = `
                <div class="qr-grid-modal">
                    ${data.data.map(qr => `
                        <div class="qr-item-modal">
                            <span class="qr-bodega-badge">${escapeHtml(qr.bodegaCodigo)}</span>

                            <div class="qr-image-wrapper">
                                <img src="${qr.qrImageUrl}?token=${encodeURIComponent(token)}"
                                     alt="QR ${escapeHtml(qr.codigoQr)}"
                                     loading="lazy">
                            </div>

                            <div class="qr-nombre">${escapeHtml(qr.nombreArticulo)}</div>

                            <div class="qr-bodega-info">
                                ${escapeHtml(qr.bodegaNombre)}
                            </div>

                            <div class="qr-codigo">${escapeHtml(qr.codigoQr)}</div>

                            <div class="qr-stock-info">
                                Stock: <strong>${qr.stockActual}</strong> ${formatUnidad(qr.unidadMedida)}
                            </div>

                            ${qr.ubicacionFisica
                                ? `<div class="qr-ubicacion">📍 ${escapeHtml(qr.ubicacionFisica)}</div>`
                                : ''}
                        </div>
                    `).join('')}
                </div>
            `;
        }

        document.getElementById('modalQrs').classList.add('active');

    } catch (e) {
        console.error(e);
        Toast.error('Error de conexión', 'Error');
    }
}

function cerrarModalQrs() {
    document.getElementById('modalQrs').classList.remove('active');
    qrsArticuloActual = null;
}

function imprimirQrs() {
    if (!qrsArticuloActual) return;
    sessionStorage.setItem('qrsParaImprimir', JSON.stringify(qrsArticuloActual));
    window.open('/qr-print.html', '_blank');
}

/* ============================================================
   IMPORTAR EXCEL
   ============================================================ */
function abrirImportador() {
    document.getElementById('modalImport').classList.add('active');
    document.getElementById('archivoExcel').value = '';
}

function cerrarImportador() {
    document.getElementById('modalImport').classList.remove('active');
}

async function importarExcel() {
    const fileInput = document.getElementById('archivoExcel');
    const file = fileInput.files[0];

    if (!file) {
        Toast.warning('Selecciona un archivo Excel', 'Archivo requerido');
        return;
    }

    if (!file.name.toLowerCase().endsWith('.xlsx')) {
        Toast.error('El archivo debe ser .xlsx', 'Formato inválido');
        return;
    }

    const btn = document.getElementById('btnImportar');
    btn.disabled = true;
    btn.textContent = 'Importando...';

    Toast.info('Importando artículos. Esto puede tardar unos segundos...', '📤 Procesando');

    const formData = new FormData();
    formData.append('archivo', file);

    try {
        const res = await fetch(`${API}/import/excel`, {
            method: 'POST',
            headers: { 'Authorization': `Bearer ${token}` },
            body: formData
        });

        const data = await res.json();

        if (!data.success) {
            Toast.error(data.message, 'Error en la importación');
            return;
        }

        const r = data.data;

        let resumen = `✅ ${r.importados} artículos importados\n`;
        if (r.duplicados > 0) resumen += `⚠️ ${r.duplicados} duplicados (omitidos)\n`;
        if (r.errores && r.errores.length > 0) resumen += `❌ ${r.errores.length} errores`;

        Toast.success(resumen, '✓ Importación completada');

        console.log('📊 Detalle de importación:', r);

        cerrarImportador();
        cargarArticulos();
        cargarCategorias();

    } catch (e) {
        console.error(e);
        Toast.error('Error de conexión al importar', 'Error');
    } finally {
        btn.disabled = false;
        btn.textContent = '📤 Importar';
    }
}

/* ============================================================
   MODAL DE CONFIRMACIÓN
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