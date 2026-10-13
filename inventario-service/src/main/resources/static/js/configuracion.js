/* ============================================================
   CONFIGURACIÓN DEL SISTEMA - Sistema Bodega DSM
   ============================================================ */

const API = '/api';

if (!window.requireAuth()) throw new Error('Sin autenticación');
Layout.init('config');

const token = window.getToken();
const usuario = window.getUsuario();

let state = {
    configuraciones: [],
    esJefatura: false
};

document.addEventListener('DOMContentLoaded', async () => {
    state.esJefatura = usuario.rol === 'JEFATURA';
    await cargarConfiguraciones();
});

async function cargarConfiguraciones() {
    mostrarLoading(true);
    try {
        const res = await fetch(`${API}/configuracion`, {
            headers: { 'Authorization': `Bearer ${token}` }
        });
        const data = await res.json();

        if (!data.success) {
            Toast.error(data.message || 'Error al cargar configuración', 'Error');
            return;
        }

        state.configuraciones = data.data;
        renderizarFormulario();

    } catch (e) {
        console.error(e);
        Toast.error('Error de conexión', 'Error');
    } finally {
        mostrarLoading(false);
    }
}

function renderizarFormulario() {
    const container = document.getElementById('configForm');
    const get = (clave) => state.configuraciones.find(c => c.clave === clave);

    const diasCritico = get('dias_alerta_vencimiento_critico');
    const diasProximo = get('dias_alerta_vencimiento_proximo');
    const diasVigilar = get('dias_alerta_vencimiento_vigilar');
    const timeout = get('timeout_sesion_minutos');
    const notifCritico = get('notif_critico_activo');
    const notifBloqueo = get('notif_bloqueo_activo');
    const notifVencimiento = get('notif_vencimiento_activo');   // ⚡ NUEVO

    const disabled = state.esJefatura ? '' : 'disabled';

    container.innerHTML = `
        <h3 style="margin-bottom:20px;color:#2c3e50;">Información del Sistema</h3>
        <div style="display:grid;grid-template-columns:1fr 1fr;gap:15px;margin-bottom:25px;">
            <div>
                <label style="font-weight:600;font-size:0.85em;color:#555;">Institución</label>
                <input type="text" value="Ilustre Municipalidad de Santa Cruz"
                       style="width:100%;padding:10px;border:1px solid #ddd;border-radius:8px;margin-top:5px;" readonly>
            </div>
            <div>
                <label style="font-weight:600;font-size:0.85em;color:#555;">Unidad</label>
                <input type="text" value="Dirección de Salud Municipal - Bodega"
                       style="width:100%;padding:10px;border:1px solid #ddd;border-radius:8px;margin-top:5px;" readonly>
            </div>
        </div>

        <h3 style="margin-bottom:20px;color:#2c3e50;">Parámetros Operativos</h3>
        <div style="display:grid;grid-template-columns:1fr 1fr;gap:15px;margin-bottom:25px;">
            <div>
                <label style="font-weight:600;font-size:0.85em;color:#555;">Días alerta vencimiento crítico</label>
                <input type="number" id="cfgDiasCritico" value="${diasCritico?.valor || 120}" ${disabled}
                       style="width:100%;padding:10px;border:1px solid #ddd;border-radius:8px;margin-top:5px;">
                <small style="color:#95a5a6;font-size:0.75em;">Artículos que vencen dentro de este plazo</small>
            </div>
            <div>
                <label style="font-weight:600;font-size:0.85em;color:#555;">Días alerta vencimiento próximo</label>
                <input type="number" id="cfgDiasProximo" value="${diasProximo?.valor || 180}" ${disabled}
                       style="width:100%;padding:10px;border:1px solid #ddd;border-radius:8px;margin-top:5px;">
                <small style="color:#95a5a6;font-size:0.75em;">Alerta intermedia</small>
            </div>
            <div>
                <label style="font-weight:600;font-size:0.85em;color:#555;">Días alerta vencimiento vigilar</label>
                <input type="number" id="cfgDiasVigilar" value="${diasVigilar?.valor || 365}" ${disabled}
                       style="width:100%;padding:10px;border:1px solid #ddd;border-radius:8px;margin-top:5px;">
                <small style="color:#95a5a6;font-size:0.75em;">Aviso con un año de anticipación</small>
            </div>
            <div>
                <label style="font-weight:600;font-size:0.85em;color:#555;">Timeout sesión (minutos)</label>
                <input type="number" id="cfgTimeout" value="${timeout?.valor || 15}" ${disabled}
                       style="width:100%;padding:10px;border:1px solid #ddd;border-radius:8px;margin-top:5px;">
                <small style="color:#95a5a6;font-size:0.75em;">Minutos de inactividad</small>
            </div>
        </div>

        <h3 style="margin-bottom:20px;color:#2c3e50;">Notificaciones por Correo</h3>
        <div style="display:grid;grid-template-columns:1fr 1fr 1fr;gap:15px;margin-bottom:25px;">
            <label style="display:flex;align-items:center;gap:10px;font-size:0.9em;padding:12px;background:#faf9f7;border-radius:8px;">
                <input type="checkbox" id="cfgNotifCritico"
                       ${notifCritico?.valor === 'true' ? 'checked' : ''} ${disabled}>
                <div>
                    <strong style="color:#2c3e50;">Alertas de Stock Crítico</strong>
                    <small style="display:block;color:#7f8c8d;">Email diario 8:00 AM a JEFATURA y OPERATIVO</small>
                </div>
            </label>
            <label style="display:flex;align-items:center;gap:10px;font-size:0.9em;padding:12px;background:#faf9f7;border-radius:8px;">
                <input type="checkbox" id="cfgNotifVencimiento"
                       ${notifVencimiento?.valor === 'true' ? 'checked' : ''} ${disabled}>
                <div>
                    <strong style="color:#2c3e50;">Alertas de Vencimientos</strong>
                    <small style="display:block;color:#7f8c8d;">Email diario 8:05 AM a JEFATURA, OPERATIVO y AUDITOR</small>
                </div>
            </label>
            <label style="display:flex;align-items:center;gap:10px;font-size:0.9em;padding:12px;background:#faf9f7;border-radius:8px;">
                <input type="checkbox" id="cfgNotifBloqueo"
                       ${notifBloqueo?.valor === 'true' ? 'checked' : ''} ${disabled}>
                <div>
                    <strong style="color:#2c3e50;">Alertas de Bloqueo Sanitario</strong>
                    <small style="display:block;color:#7f8c8d;">Email inmediato al crear un bloqueo</small>
                </div>
            </label>
        </div>

        ${state.esJefatura ? `
            <div style="display:flex;gap:10px;justify-content:flex-end;flex-wrap:wrap;">
                <button class="btn btn-primary" onclick="guardarConfiguracion()">
                    💾 Guardar Cambios
                </button>
                <button class="btn btn-secondary" onclick="probarNotificacion('critico')">
                    📧 Probar Críticos
                </button>
                <button class="btn btn-secondary" onclick="probarNotificacion('vencimiento')">
                    📧 Probar Vencimientos
                </button>
            </div>
        ` : `
            <div style="padding:15px;background:#fef9e7;border-left:4px solid #f39c12;border-radius:8px;">
                <strong style="color:#7d6608;">ℹ️ Solo lectura:</strong>
                <p style="margin-top:5px;color:#7d6608;font-size:0.9em;">
                    Solo el rol JEFATURA puede modificar la configuración del sistema.
                </p>
            </div>
        `}
    `;
}

