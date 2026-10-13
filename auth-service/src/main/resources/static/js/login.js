/* ============================================================
   LOGIN - Sistema Bodega DSM Santa Cruz
   ============================================================ */

const API_BASE = '/api/auth';
const DASHBOARD_URL = 'http://localhost:8082/dashboard.html';
const DESPACHO_URL = 'http://localhost:8082/despacho.html';  // ⚡ NUEVO
const MFA_TTL_SECONDS = 300;

let sessionToken = null;
let mfaIntervalId = null;

/* ============================================================
   INICIALIZACIÓN
   ============================================================ */
document.addEventListener('DOMContentLoaded', () => {
    const rutInput = document.getElementById('rut');
    
    const rutRecordado = localStorage.getItem('rut_recordado');
    if (rutRecordado) {
        rutInput.value = rutRecordado;
        document.getElementById('recordar').checked = true;
        validarRutField();
    }
    
    // Auto-formato mientras escribe
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
        
        limpiarEstado('group-rut', 'hint-rut');
    });
    
    // Auto-formato al pegar
    rutInput.addEventListener('paste', (e) => {
        setTimeout(() => {
            rutInput.value = formatearRut(rutInput.value);
        }, 0);
    });
    
    // Validar al salir del campo
    rutInput.addEventListener('blur', validarRutField);
    
    // MFA: solo dígitos y Enter
    document.getElementById('codigoMfa').addEventListener('keypress', (e) => {
        if (e.key === 'Enter') {
            e.preventDefault();
            verificarMfa();
        }
    });
    
    document.getElementById('codigoMfa').addEventListener('input', (e) => {
        e.target.value = e.target.value.replace(/\D/g, '').slice(0, 6);
    });
});

/* ============================================================
   FORMATEAR RUT CHILENO
   ============================================================ */
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

/* ============================================================
   VALIDAR RUT CHILENO
   ============================================================ */
function validarRutField() {
    const rut = document.getElementById('rut').value.trim();
    
    if (!rut) {
        limpiarEstado('group-rut', 'hint-rut');
        return false;
    }
    
    if (!/^\d{1,2}\.\d{3}\.\d{3}-[\dK]$/.test(rut)) {
        marcarInvalido('group-rut', 'hint-rut',
            'RUT incompleto. Ejemplo: 12.345.678-5');
        return false;
    }
    
    if (!esRutValido(rut)) {
        marcarInvalido('group-rut', 'hint-rut',
            'RUT inválido. Verifique el dígito verificador.');
        return false;
    }
    
    marcarValido('group-rut', 'hint-rut');
    return true;
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
   LOGIN - PASO 1
   ============================================================ */
document.getElementById('formLogin').addEventListener('submit', async (e) => {
    e.preventDefault();
    
    const rut = document.getElementById('rut').value.trim();
    const password = document.getElementById('password').value;
    const recordar = document.getElementById('recordar').checked;
    const btn = document.getElementById('btnLogin');
    
    console.log('📤 [LOGIN] RUT a enviar:', JSON.stringify(rut));
    
    // Validaciones
    if (!rut) {
        Toast.warning('Ingrese su RUT para continuar', 'Campo requerido');
        document.getElementById('rut').focus();
        shake('rut');
        return;
    }
    
    if (!/^\d{1,2}\.\d{3}\.\d{3}-[\dK]$/.test(rut)) {
        Toast.error('RUT incompleto. Ejemplo válido: 12.345.678-5', 'Formato inválido');
        document.getElementById('rut').focus();
        shake('rut');
        return;
    }
    
    if (!esRutValido(rut)) {
        Toast.error('El RUT ingresado no es válido. Verifique el dígito verificador.', 'RUT inválido');
        document.getElementById('rut').focus();
        shake('rut');
        return;
    }
    
    if (!password) {
        Toast.warning('Ingrese su contraseña para continuar', 'Campo requerido');
        document.getElementById('password').focus();
        shake('password');
        return;
    }
    
    if (password.length < 4) {
        Toast.warning('La contraseña parece demasiado corta', 'Verifique');
        document.getElementById('password').focus();
        return;
    }
    
    btn.disabled = true;
    btn.classList.add('loading');
    btn.querySelector('.btn-text').textContent = 'Verificando...';
    
    try {
        const res = await fetch(`${API_BASE}/login`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ rut, password })
        });
        
        const data = await res.json();
        console.log('📥 [LOGIN] Respuesta:', data);
        
        if (!res.ok || !data.success) {
            Toast.error(data.message || 'No se pudo iniciar sesión', 'Error de autenticación');
            document.querySelector('.login-card').classList.add('shake');
            setTimeout(() => document.querySelector('.login-card').classList.remove('shake'), 500);
            return;
        }
        
        if (recordar) {
            localStorage.setItem('rut_recordado', rut);
        } else {
            localStorage.removeItem('rut_recordado');
        }
        
        if (data.stage === 'MFA_REQUIRED') {
            sessionToken = data.sessionToken;
            Toast.info(data.message, '📧 Código enviado');
            
            document.getElementById('step1').style.display = 'none';
            document.getElementById('step2').style.display = 'block';
            document.getElementById('mfaMessage').textContent = data.message;
            
            setTimeout(() => document.getElementById('codigoMfa').focus(), 100);
            iniciarCountdownMfa();
            
        } else if (data.stage === 'AUTHENTICATED') {
            Toast.success(`Bienvenido/a, ${data.usuario.nombreCompleto}`, '✓ Acceso concedido');
            setTimeout(() => guardarSesion(data), 800);
        }
        
    } catch (err) {
        console.error('❌ [LOGIN] Error:', err);
        Toast.error('No se pudo conectar con el servidor. Intente más tarde.', 'Error de conexión');
    } finally {
        btn.disabled = false;
        btn.classList.remove('loading');
        btn.querySelector('.btn-text').textContent = 'Iniciar Sesión';
    }
});

