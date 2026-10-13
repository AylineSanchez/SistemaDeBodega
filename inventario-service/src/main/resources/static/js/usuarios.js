/* ============================================================
   GESTIÓN DE USUARIOS - Sistema Bodega DSM
   ============================================================ */

const API = '/api';

if (!window.requireAuth()) throw new Error('Sin autenticación');
Layout.init('usuarios');

const token = window.getToken();
const usuarioActual = window.getUsuario();

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

// Descripciones de roles para mostrar debajo del select
const ROL_INFO = {
    JEFATURA: {
        icon: '👑',
        desc: 'Control global del sistema, reportes y gestión de usuarios.',
        class: 'jefatura'
    },
    OPERATIVO: {
        icon: '📦',
        desc: 'Operación diaria: recepción, despacho, inventario.',
        class: 'operativo'
    },
    AUDITOR: {
        icon: '🛡️',
        desc: 'Trazabilidad sanitaria NT 226 y gestión de bloqueos ISP.',
        class: 'auditor'
    },
    ENCARGADO_DEPENDENCIA: {
        icon: '🏥',
        desc: 'Solo lectura de stock y creación de pedidos para su dependencia.',
        class: 'encargado'
    }
};

/* ============================================================
   INICIALIZACIÓN
   ============================================================ */
document.addEventListener('DOMContentLoaded', async () => {
    await cargarUsuarios();

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

    document.getElementById('formUsuario').addEventListener('submit', guardarUsuario);

    // Auto-formato del RUT
    configurarAutoFormatoRut();

    // Validación del email
    configurarValidacionEmail();
});

/* ============================================================
   AUTO-FORMATO DE RUT
   ============================================================ */
function configurarAutoFormatoRut() {
    const rutInput = document.getElementById('rut');

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

        limpiarValidacion('field-rut', 'hint-rut', '💡 Solo números, el sistema agrega puntos y guion');
    });

    rutInput.addEventListener('paste', (e) => {
        setTimeout(() => {
            rutInput.value = formatearRut(rutInput.value);
        }, 0);
    });

    rutInput.addEventListener('blur', () => {
        const rut = rutInput.value.trim();
        if (!rut) return;

        if (!esRutValido(rut)) {
            marcarInvalido('field-rut', 'hint-rut',
                '❌ RUT inválido. Verifique el dígito verificador.');
        } else {
            marcarValido('field-rut', 'hint-rut',
                '✓ RUT válido');
        }
    });
}

function formatearRut(valor) {
    if (!valor) return '';
    const limpio = valor.replace(/[^0-9kK]/g, '').toUpperCase();
    if (!limpio) return '';
    if (limpio.length <= 1) return limpio;

    const cuerpo = limpio.slice(0, -1);
    const dv = limpio.slice(-1);
    const cuerpoFormateado = cuerpo.replace(/\B(?=(\d{3})+(?!\d))/g, '.');

    return `${cuerpoFormateado}-${dv}`;
}

function esRutValido(rut) {
    const limpio = rut.replace(/\./g, '').replace('-', '');
    if (limpio.length < 2) return false;

    const cuerpo = limpio.slice(0, -1);
    const dv = limpio.slice(-1).toUpperCase();

    if (!/^\d+$/.test(cuerpo)) return false;

    let suma = 0;
    let multiplo = 2;
    for (let i = cuerpo.length - 1; i >= 0; i--) {
        suma += parseInt(cuerpo.charAt(i)) * multiplo;
        multiplo = multiplo === 7 ? 2 : multiplo + 1;
    }
    const resto = suma % 11;
    const dvEsperado = 11 - resto;

    const dvCalc = dvEsperado === 11 ? '0' : dvEsperado === 10 ? 'K' : String(dvEsperado);
    return dv === dvCalc;
}

/* ============================================================
   VALIDACIÓN DE EMAIL
   ============================================================ */
