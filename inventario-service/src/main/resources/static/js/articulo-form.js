/* ============================================================
   FORMULARIO DE ARTÍCULO - Sistema Bodega DSM
   ============================================================ */

const API = '/api';

if (!window.requireAuth()) throw new Error('Sin autenticación');
Layout.init('inventario');

const token = window.getToken();
const usuario = window.getUsuario();

const urlParams = new URLSearchParams(window.location.search);
const articuloId = urlParams.get('id');
const modo = urlParams.get('mode');

document.addEventListener('DOMContentLoaded', async () => {
    await cargarCategorias();
    
    if (articuloId) {
        document.getElementById('pageTitle').textContent = 
            modo === 'view' ? '👁️ Ver Artículo' : '✏️ Editar Artículo';
        await cargarArticulo(articuloId);
    }
    
    document.getElementById('formArticulo').addEventListener('submit', guardarArticulo);
});

async function cargarCategorias() {
    try {
        const res = await fetch(`${API}/categorias`, {
            headers: { 'Authorization': `Bearer ${token}` }
        });
        const data = await res.json();
        if (data.success) {
            const select = document.getElementById('categoriaId');
            data.data.forEach(c => {
                const opt = document.createElement('option');
                opt.value = c.id;
                opt.textContent = c.nombre;
                select.appendChild(opt);
            });
        }
    } catch (e) { console.error(e); }
}

async function cargarArticulo(id) {
    try {
        const res = await fetch(`${API}/articulos/${id}`, {
            headers: { 'Authorization': `Bearer ${token}` }
        });
        const data = await res.json();
        
        if (!data.success) {
            Toast.error('No se pudo cargar el artículo', 'Error');
            return;
        }
        
        const a = data.data;
        document.getElementById('articuloId').value = a.id;
        document.getElementById('sku').value = a.sku;
        document.getElementById('nombreTecnico').value = a.nombreTecnico;
        document.getElementById('categoriaId').value = a.categoriaId;
        document.getElementById('unidadMedidaBase').value = a.unidadMedidaBase;
        document.getElementById('stockMinimo').value = a.stockMinimo;
        document.getElementById('stockMaximo').value = a.stockMaximo || '';
        document.getElementById('requiereLote').checked = a.requiereLote;
        document.getElementById('requiereVencimiento').checked = a.requiereVencimiento;
        document.getElementById('esDispositivoMedico').checked = a.esDispositivoMedico;
        
        if (modo === 'view') {
            document.querySelectorAll('#formArticulo input, #formArticulo select').forEach(el => {
                el.disabled = true;
            });
            document.getElementById('btnGuardar').style.display = 'none';
        }
    } catch (e) {
        console.error(e);
        Toast.error('Error de conexión', 'Error');
    }
}

async function guardarArticulo(e) {
    e.preventDefault();
    
    const id = document.getElementById('articuloId').value;
    
    const payload = {
        sku: document.getElementById('sku').value.trim() || null,
        nombreTecnico: document.getElementById('nombreTecnico').value.trim(),
        categoriaId: parseInt(document.getElementById('categoriaId').value),
        unidadMedidaBase: document.getElementById('unidadMedidaBase').value,
        stockMinimo: parseInt(document.getElementById('stockMinimo').value) || 0,
        stockMaximo: parseInt(document.getElementById('stockMaximo').value) || null,
        requiereLote: document.getElementById('requiereLote').checked,
        requiereVencimiento: document.getElementById('requiereVencimiento').checked,
        esDispositivoMedico: document.getElementById('esDispositivoMedico').checked
    };
    
    if (!payload.nombreTecnico || !payload.categoriaId) {
        Toast.warning('Complete los campos obligatorios', 'Campos faltantes');
        return;
    }
    
    const btn = document.getElementById('btnGuardar');
    btn.disabled = true;
    btn.textContent = 'Guardando...';
    
    try {
        const url = id ? `${API}/articulos/${id}` : `${API}/articulos`;
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
        
        Toast.success(id ? 'Artículo actualizado' : 'Artículo creado', '✓ Éxito');
        
        setTimeout(() => window.location.href = '/articulos.html', 1000);
        
    } catch (e) {
        console.error(e);
        Toast.error('Error de conexión', 'Error');
    } finally {
        btn.disabled = false;
        btn.textContent = '💾 Guardar';
    }
}