/* ============================================================
   VERIFICAR MFA - PASO 2
   ============================================================ */
async function verificarMfa() {
    const input = document.getElementById('codigoMfa');
    const codigo = input.value.trim();
    const btn = document.getElementById('btnVerificar');
    
    if (!codigo) {
        Toast.warning('Ingrese el código de 6 dígitos que recibió', 'Código vacío');
        input.focus();
        return;
    }
    
    if (codigo.length !== 6) {
        Toast.warning('El código debe tener exactamente 6 dígitos', 'Código incompleto');
        input.focus();
        return;
    }
    
    btn.disabled = true;
    btn.classList.add('loading');
    btn.querySelector('.btn-text').textContent = 'Verificando...';
    
    try {
        const res = await fetch(`${API_BASE}/mfa/verify`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ sessionToken, codigo })
        });
        
        const data = await res.json();
        console.log('📥 [MFA] Respuesta:', data);
        
        if (!res.ok || !data.success) {
            Toast.error(data.message || 'Código inválido o expirado', 'Verificación fallida');
            input.value = '';
            input.focus();
            document.getElementById('group-mfa').classList.add('shake');
            setTimeout(() => document.getElementById('group-mfa').classList.remove('shake'), 500);
            return;
        }
        
        detenerCountdown();
        Toast.success(`Bienvenido/a, ${data.usuario.nombreCompleto}`, '✓ Acceso concedido');
        setTimeout(() => guardarSesion(data), 800);
        
    } catch (err) {
        console.error('❌ [MFA] Error:', err);
        Toast.error('Error de conexión al verificar el código', 'Error');
    } finally {
        btn.disabled = false;
        btn.classList.remove('loading');
        btn.querySelector('.btn-text').textContent = 'Verificar';
    }
}

/* ============================================================
   COUNTDOWN MFA
   ============================================================ */
