/* ============================================================
   TOAST - Sistema de notificaciones
   Sistema Bodega DSM Santa Cruz
   Uso:
     Toast.success('Mensaje');
     Toast.error('Mensaje');
     Toast.warning('Mensaje');
     Toast.info('Mensaje');
     Toast.loading('Cargando...')  → devuelve id
     Toast.dismiss(id);
     Toast.promise(promesa, { loading, success, error });
   ============================================================ */

(function (global) {
    'use strict';

    // ---------- Configuración ----------
    const CONFIG = {
        duration: 4000,        // ms por defecto
        position: 'top-right', // top-right | top-center | bottom-right
        maxVisible: 4          // máximo de toasts simultáneos
    };

    // ---------- Contenedor raíz ----------
    let container = null;

    function ensureContainer() {
        if (container && document.body.contains(container)) return container;
        container = document.createElement('div');
        container.id = 'toast-container';
        container.className = `toast-container toast-${CONFIG.position}`;
        document.body.appendChild(container);
        return container;
    }

    // ---------- Iconos SVG por tipo ----------
    const ICONS = {
        success: `
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"
                 stroke-linecap="round" stroke-linejoin="round">
                <path d="M20 6L9 17l-5-5"/>
            </svg>`,
        error: `
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"
                 stroke-linecap="round" stroke-linejoin="round">
                <circle cx="12" cy="12" r="10"/>
                <path d="M15 9l-6 6M9 9l6 6"/>
            </svg>`,
        warning: `
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"
                 stroke-linecap="round" stroke-linejoin="round">
                <path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"/>
                <line x1="12" y1="9" x2="12" y2="13"/>
                <line x1="12" y1="17" x2="12.01" y2="17"/>
            </svg>`,
        info: `
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"
                 stroke-linecap="round" stroke-linejoin="round">
                <circle cx="12" cy="12" r="10"/>
                <line x1="12" y1="16" x2="12" y2="12"/>
                <line x1="12" y1="8" x2="12.01" y2="8"/>
            </svg>`,
        loading: `
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"
                 stroke-linecap="round" stroke-linejoin="round" class="toast-spinner">
                <path d="M21 12a9 9 0 1 1-6.219-8.56"/>
            </svg>`
    };

    // ---------- Crear elemento toast ----------
    function createToast({ type, title, message, duration, dismissible = true }) {
        const el = document.createElement('div');
        el.className = `toast toast-${type}`;
        el.setAttribute('role', type === 'error' ? 'alert' : 'status');
        el.setAttribute('aria-live', type === 'error' ? 'assertive' : 'polite');

        el.innerHTML = `
            <div class="toast-icon">${ICONS[type] || ICONS.info}</div>
            <div class="toast-body">
                ${title ? `<div class="toast-title">${escapeHtml(title)}</div>` : ''}
                <div class="toast-message">${escapeHtml(message || '')}</div>
            </div>
            ${dismissible ? `
                <button class="toast-close" type="button" aria-label="Cerrar">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"
                         stroke-linecap="round" stroke-linejoin="round">
                        <path d="M18 6L6 18M6 6l12 12"/>
                    </svg>
                </button>` : ''}
            <div class="toast-progress" style="animation-duration:${duration}ms"></div>
        `;

        // Cerrar al hacer click
        if (dismissible) {
            el.querySelector('.toast-close').addEventListener('click', () => dismiss(el));
        }

        // Auto-cerrar
        if (duration > 0) {
            setTimeout(() => dismiss(el), duration);
        }

        return el;
    }

    // ---------- Mostrar toast ----------
    function show({ type = 'info', title, message, duration }) {
        const box = ensureContainer();
        const dur = duration !== undefined ? duration : CONFIG.duration;

        // Límite de toasts visibles
        const current = box.querySelectorAll('.toast');
        if (current.length >= CONFIG.maxVisible) {
            dismiss(current[0]);
        }

        const el = createToast({ type, title, message, duration: dur });
        box.appendChild(el);

        // Animación de entrada
        requestAnimationFrame(() => el.classList.add('toast-visible'));

        return el;
    }

    // ---------- Cerrar toast ----------
    function dismiss(el) {
        if (!el || el.classList.contains('toast-hiding')) return;
        el.classList.add('toast-hiding');
        el.classList.remove('toast-visible');

        setTimeout(() => {
            if (el.parentNode) el.parentNode.removeChild(el);
        }, 300);
    }

    // ---------- Helpers ----------
    function escapeHtml(str) {
        if (str == null) return '';
        return String(str)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#039;');
    }

    // ---------- API pública ----------
    const Toast = {
        success: (message, title = 'Operación exitosa') =>
            show({ type: 'success', title, message }),

        error: (message, title = 'Error') =>
            show({ type: 'error', title, message, duration: 6000 }),

        warning: (message, title = 'Atención') =>
            show({ type: 'warning', title, message, duration: 5000 }),

        info: (message, title = null) =>
            show({ type: 'info', title, message }),

        loading: (message = 'Procesando...', title = null) =>
            show({ type: 'loading', title, message, duration: 0, dismissible: false }),

        dismiss: (el) => dismiss(el),

        clear: () => {
            const c = ensureContainer();
            c.querySelectorAll('.toast').forEach(dismiss);
        },

        // Muestra loading → success/error según resolución de promesa
        promise: async (promise, { loading, success, error }) => {
            const loadingToast = show({
                type: 'loading',
                message: loading || 'Procesando...',
                duration: 0,
                dismissible: false
            });

            try {
                const result = await promise;
                dismiss(loadingToast);
                if (success) show({ type: 'success', title: 'Éxito', message: success });
                return result;
            } catch (err) {
                dismiss(loadingToast);
                const errMsg = error || err?.message || 'Error inesperado';
                show({ type: 'error', title: 'Error', message: errMsg, duration: 6000 });
                throw err;
            }
        }
    };

    // Exponer globalmente
    global.Toast = Toast;

})(window);