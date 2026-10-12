/* ============================================================
   SESSION HELPER - Sistema Bodega DSM Santa Cruz
   ============================================================
   Propósito:
     Lee el token JWT y los datos del usuario desde la URL
     (query string) y los guarda en localStorage de ESTE origen.
     
   ¿Por qué existe?
     El auth-service corre en puerto 8081 y el inventario-service
     en 8082. Son orígenes distintos, por lo que localStorage NO
     se comparte. El login redirige con:
     
       http://localhost:8082/dashboard.html?token=xxx&rol=xxx&...
     
     Este script debe cargarse ANTES de layout.js en TODAS las
     páginas del sistema para que el token esté disponible.
   
   Uso:
     <script src="/js/session-helper.js"></script>
     <script src="/js/layout.js"></script>
     <script src="/js/dashboard.js"></script>
   ============================================================ */

(function () {
    'use strict';
    
    // ============================================================
    // 1. LEER TOKEN DE LA URL
    // ============================================================
    const urlParams = new URLSearchParams(window.location.search);
    const urlToken = urlParams.get('token');
    
    if (urlToken && urlToken !== 'null' && urlToken.trim() !== '') {
        
        // Guardar token y datos del usuario en localStorage
        localStorage.setItem('token', urlToken);
        localStorage.setItem('tokenType', 'Bearer');
        localStorage.setItem('loginTime', Date.now().toString());
        
        // Guardar info del usuario
        const usuario = {
            nombreCompleto: urlParams.get('nombre') || 'Usuario',
            rol: urlParams.get('rol') || '',
            rut: urlParams.get('rut') || '',
            email: urlParams.get('email') || ''
        };
        localStorage.setItem('usuario', JSON.stringify(usuario));
        
        console.log('✅ [SESSION] Token guardado desde URL para:', usuario.nombreCompleto);
        
        // ============================================================
        // 2. LIMPIAR LA URL
        //    Quita el ?token=... para que no quede visible en el
        //    navegador ni en el historial.
        // ============================================================
        const urlLimpia = window.location.pathname;
        window.history.replaceState({}, document.title, urlLimpia);
        
    } else {
        // No venía token en la URL, verificar si ya hay uno en localStorage
        const tokenExistente = localStorage.getItem('token');
        if (tokenExistente && tokenExistente !== 'null') {
            console.log('ℹ️ [SESSION] Token ya estaba en localStorage');
        } else {
            console.log('⚠️ [SESSION] Sin token en URL ni en localStorage');
        }
    }
    
    // ============================================================
    // 3. EXPONER HELPERS GLOBALES
    // ============================================================
    
    /**
     * Devuelve el token JWT actual o null.
     */
    window.getToken = function() {
        const t = localStorage.getItem('token');
        return (t && t !== 'null') ? t : null;
    };
    
    /**
     * Devuelve el objeto del usuario actual.
     */
    window.getUsuario = function() {
        try {
            return JSON.parse(localStorage.getItem('usuario') || '{}');
        } catch (e) {
            return {};
        }
    };
    
    /**
     * Verifica si hay una sesión activa.
     */
    window.isAuthenticated = function() {
        return window.getToken() !== null;
    };
    
    /**
     * Verifica si el usuario tiene uno de los roles dados.
     * Ej: tieneRol('JEFATURA', 'OPERATIVO')
     */
    window.tieneRol = function(...roles) {
        const usuario = window.getUsuario();
        return roles.includes(usuario.rol);
    };
    
    /**
     * Cierra la sesión y redirige al login del auth-service.
     */
    window.cerrarSesion = function() {
        localStorage.removeItem('token');
        localStorage.removeItem('tokenType');
        localStorage.removeItem('usuario');
        localStorage.removeItem('expiresIn');
        localStorage.removeItem('loginTime');
        window.location.href = 'http://localhost:8081/login.html';
    };
    
    /**
     * Redirige al login si no hay sesión activa.
     * Se puede llamar al inicio de cada página.
     */
    window.requireAuth = function() {
        if (!window.isAuthenticated()) {
            console.warn('⚠️ [SESSION] No autenticado, redirigiendo al login');
            window.location.href = 'http://localhost:8081/login.html';
            return false;
        }
        return true;
    };
    
    /**
     * Redirige al dashboard si el usuario NO tiene uno de los roles dados.
     * Ej: requireRol('JEFATURA')
     */
    window.requireRol = function(...roles) {
        if (!window.requireAuth()) return false;
        
        const usuario = window.getUsuario();
        if (!roles.includes(usuario.rol)) {
            console.warn(`⚠️ [SESSION] Rol '${usuario.rol}' no autorizado. Requeridos: ${roles.join(', ')}`);
            window.location.href = '/dashboard.html';
            return false;
        }
        return true;
    };
    
})();