/* ============================================================
   REPORTES - Sistema Bodega DSM
   ============================================================ */

const API = '/api';

if (!window.requireAuth()) throw new Error('Sin autenticación');
Layout.init('reportes');

const token = window.getToken();
const usuario = window.getUsuario();

let state = {
    tipoReporte: null,
    reporteActual: null,
    bodegas: [],
    categorias: [],
    centros: []
};

// ============================================================
// INICIALIZACIÓN
// ============================================================
document.addEventListener('DOMContentLoaded', async () => {
    await Promise.all([
        cargarBodegas(),
        cargarCategorias(),
        cargarCentros()
    ]);
});

async function cargarBodegas() {
    try {
        const res = await fetch(`${API}/bodegas`, {
            headers: { 'Authorization': `Bearer ${token}` }
        });
        const data = await res.json();
        if (data.success) {
            state.bodegas = data.data;
            const select = document.getElementById('filtroBodega');
            state.bodegas.forEach(b => {
                const opt = document.createElement('option');
                opt.value = b.id;
                opt.textContent = `${b.codigo} - ${b.nombre}`;
                select.appendChild(opt);
            });
        }
    } catch (e) { console.error('Error cargando bodegas', e); }
}

async function cargarCategorias() {
    try {
        const res = await fetch(`${API}/categorias`, {
            headers: { 'Authorization': `Bearer ${token}` }
        });
        const data = await res.json();
        if (data.success) {
            state.categorias = data.data;
            const select = document.getElementById('filtroCategoria');
            state.categorias.forEach(c => {
                const opt = document.createElement('option');
                opt.value = c.id;
                opt.textContent = c.nombre;
                select.appendChild(opt);
            });
        }
    } catch (e) { console.error('Error cargando categorías', e); }
}

async function cargarCentros() {
    try {
        const res = await fetch(`${API}/centros-costo`, {
            headers: { 'Authorization': `Bearer ${token}` }
        });
        const data = await res.json();
        if (data.success) {
            state.centros = data.data;
            const select = document.getElementById('filtroCentro');
            state.centros.forEach(c => {
                const opt = document.createElement('option');
                opt.value = c.id;
                opt.textContent = c.nombre;
                select.appendChild(opt);
            });
        }
    } catch (e) { console.error('Error cargando centros', e); }
}

// ============================================================
// SELECCIÓN DE REPORTE
// ============================================================
function seleccionarReporte(tipo, card) {
    state.tipoReporte = tipo;
    
    document.querySelectorAll('.report-card').forEach(c => c.classList.remove('active'));
    card.classList.add('active');
    
    document.getElementById('filtersCard').style.display = 'block';
    configurarFiltrosPorTipo(tipo);
    
    document.getElementById('filtersCard').scrollIntoView({ behavior: 'smooth' });
}

function configurarFiltrosPorTipo(tipo) {
    // Reset - mostrar todos por defecto
    document.getElementById('filtroBodegaGroup').style.display = 'block';
    document.getElementById('filtroCategoriaGroup').style.display = 'block';
    document.getElementById('filtroCentroGroup').style.display = 'none';
    document.getElementById('filtroTipoMovGroup').style.display = 'none';
    document.getElementById('filtroBusquedaGroup').style.display = 'block';
    document.getElementById('filtrosFecha').style.display = 'grid';
    
    switch (tipo) {
        case 'STOCK_CRITICO':
            document.getElementById('filtroBodegaGroup').style.display = 'none';
            document.getElementById('filtroBusquedaGroup').style.display = 'none';
            document.getElementById('filtrosFecha').style.display = 'none';
            break;
        case 'VENCIMIENTOS':
            document.getElementById('filtroBusquedaGroup').style.display = 'none';
            document.getElementById('filtrosFecha').style.display = 'none';
            break;
        case 'MERMAS_BAJAS':
            document.getElementById('filtroCentroGroup').style.display = 'none';
            break;
        case 'CONSUMO_CENTRO_COSTO':
            document.getElementById('filtroBodegaGroup').style.display = 'none';
            document.getElementById('filtroCentroGroup').style.display = 'block';
            break;
        case 'KARDEX':
            document.getElementById('filtroCentroGroup').style.display = 'none';
            document.getElementById('filtroTipoMovGroup').style.display = 'block';
            break;
        case 'LOG_AUDITORIA':
            document.getElementById('filtroBodegaGroup').style.display = 'none';
            document.getElementById('filtroCategoriaGroup').style.display = 'none';
            break;
        case 'VALORIZACION':
            document.getElementById('filtroBodegaGroup').style.display = 'none';
            document.getElementById('filtroCategoriaGroup').style.display = 'none';
            document.getElementById('filtroBusquedaGroup').style.display = 'none';
            document.getElementById('filtrosFecha').style.display = 'none';
            break;
        case 'CUMPLIMIENTO_OC':
            document.getElementById('filtroBodegaGroup').style.display = 'none';
            document.getElementById('filtroCategoriaGroup').style.display = 'none';
            break;
    }
}

