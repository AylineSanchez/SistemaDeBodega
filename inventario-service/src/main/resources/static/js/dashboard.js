/* ============================================================
   DASHBOARD - Sistema Bodega DSM
   ============================================================
   Requiere: session-helper.js cargado antes
   ============================================================ */

const API = '/api';

// Verificar autenticación (redirige si no hay token)
if (!window.requireAuth()) {
    throw new Error('Sin autenticación');
}

// Inicializar layout
Layout.init('dashboard');

const token = window.getToken();
const usuario = window.getUsuario();

// Personalizar bienvenida
const welcomeTitle = document.getElementById('welcomeTitle');
if (welcomeTitle) {
    welcomeTitle.textContent = 
        `Bienvenido/a, ${usuario.nombreCompleto || 'Usuario'} 👋`;
}

/* ============================================================
   INICIALIZACIÓN
   ============================================================ */
document.addEventListener('DOMContentLoaded', async () => {
    await verificarToken();
    await Promise.all([
        cargarKPIs(),
        cargarAlertas()
    ]);
});

async function verificarToken() {
    try {
        const res = await fetch(`${API}/categorias`, {
            headers: { 'Authorization': `Bearer ${token}` }
        });
        
        // Solo redirigir si el token está expirado (401)
        if (res.status === 401) {
            console.warn('⚠️ [DASHBOARD] Token expirado');
            Toast.warning('Tu sesión expiró. Vuelve a iniciar sesión.', 'Sesión expirada');
            setTimeout(() => window.cerrarSesion(), 2000);
        }
    } catch (e) {
        console.error('Error verificando token:', e);
    }
}

async function cargarKPIs() {
    try {
        const resArt = await fetch(`${API}/articulos?size=1`, {
            headers: { 'Authorization': `Bearer ${token}` }
        });
        const dataArt = await resArt.json();
        
        if (dataArt.success) {
            document.getElementById('kpiArticulos').textContent = 
                dataArt.data.totalElements;
        }
        
        const resBod = await fetch(`${API}/bodegas`, {
            headers: { 'Authorization': `Bearer ${token}` }
        });
        const dataBod = await resBod.json();
        
        if (dataBod.success) {
            document.getElementById('kpiBodegas').textContent = dataBod.data.length;
        }
        
        const resAll = await fetch(`${API}/articulos?size=2000`, {
            headers: { 'Authorization': `Bearer ${token}` }
        });
        const dataAll = await resAll.json();
        
        if (dataAll.success) {
            const criticos = dataAll.data.content.filter(a => a.estadoStock === 'CRITICO').length;
            const quiebres = dataAll.data.content.filter(a => a.estadoStock === 'QUIEBRE').length;
            
            document.getElementById('kpiCriticos').textContent = criticos;
            document.getElementById('kpiQuiebres').textContent = quiebres;
        }
        
    } catch (e) {
        console.error('Error cargando KPIs:', e);
    }
}

async function cargarAlertas() {
    try {
        const res = await fetch(`${API}/articulos?size=2000`, {
            headers: { 'Authorization': `Bearer ${token}` }
        });
        const data = await res.json();
        
        if (!data.success) return;
        
        const articulos = data.data.content;
        const criticos = articulos.filter(a => a.estadoStock === 'CRITICO').slice(0, 3);
        const quiebres = articulos.filter(a => a.estadoStock === 'QUIEBRE').slice(0, 3);
        
        const alertas = [];
        
        quiebres.forEach(a => {
            alertas.push({
                tipo: 'red',
                icon: '🔴',
                title: 'Quiebre de Stock',
                desc: `${a.nombreTecnico} (${a.sku}) — 0 ${a.unidadMedidaBase}`,
                action: 'Ver artículo →',
                href: `/articulos.html?q=${encodeURIComponent(a.sku)}`
            });
        });
        
        criticos.forEach(a => {
            alertas.push({
                tipo: 'yellow',
                icon: '⚠️',
                title: 'Stock Crítico',
                desc: `${a.nombreTecnico} (${a.sku}) — ${a.stockTotal}/${a.stockMinimo} ${a.unidadMedidaBase}`,
                action: 'Ver artículo →',
                href: `/articulos.html?q=${encodeURIComponent(a.sku)}`
            });
        });
        
        document.getElementById('alertCount').textContent = 
            `${alertas.length} ${alertas.length === 1 ? 'alerta activa' : 'alertas activas'}`;
        
        const grid = document.getElementById('alertsGrid');
        
        if (alertas.length === 0) {
            grid.innerHTML = `
                <div style="grid-column:1/-1;text-align:center;padding:40px;color:#27ae60;">
                    <div style="font-size:3em;margin-bottom:10px;">✅</div>
                    <h3 style="color:#27ae60;">Todo en orden</h3>
                    <p style="color:#7f8c8d;">No hay alertas activas en este momento.</p>
                </div>
            `;
            return;
        }
        
        grid.innerHTML = alertas.map(alerta => `
            <button class="alert-card ${alerta.tipo}"
                    onclick="window.location.href='${alerta.href}'">
                <span class="alert-icon">${alerta.icon}</span>
                <div class="alert-content">
                    <span class="alert-title">${escapeHtml(alerta.title)}</span>
                    <span class="alert-desc">${escapeHtml(alerta.desc)}</span>
                    <span class="alert-action">${alerta.action}</span>
                </div>
            </button>
        `).join('');
        
    } catch (e) {
        console.error('Error cargando alertas:', e);
        document.getElementById('alertsGrid').innerHTML = `
            <div style="grid-column:1/-1;text-align:center;padding:40px;color:#e74c3c;">
                Error al cargar alertas
            </div>
        `;
    }
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