function configurarValidacionEmail() {
    const emailInput = document.getElementById('email');

    emailInput.addEventListener('input', () => {
        limpiarValidacion('field-email', 'hint-email',
            'Se enviarán notificaciones a este correo');
    });

    emailInput.addEventListener('blur', () => {
        const email = emailInput.value.trim();
        if (!email) return;

        if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
            marcarInvalido('field-email', 'hint-email',
                '❌ Ingrese un email válido (ej: usuario@dsm.cl)');
        } else {
            marcarValido('field-email', 'hint-email',
                '✓ Email válido');
        }
    });
}

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
                <td class="name-cell">${escapeHtml(u.nombreCompleto)}</td>
                <td style="font-size:0.85em;color:#7f8c8d;">${escapeHtml(u.email)}</td>
                <td><span class="badge-rol ${rolClass}">${rolLabel}</span></td>
                <td style="font-size:0.85em;color:#7f8c8d;">
                    ${u.dependencia ? escapeHtml(u.dependencia) : '—'}
                </td>
                <td>${estadoBadge}</td>
                <td style="font-size:0.82em;color:#7f8c8d;">${ultimoAcceso}</td>
                <td class="actions-cell">
                    <button class="btn-icon edit" title="Editar"
                            onclick="editarUsuario(${u.id})">✏️</button>
                    ${u.bloqueado
                        ? `<button class="btn-icon" title="Desbloquear"
                                   style="color:#f39c12;"
                                   onclick="desbloquearUsuario(${u.id}, '${escapeAttr(u.nombreCompleto)}')">🔓</button>`
                        : ''
                    }
                    ${u.activo
                        ? `<button class="btn-icon delete" title="Desactivar"
                                   onclick="desactivarUsuario(${u.id}, '${escapeAttr(u.nombreCompleto)}')">🚫</button>`
                        : `<button class="btn-icon" title="Reactivar"
                                   style="color:#27ae60;"
                                   onclick="reactivarUsuario(${u.id}, '${escapeAttr(u.nombreCompleto)}')">♻️</button>`
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
    const header = document.getElementById('modalFormHeader');
    const icon = document.getElementById('modalFormIcon');
    const title = document.getElementById('modalFormTitle');
    const form = document.getElementById('formUsuario');

    form.reset();
    document.getElementById('usuarioId').value = '';
    document.getElementById('rolInfoBox').innerHTML = '';

    // Limpiar validaciones
    limpiarValidacion('field-rut', 'hint-rut', '💡 Solo números, el sistema agrega puntos y guion');
    limpiarValidacion('field-email', 'hint-email', 'Se enviarán notificaciones a este correo');
    document.getElementById('field-nombre').classList.remove('valid', 'invalid');
    document.getElementById('field-rol').classList.remove('valid', 'invalid');

    if (usuario) {
        // Modo edición
        header.className = 'modal-header-custom editar';
        icon.textContent = '✏️';
        title.textContent = 'Editar Usuario';

        document.getElementById('usuarioId').value = usuario.id;
        document.getElementById('rut').value = usuario.rut;
        document.getElementById('nombreCompleto').value = usuario.nombreCompleto;
        document.getElementById('email').value = usuario.email;
        document.getElementById('rol').value = usuario.rol;
        document.getElementById('dependencia').value = usuario.dependencia || '';

        // Marcar como validados los campos que ya tienen datos
        if (usuario.rut) marcarValido('field-rut', 'hint-rut', '✓ RUT actual');
        if (usuario.email) marcarValido('field-email', 'hint-email', '✓ Email actual');
    } else {
        // Modo nuevo
        header.className = 'modal-header-custom nuevo';
        icon.textContent = '➕';
        title.textContent = 'Nuevo Usuario';
    }

    toggleDependencia();
    modal.classList.add('active');

    setTimeout(() => document.getElementById('rut').focus(), 150);
}

function cerrarFormulario() {
    document.getElementById('modalForm').classList.remove('active');
    usuarioEditando = null;
}

