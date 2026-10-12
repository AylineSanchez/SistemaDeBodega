/* ============================================================
   GESTIÓN DE USUARIOS - Sistema Bodega DSM
   ============================================================ */

const API = '/api';

if (!window.requireAuth()) throw new Error('Sin autenticación');
Layout.init('usuarios');

const token = window.getToken();
const usuarioActual = window.getUsuario();

// Solo Jefatura puede acceder
if (usuarioActual.rol !== 'JEFATURA') {
    Toast.error('No tiene permisos para acceder a este módulo', 'Acceso denegado');
    setTimeout(() => window.location.href = '/dashboard.html', 1500);
    throw new Error('Sin permisos');
}

let state = {
    q: '',
    rol: '',
    estado: ''
};

let usuarios = [];
let usuarioEditando = null;

/* ============================================================
   INICIALIZACIÓN
   ============================================================ */
document.addEventListener('DOMContentLoaded', async () => {
    await cargarUsuarios();
    
    // Listeners de filtros
    document.getElementById('searchInput').addEventListener('input', debounce((e) => {
        state.q = e.target.value.trim().toLowerCase();
        renderizarTabla();
    }, 300));
    
    document.getElementById('rolFilter').addEventListener('change', (e) => {
        state.rol = e.target.value;
        renderizarTabla();
    });
    
    document.getElementById('estadoFilter').addEventListener('change', (e) => {
        state.estado = e.target.value;
        renderizarTabla();
    });
    
    // Submit del formulario
    document.getElementById('formUsuario').addEventListener('submit', guardarUsuario);
});

/* ============================================================
   CARGAR USUARIOS
   ============================================================ */
async function cargarUsuarios() {
    mostrarLoading(true);
    try {
        const res = await fetch(`${API}/usuarios`, {
            headers: { 'Authorization': `Bearer ${token}` }
        });
        
        if (res.status === 401) {
            Layout.cerrarSesion();
            return;
        }
        
        if (res.status === 403) {
            Toast.error('No tiene permisos para ver usuarios', 'Acceso denegado');
            setTimeout(() => window.location.href = '/dashboard.html', 1500);
            return;
        }
        
        const data = await res.json();
        
        if (!data.success) {
            Toast.error(data.message || 'Error al cargar usuarios', 'Error');
            return;
        }
        
        usuarios = data.data;
        renderizarTabla();
        
    } catch (e) {
        console.error(e);
        Toast.error('Error de conexión', 'Error');
    } finally {
        mostrarLoading(false);
    }
}

/* ============================================================
   RENDERIZAR TABLA
   ============================================================ */