function iniciarCountdownMfa() {
    detenerCountdown();
    let restante = MFA_TTL_SECONDS;
    const el = document.getElementById('mfaCountdown');
    
    const actualizar = () => {
        if (restante <= 0) {
            el.textContent = '⚠ Código expirado. Vuelva a iniciar sesión.';
            el.className = 'mfa-countdown expired';
            detenerCountdown();
            return;
        }
        
        const min = String(Math.floor(restante / 60)).padStart(2, '0');
        const seg = String(restante % 60).padStart(2, '0');
        el.textContent = `⏱ El código expira en ${min}:${seg}`;
        el.className = 'mfa-countdown' + (restante <= 60 ? ' warning' : '');
        restante--;
    };
    
    actualizar();
    mfaIntervalId = setInterval(actualizar, 1000);
}

function detenerCountdown() {
    if (mfaIntervalId) {
        clearInterval(mfaIntervalId);
        mfaIntervalId = null;
    }
}

/* ============================================================
   GUARDAR SESIÓN Y REDIRIGIR
   ⚡ NUEVO: Redirige según rol
   - ENCARGADO_DEPENDENCIA → /despacho.html
   - Otros roles → /dashboard.html
   ============================================================ */
function guardarSesion(data) {
    // Guardar en el localStorage del auth-service (por si acaso)
    localStorage.setItem('token', data.token);
    localStorage.setItem('tokenType', data.tokenType || 'Bearer');
    localStorage.setItem('usuario', JSON.stringify(data.usuario));
    localStorage.setItem('expiresIn', data.expiresIn);
    localStorage.setItem('loginTime', Date.now().toString());
    
    // ⚡ Construir URL con el token como parámetro
    const params = new URLSearchParams({
        token: data.token,
        nombre: data.usuario.nombreCompleto,
        rol: data.usuario.rol,
        rut: data.usuario.rut,
        email: data.usuario.email || ''
    });
    
    // ⚡ Redirigir según rol
    const esEncargadoDependencia = data.usuario.rol === 'ENCARGADO_DEPENDENCIA';
    
    if (esEncargadoDependencia) {
        console.log('✅ [LOGIN] Encargado de Dependencia, redirigiendo a Pedidos (despacho.html)...');
        window.location.href = `${DESPACHO_URL}?${params.toString()}`;
    } else {
        console.log('✅ [LOGIN] Sesión guardada, redirigiendo al Dashboard...');
        window.location.href = `${DASHBOARD_URL}?${params.toString()}`;
    }
}

/* ============================================================
   HELPERS DE VALIDACIÓN VISUAL
   ============================================================ */
function marcarValido(groupId, hintId) {
    const g = document.getElementById(groupId);
    const h = document.getElementById(hintId);
    if (g) {
        g.classList.remove('invalid');
        g.classList.add('valid');
    }
    if (h) h.classList.remove('visible');
}

function marcarInvalido(groupId, hintId, mensaje) {
    const g = document.getElementById(groupId);
    const h = document.getElementById(hintId);
    if (g) {
        g.classList.remove('valid');
        g.classList.add('invalid');
    }
    if (h) {
        h.textContent = mensaje;
        h.classList.add('visible');
    }
}

function limpiarEstado(groupId, hintId) {
    const g = document.getElementById(groupId);
    const h = document.getElementById(hintId);
    if (g) g.classList.remove('valid', 'invalid');
    if (h) h.classList.remove('visible');
}

function shake(elementId) {
    const el = document.getElementById(elementId);
    if (!el) return;
    el.classList.add('shake');
    setTimeout(() => el.classList.remove('shake'), 400);
}

/* ============================================================
   HELPERS UI
   ============================================================ */
function togglePassword() {
    const input = document.getElementById('password');
    input.type = input.type === 'password' ? 'text' : 'password';
}

function volverPaso1() {
    detenerCountdown();
    document.getElementById('step1').style.display = 'block';
    document.getElementById('step2').style.display = 'none';
    document.getElementById('codigoMfa').value = '';
    document.getElementById('mfaCountdown').textContent = '';
    sessionToken = null;
    setTimeout(() => document.getElementById('password').focus(), 100);
}