function toggleDependencia() {
    const rol = document.getElementById('rol').value;
    const section = document.getElementById('dependenciaSection');
    const rolInfoBox = document.getElementById('rolInfoBox');

    // Mostrar dependencia solo si es ENCARGADO_DEPENDENCIA
    if (rol === 'ENCARGADO_DEPENDENCIA') {
        section.style.display = 'block';
    } else {
        section.style.display = 'none';
        document.getElementById('dependencia').value = '';
    }

    // Mostrar info del rol
    if (rol && ROL_INFO[rol]) {
        const info = ROL_INFO[rol];
        rolInfoBox.innerHTML = `
            <div class="rol-info ${info.class}">
                <span>${info.icon}</span>
                <span>${info.desc}</span>
            </div>
        `;
    } else {
        rolInfoBox.innerHTML = '';
    }

    // Marcar el campo rol como válido
    if (rol) {
        document.getElementById('field-rol').classList.add('valid');
    } else {
        document.getElementById('field-rol').classList.remove('valid');
    }
}

async function guardarUsuario(e) {
    e.preventDefault();

    const id = document.getElementById('usuarioId').value;
    const btn = document.getElementById('btnGuardar');

    const rut = document.getElementById('rut').value.trim();
    const nombreCompleto = document.getElementById('nombreCompleto').value.trim();
    const email = document.getElementById('email').value.trim();
    const rol = document.getElementById('rol').value;
    const dependencia = document.getElementById('dependencia').value || null;

    // Validaciones
    if (!rut || !nombreCompleto || !email || !rol) {
        Toast.warning('Complete todos los campos requeridos', 'Campos faltantes');
        return;
    }

    if (!esRutValido(rut)) {
        Toast.error('El RUT ingresado no es válido. Verifique el dígito verificador.', 'RUT inválido');
        document.getElementById('rut').focus();
        return;
    }

    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
        Toast.error('Ingrese un email válido', 'Email inválido');
        document.getElementById('email').focus();
        return;
    }

    if (rol === 'ENCARGADO_DEPENDENCIA' && !dependencia) {
        Toast.warning('Debe seleccionar una dependencia para este rol', 'Campo requerido');
        document.getElementById('dependencia').focus();
        return;
    }

    const payload = { rut, nombreCompleto, email, rol, dependencia };

    btn.disabled = true;
    btn.innerHTML = '<span class="btn-spinner"></span>Guardando...';

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
        btn.innerHTML = '💾 Guardar Usuario';
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
        '🚫 Desactivar Usuario',
        `¿Está seguro de desactivar a "${nombre}"? El usuario no podrá iniciar sesión hasta que sea reactivado.`,
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
        '🔓 Desbloquear Usuario',
        `¿Desbloquear a "${nombre}"? Se resetearán los intentos fallidos y podrá iniciar sesión.`,
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
   HELPERS DE VALIDACIÓN VISUAL
   ============================================================ */
function marcarValido(fieldId, hintId, mensaje) {
    const f = document.getElementById(fieldId);
    const h = document.getElementById(hintId);
    if (f) { f.classList.remove('invalid'); f.classList.add('valid'); }
    if (h) {
        h.classList.remove('error');
        h.classList.add('success');
        h.textContent = mensaje;
    }
}

function marcarInvalido(fieldId, hintId, mensaje) {
    const f = document.getElementById(fieldId);
    const h = document.getElementById(hintId);
    if (f) { f.classList.remove('valid'); f.classList.add('invalid'); }
    if (h) {
        h.classList.remove('success');
        h.classList.add('error');
        h.textContent = mensaje;
    }
}

function limpiarValidacion(fieldId, hintId, mensajeDefault) {
    const f = document.getElementById(fieldId);
    const h = document.getElementById(hintId);
    if (f) f.classList.remove('valid', 'invalid');
    if (h) {
        h.classList.remove('error', 'success');
        h.textContent = mensajeDefault;
    }
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
        const later = () => {
            clearTimeout(timeout);
            func(...args);
        };
        clearTimeout(timeout);
        timeout = setTimeout(later, wait);
    };
}