function renderizarTabla() {
    const tbody = document.getElementById('tablaBody');
    
    // Filtrar
    let filtrados = usuarios.filter(u => {
        if (state.q) {
            const q = state.q;
            if (!u.rut.toLowerCase().includes(q) &&
                !u.nombreCompleto.toLowerCase().includes(q) &&
                !u.email.toLowerCase().includes(q)) {
                return false;
            }
        }
        
        if (state.rol && u.rol !== state.rol) return false;
        
        if (state.estado) {
            if (state.estado === 'ACTIVO' && (!u.activo || u.bloqueado)) return false;
            if (state.estado === 'INACTIVO' && u.activo) return false;
            if (state.estado === 'BLOQUEADO' && !u.bloqueado) return false;
        }
        
        return true;
    });
    
    if (filtrados.length === 0) {
        tbody.innerHTML = `
            <tr>
                <td colspan="8">
                    <div class="empty-state">
                        <div class="icon">👥</div>
                        <h3>No hay usuarios</h3>
                        <p>Prueba con otros filtros o agrega un nuevo usuario.</p>
                    </div>
                </td>
            </tr>
        `;
        return;
    }
    
    tbody.innerHTML = filtrados.map(u => {
        const rolClass = getRolClass(u.rol);
        const rolLabel = getRolLabel(u.rol);
        const estadoBadge = getEstadoBadge(u);
        const ultimoAcceso = u.ultimoAcceso 
            ? formatFecha(u.ultimoAcceso) 
            : '<span style="color:#95a5a6;">Nunca</span>';
        
        return `
            <tr>
                <td><span class="sku-cell">${u.rut}</span></td>
                <td class="name-cell">
                    ${escapeHtml(u.nombreCompleto)}
                </td>
                <td style="font-size:0.85em;color:#7f8c8d;">
                    ${escapeHtml(u.email)}
                </td>
                <td>
                    <span class="badge-rol ${rolClass}">${rolLabel}</span>
                </td>
                <td style="font-size:0.85em;color:#7f8c8d;">
                    ${u.dependencia ? escapeHtml(u.dependencia) : '—'}
                </td>
                <td>${estadoBadge}</td>
                <td style="font-size:0.82em;color:#7f8c8d;">
                    ${ultimoAcceso}
                </td>
                <td class="actions-cell">
                    <button class="btn-icon edit" title="Editar"
                            onclick="editarUsuario(${u.id})">✏️</button>
                    ${u.bloqueado 
                        ? `<button class="btn-icon" title="Desbloquear" 
                                   style="color:#f39c12;"
                                   onclick="desbloquearUsuario(${u.id}, '${escapeHtml(u.nombreCompleto)}')">🔓</button>`
                        : ''
                    }
                    ${u.activo 
                        ? `<button class="btn-icon delete" title="Desactivar"
                                   onclick="desactivarUsuario(${u.id}, '${escapeHtml(u.nombreCompleto)}')">🚫</button>`
                        : `<button class="btn-icon" title="Reactivar" 
                                   style="color:#27ae60;"
                                   onclick="reactivarUsuario(${u.id}, '${escapeHtml(u.nombreCompleto)}')">♻️</button>`
                    }
                </td>
            </tr>
        `;
    }).join('');
}

function getRolClass(rol) {
    switch (rol) {
        case 'JEFATURA': return 'jefatura';
        case 'OPERATIVO': return 'operativo';
        case 'AUDITOR': return 'auditor';
        case 'ENCARGADO_DEPENDENCIA': return 'encargado';
        default: return 'operativo';
    }
}

function getRolLabel(rol) {
    const labels = {
        'JEFATURA': 'Jefatura',
        'OPERATIVO': 'Operativo',
        'AUDITOR': 'Auditor NT 226',
        'ENCARGADO_DEPENDENCIA': 'Enc. Dependencia'
    };
    return labels[rol] || rol;
}

function getEstadoBadge(u) {
    if (!u.activo) {
        return '<span class="badge-estado badge-inactivo">⚪ Inactivo</span>';
    }
    if (u.bloqueado) {
        return '<span class="badge-estado badge-bloqueado">⚠️ Bloqueado</span>';
    }
    return '<span class="badge-estado badge-activo">✅ Activo</span>';
}

function formatFecha(fechaStr) {
    if (!fechaStr) return '—';
    const fecha = new Date(fechaStr);
    return fecha.toLocaleString('es-CL', {
        day: '2-digit',
        month: '2-digit',
        year: 'numeric',
        hour: '2-digit',
        minute: '2-digit'
    });
}

/* ============================================================
   FORMULARIO
   ============================================================ */
function abrirFormulario(usuario = null) {
    usuarioEditando = usuario;
    
    const modal = document.getElementById('modalForm');
    const title = document.getElementById('modalFormTitle');
    const form = document.getElementById('formUsuario');
    
    form.reset();
    document.getElementById('usuarioId').value = '';
    
    if (usuario) {
        title.textContent = 'Editar Usuario';
        document.getElementById('usuarioId').value = usuario.id;
        document.getElementById('rut').value = usuario.rut;
        document.getElementById('nombreCompleto').value = usuario.nombreCompleto;
        document.getElementById('email').value = usuario.email;
        document.getElementById('rol').value = usuario.rol;
        document.getElementById('dependencia').value = usuario.dependencia || '';
    } else {
        title.textContent = 'Nuevo Usuario';
    }
    
    toggleDependencia();
    modal.classList.add('active');
}

function cerrarFormulario() {
    document.getElementById('modalForm').classList.remove('active');
    usuarioEditando = null;
}