async function guardarConfiguracion() {
    const cambios = [
        { clave: 'dias_alerta_vencimiento_critico', valor: document.getElementById('cfgDiasCritico').value },
        { clave: 'dias_alerta_vencimiento_proximo', valor: document.getElementById('cfgDiasProximo').value },
        { clave: 'dias_alerta_vencimiento_vigilar', valor: document.getElementById('cfgDiasVigilar').value },
        { clave: 'timeout_sesion_minutos', valor: document.getElementById('cfgTimeout').value },
        { clave: 'notif_critico_activo', valor: document.getElementById('cfgNotifCritico').checked ? 'true' : 'false' },
        { clave: 'notif_vencimiento_activo', valor: document.getElementById('cfgNotifVencimiento').checked ? 'true' : 'false' },
        { clave: 'notif_bloqueo_activo', valor: document.getElementById('cfgNotifBloqueo').checked ? 'true' : 'false' }
    ];

    mostrarLoading(true);
    try {
        for (const c of cambios) {
            const res = await fetch(`${API}/configuracion`, {
                method: 'PUT',
                headers: {
                    'Authorization': `Bearer ${token}`,
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify(c)
            });

            const data = await res.json();
            if (!data.success) {
                Toast.error(data.message || `Error al guardar ${c.clave}`, 'Error');
                return;
            }
        }

        Toast.success('Configuración guardada exitosamente', '✓ Guardado');
        await cargarConfiguraciones();

    } catch (e) {
        console.error(e);
        Toast.error('Error de conexión', 'Error');
    } finally {
        mostrarLoading(false);
    }
}

async function probarNotificacion(tipo) {
    const endpoint = tipo === 'critico'
        ? '/api/configuracion/probar-notificacion-critico'
        : '/api/configuracion/probar-notificacion-vencimiento';

    const titulo = tipo === 'critico' ? 'críticos' : 'vencimientos';

    const ok = confirm(`¿Enviar un correo de prueba con los ${titulo} actuales?`);
    if (!ok) return;

    mostrarLoading(true);
    try {
        const res = await fetch(endpoint, {
            method: 'POST',
            headers: { 'Authorization': `Bearer ${token}` }
        });
        const data = await res.json();

        if (data.success) {
            Toast.success('Correo de prueba enviado. Revisa la bandeja.', '✓ Enviado');
        } else {
            Toast.error(data.message || 'Error al enviar prueba', 'Error');
        }
    } catch (e) {
        console.error(e);
        Toast.error('Error de conexión', 'Error');
    } finally {
        mostrarLoading(false);
    }
}

function mostrarLoading(show) {
    document.getElementById('loadingOverlay').classList.toggle('active', show);
}