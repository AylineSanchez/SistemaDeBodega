const API_BASE = '/api/auth';

document.addEventListener('DOMContentLoaded', () => {
    const input = document.getElementById('identificador');
    
    // Auto-formato RUT si NO contiene @ (es decir, si es RUT y no email)
    input.addEventListener('input', (e) => {
        const valor = e.target.value;
        if (valor.includes('@')) {
            // Es un email, no formatear
            return;
        }
        const formateado = formatearRut(valor);
        if (formateado !== valor) {
            e.target.value = formateado;
        }
        limpiarEstado('group-id', 'hint-id');
    });
    
    input.addEventListener('paste', (e) => {
        setTimeout(() => {
            const valor = input.value;
            if (!valor.includes('@')) {
                input.value = formatearRut(valor);
            }
        }, 0);
    });
});

function formatearRut(valor) {
    if (!valor) return '';
    const limpio = valor.replace(/[^0-9kK]/g, '').toUpperCase();
    if (!limpio || limpio.length <= 1) return limpio;
    
    const cuerpo = limpio.slice(0, -1);
    const dv = limpio.slice(-1);
    const cuerpoFormateado = cuerpo.replace(/\B(?=(\d{3})+(?!\d))/g, '.');
    return `${cuerpoFormateado}-${dv}`;
}

document.getElementById('formForgot').addEventListener('submit', async (e) => {
    e.preventDefault();
    
    let identificador = document.getElementById('identificador').value.trim();
    const btn = document.getElementById('btnEnviar');
    
    if (!identificador) {
        Toast.warning('Ingrese su RUT o email', 'Campo requerido');
        document.getElementById('identificador').focus();
        return;
    }
    
    const esEmail = /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(identificador);
    const esRut = /^\d{1,2}\.\d{3}\.\d{3}-[\dK]$/.test(identificador);
    
    if (!esEmail && !esRut) {
        Toast.error('Ingrese un RUT o email válido', 'Formato incorrecto');
        return;
    }
    
    btn.disabled = true;
    btn.classList.add('loading');
    btn.querySelector('.btn-text').textContent = 'Enviando...';
    
    try {
        const res = await fetch(`${API_BASE}/password/forgot`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ identificador })
        });
        
        const data = await res.json();
        
        if (!res.ok || !data.success) {
            Toast.error(data.message || 'No se pudo procesar la solicitud', 'Error');
            return;
        }
        
        document.querySelector('.login-card').innerHTML = `
            <div class="login-logos">
                <div class="logo logo-muni">Muni</div>
                <div class="logo logo-cesfam">CESFAM</div>
            </div>
            <div class="mfa-info">
                <span class="mfa-icon">📧</span>
                <h1 class="login-title">Revise su correo</h1>
                <p class="login-subtitle">
                    Si el identificador existe en nuestro sistema,
                    recibirá un email con las instrucciones para
                    restablecer su contraseña.
                </p>
                <p style="color: #95a5a6; font-size: 0.85em; margin-top: 15px;">
                    El enlace expira en 15 minutos.
                </p>
                <p style="color: #95a5a6; font-size: 0.8em; margin-top: 10px;">
                    Si no lo ve, revise su carpeta de spam.
                </p>
            </div>
            <a href="/login.html" class="btn-login-primary"
               style="display:block; text-align:center; text-decoration:none; margin-top: 25px;">
                Volver al inicio de sesión
            </a>
        `;
        
        Toast.success('Revise su bandeja de entrada', '✓ Email enviado');
        
    } catch (err) {
        console.error(err);
        Toast.error('Error de conexión con el servidor', 'Error');
    } finally {
        btn.disabled = false;
        btn.classList.remove('loading');
        btn.querySelector('.btn-text').textContent = 'Enviar Instrucciones';
    }
});

function limpiarEstado(groupId, hintId) {
    const g = document.getElementById(groupId);
    const h = document.getElementById(hintId);
    if (g) g.classList.remove('valid', 'invalid');
    if (h) h.classList.remove('visible');
}