function toggleDependencia() {
    const rol = document.getElementById('rol').value;
    const row = document.getElementById('dependenciaRow');
    
    if (rol === 'ENCARGADO_DEPENDENCIA') {
        row.style.display = 'flex';
    } else {
        row.style.display = 'none';
        document.getElementById('dependencia').value = '';
    }
}

async function guardarUsuario(e) {
    e.preventDefault();
    
    const id = document.getElementById('usuarioId').value;
    const btn = document.getElementById('btnGuardar');
    
    const payload = {
        rut: document.getElementById('rut').value.trim(),
        nombreCompleto: document.getElementById('nombreCompleto').value.trim(),
        email: document.getElementById('email').value.trim(),
        rol: document.getElementById('rol').value,
        dependencia: document.getElementById('dependencia').value || null
    };
    
    // Validaciones
    if (!payload.rut || !payload.nombreCompleto || !payload.email || !payload.rol) {
        Toast.warning('Complete todos los campos requeridos', 'Campos faltantes');
        return;
    }
    
    if (payload.rol === 'ENCARGADO_DEPENDENCIA' && !payload.dependencia) {
        Toast.warning('Debe seleccionar una dependencia', 'Campo requerido');
        return;
    }
    
    btn.disabled = true;
    btn.textContent = 'Guardando...';
    
    try {
        const url = id ? `${API}/usuarios/${id}` : `${API}/usuarios`;
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
            id ? 'Usuario actualizado correctamente' : 'Usuario creado correctamente',
            '✓ Éxito'
        );
        
        cerrarFormulario();
        await cargarUsuarios();
        
    } catch (e) {
        console.error(e);
        Toast.error('Error de conexión', 'Error');
    } finally {
        btn.disabled = false;
        btn.textContent = '💾 Guardar';
    }
}

function editarUsuario(id) {
    const u = usuarios.find(x => x.id === id);
    if (u) abrirFormulario(u);
}

/* ============================================================
   ACCIONES
   ============================================================ */
function desactivarUsuario(id, nombre) {
    abrirConfirm(
        'Desactivar usuario',
        `¿Está seguro de desactivar a "${nombre}"? El usuario no podrá iniciar sesión.`,
        async () => {
            try {
                const res = await fetch(`${API}/usuarios/${id}`, {
                    method: 'DELETE',
                    headers: { 'Authorization': `Bearer ${token}` }
                });
                const data = await res.json();
                if (data.success) {
                    Toast.success('Usuario desactivado', '✓ Éxito');
                    cargarUsuarios();
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

async function reactivarUsuario(id, nombre) {
    try {
        const res = await fetch(`${API}/usuarios/${id}/reactivar`, {
            method: 'POST',
            headers: { 'Authorization': `Bearer ${token}` }
        });
        const data = await res.json();
        if (data.success) {
            Toast.success(`Usuario "${nombre}" reactivado`, '✓ Éxito');
            cargarUsuarios();
        } else {
            Toast.error(data.message, 'Error');
        }
    } catch (e) {
        Toast.error('Error de conexión', 'Error');
    }
}

function desbloquearUsuario(id, nombre) {
    abrirConfirm(
        'Desbloquear usuario',
        `¿Desbloquear a "${nombre}"? Se resetearán los intentos fallidos.`,
        async () => {
            try {
                const res = await fetch(`${API}/usuarios/${id}/desbloquear`, {
                    method: 'POST',
                    headers: { 'Authorization': `Bearer ${token}` }
                });
                const data = await res.json();
                if (data.success) {
                    Toast.success('Usuario desbloqueado', '✓ Éxito');
                    cargarUsuarios();
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

function limpiarFiltros() {
    document.getElementById('searchInput').value = '';
    document.getElementById('rolFilter').value = '';
    document.getElementById('estadoFilter').value = '';
    
    state.q = '';
    state.rol = '';
    state.estado = '';
    
    renderizarTabla();
}

/* ============================================================
   HELPERS
   ============================================================ */
function mostrarLoading(show) {
    document.getElementById('loadingOverlay').classList.toggle('active', show);
}

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