// ============================================================
// GENERAR REPORTE
// ============================================================
async function generarReporte() {
    if (!state.tipoReporte) {
        Toast.warning('Seleccione un tipo de reporte', 'Reporte no seleccionado');
        return;
    }
    
    mostrarLoading(true);
    
    const payload = construirPayload();
    
    try {
        const res = await fetch(`${API}/reportes/generar`, {
            method: 'POST',
            headers: {
                'Authorization': `Bearer ${token}`,
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(payload)
        });
        
        const data = await res.json();
        
        if (!res.ok || !data.success) {
            Toast.error(data.message || 'Error al generar reporte', 'Error');
            return;
        }
        
        state.reporteActual = data.data;
        renderizarPreview(data.data);
        
        document.getElementById('btnExportExcel').disabled = false;
        document.getElementById('btnImprimir').disabled = false;
        
        Toast.success(`${data.data.totalRegistros} registros encontrados`, '✓ Reporte generado');
        
        document.getElementById('reportPreview').scrollIntoView({ behavior: 'smooth' });
        
    } catch (e) {
        console.error(e);
        Toast.error('Error de conexión', 'Error');
    } finally {
        mostrarLoading(false);
    }
}

function construirPayload() {
    return {
        tipoReporte: state.tipoReporte,
        fechaInicio: document.getElementById('fechaInicio').value || null,
        fechaFin: document.getElementById('fechaFin').value || null,
        bodegaId: document.getElementById('filtroBodega').value || null,
        categoriaId: document.getElementById('filtroCategoria').value || null,
        centroCostoId: document.getElementById('filtroCentro').value || null,
        tipoMovimiento: document.getElementById('filtroTipoMov').value || null,
        q: document.getElementById('filtroBusqueda').value.trim() || null,
        page: 0,
        size: 500
    };
}

// ============================================================
// RENDERIZAR PREVIEW
// ============================================================
function renderizarPreview(reporte) {
    
    document.getElementById('previewTitulo').textContent = reporte.titulo;
    document.getElementById('previewSubtitulo').textContent = reporte.subtitulo || '';
    document.getElementById('previewFiltros').textContent = 'Filtros: ' + (reporte.filtrosAplicados || 'Sin filtros');
    document.getElementById('previewGenerado').textContent = 
        `Generado por: ${reporte.generadoPor} el ${reporte.fechaGeneracion}`;
    
    // Resumen
    const resumen = document.getElementById('previewResumen');
    if (reporte.resumen && reporte.resumen.length > 0) {
        resumen.innerHTML = reporte.resumen.map(r => `
            <div class="resumen-item">
                <span class="resumen-label">${escapeHtml(r.label)}</span>
                <span class="resumen-valor">${formatValor(r.valor)}</span>
            </div>
        `).join('');
    } else {
        resumen.innerHTML = '';
    }
    
    // Tabla - Encabezados
    const thead = document.getElementById('previewThead');
    thead.innerHTML = `
        <tr>
            <th>#</th>
            ${reporte.columnas.map(c => `<th>${escapeHtml(c)}</th>`).join('')}
        </tr>
    `;
    
    // Tabla - Filas
    const tbody = document.getElementById('previewTbody');
    
    if (!reporte.filas || reporte.filas.length === 0) {
        tbody.innerHTML = `
            <tr>
                <td colspan="${reporte.columnas.length + 1}" style="text-align:center;padding:30px;color:#95a5a6;">
                    <div style="font-size:2em;margin-bottom:8px;">📭</div>
                    <p>No hay datos para mostrar con estos filtros</p>
                </td>
            </tr>
        `;
    } else {
        tbody.innerHTML = reporte.filas.map((fila, idx) => {
            const valores = Object.values(fila);
            return `
                <tr>
                    <td><strong>${idx + 1}</strong></td>
                    ${valores.map(v => `<td>${formatCellValue(v)}</td>`).join('')}
                </tr>
            `;
        }).join('');
    }
    
    document.getElementById('reportPreview').style.display = 'block';
}

function formatCellValue(valor) {
    if (valor == null) return '—';
    
    // Estado: mostrar badge
    if (['QUIEBRE', 'CRITICO', 'BAJO', 'NORMAL', 'VENCIDO', 'PROXIMO', 'VIGILAR', 'OK'].includes(valor)) {
        return `<span class="badge-estado ${valor}">${valor}</span>`;
    }
    
    // Booleano
    if (typeof valor === 'boolean') {
        return valor ? '✅' : '❌';
    }
    
    // Fecha ISO
    if (typeof valor === 'string' && /^\d{4}-\d{2}-\d{2}/.test(valor)) {
        return formatFecha(valor);
    }
    
    // Número grande
    if (typeof valor === 'number' && Math.abs(valor) >= 1000) {
        return valor.toLocaleString('es-CL');
    }
    
    return escapeHtml(String(valor));
}

function formatValor(v) {
    if (v == null) return '—';
    if (typeof v === 'number' && v >= 1000) {
        return v.toLocaleString('es-CL');
    }
    return escapeHtml(String(v));
}

// ============================================================
// EXPORTAR EXCEL
// ============================================================
async function exportarExcel() {
    if (!state.tipoReporte) return;
    
    const payload = construirPayload();
    
    mostrarLoading(true);
    
    try {
        const res = await fetch(`${API}/reportes/exportar/excel`, {
            method: 'POST',
            headers: {
                'Authorization': `Bearer ${token}`,
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(payload)
        });
        
        if (!res.ok) {
            let err;
            try {
                err = await res.json();
            } catch (ex) {
                err = { message: 'Error al exportar' };
            }
            Toast.error(err.message || 'Error al exportar', 'Error');
            return;
        }
        
        const blob = await res.blob();
        const url = URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = `${state.tipoReporte}_${Date.now()}.xlsx`;
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
        URL.revokeObjectURL(url);
        
        Toast.success('Reporte exportado a Excel', '✓ Exportado');
        
    } catch (e) {
        console.error(e);
        Toast.error('Error al exportar', 'Error');
    } finally {
        mostrarLoading(false);
    }
}

// ============================================================
// IMPRIMIR REPORTE
// ============================================================
function imprimirReporte() {
    window.print();
}

// ============================================================
// HELPERS
// ============================================================
function mostrarLoading(show) {
    document.getElementById('loadingOverlay').classList.toggle('active', show);
}

function formatFecha(fechaStr) {
    if (!fechaStr) return '—';
    const fecha = new Date(fechaStr);
    return fecha.toLocaleDateString('es-CL', {
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