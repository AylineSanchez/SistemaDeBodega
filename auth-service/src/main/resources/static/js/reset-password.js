const API_BASE = '/api/auth';

const urlParams = new URLSearchParams(window.location.search);
const token = urlParams.get('token');

if (!token) {
    Toast.error('Enlace inválido o incompleto', 'Error');
    setTimeout(() => window.location.href = '/login.html', 2000);
}

document.getElementById('nuevaPassword').addEventListener('input', (e) => {
    validarReglas(e.target.value);
});

document.getElementById('confirmarPassword').addEventListener('input', () => {
    const pass = document.getElementById('nuevaPassword').value;
    const confirm = document.getElementById('confirmarPassword').value;
    
    if (confirm && pass !== confirm) {
        marcarInvalido('group-confirm', 'hint-confirm', 'Las contraseñas no coinciden');
    } else if (confirm && pass === confirm) {
        marcarValido('group-confirm', 'hint-confirm');
    }
});

function validarReglas(pass) {
    const reglas = {
        length:  pass.length >= 8,
        upper:   /[A-Z]/.test(pass),
        number:  /[0-9]/.test(pass),
        special: /[!@#$%^&*()_+\-=\[\]{};':"\\|,.<>\/?]/.test(pass)
    };
    
    document.querySelectorAll('.password-rules .rule').forEach(el => {
        el.classList.toggle('ok', reglas[el.dataset.rule]);
    });
    
    return Object.values(reglas).every(v => v);
}

document.getElementById('formReset').addEventListener('submit', async (e) => {
    e.preventDefault();
    
    const nueva = document.getElementById('nuevaPassword').value;
    const confirmar = document.getElementById('confirmarPassword').value;
    const btn = document.getElementById('btnReset');
    
    if (!validarReglas(nueva)) {
        Toast.error('La contraseña no cumple con los requisitos mínimos', 'Contraseña débil');
        return;
    }
    
    if (nueva !== confirmar) {
        Toast.error('Las contraseñas no coinciden', 'Verifique');
        return;
    }
    
    btn.disabled = true;
    btn.classList.add('loading');
    btn.querySelector('.btn-text').textContent = 'Actualizando...';
    
    try {
        const res = await fetch(`${API_BASE}/password/reset`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                token,
                nuevaPassword: nueva,
                confirmarPassword: confirmar
            })
        });
        
        const data = await res.json();
        
        if (!res.ok || !data.success) {
            Toast.error(data.message || 'No se pudo restablecer', 'Error');
            return;
        }
        
        Toast.success('Contraseña actualizada. Redirigiendo...', '✓ Éxito');
        setTimeout(() => window.location.href = '/login.html', 1800);
        
    } catch (err) {
        console.error(err);
        Toast.error('Error de conexión', 'Error');
    } finally {
        btn.disabled = false;
        btn.classList.remove('loading');
        btn.querySelector('.btn-text').textContent = 'Restablecer Contraseña';
    }
});

function togglePass(id) {
    const input = document.getElementById(id);
    input.type = input.type === 'password' ? 'text' : 'password';
}

function marcarValido(groupId, hintId) {
    const g = document.getElementById(groupId);
    const h = document.getElementById(hintId);
    if (g) { g.classList.remove('invalid'); g.classList.add('valid'); }
    if (h) h.classList.remove('visible');
}

function marcarInvalido(groupId, hintId, msg) {
    const g = document.getElementById(groupId);
    const h = document.getElementById(hintId);
    if (g) { g.classList.remove('valid'); g.classList.add('invalid'); }
    if (h) { h.textContent = msg; h.classList.add('visible'); }
}