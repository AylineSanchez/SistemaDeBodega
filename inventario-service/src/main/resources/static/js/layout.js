/* ============================================================
   LAYOUT - Header + Sidebar unificado
   Sistema Bodega DSM Santa Cruz
   ============================================================
   NOTA: Este script asume que session-helper.js YA se ejecutó
   y el token/usuario están en localStorage.
   NO redirige al login (eso lo maneja el session-helper).
   ============================================================ */

const Layout = {
    
    menuItems: [
        { 
            id: 'dashboard',    
            icon: '🏠', 
            label: 'Dashboard',          
            href: '/dashboard.html',    
            // ⚡ Encargado Dependencia NO ve el Dashboard
            roles: ['JEFATURA', 'OPERATIVO', 'AUDITOR'] 
        },
        { 
            id: 'recepcion',    
            icon: '📥', 
            label: 'Recepción',          
            href: '/recepcion.html',    
            roles: ['JEFATURA', 'OPERATIVO'] 
        },
        { 
            id: 'despacho',     
            icon: '📤', 
            label: 'Despacho',           // ⚡ Este label se cambia dinámicamente según el rol
            href: '/despacho.html',     
            roles: ['JEFATURA', 'OPERATIVO', 'ENCARGADO_DEPENDENCIA'],
            // ⚡ Label alternativo para Enc. Dependencia
            labelPorRol: {
                'ENCARGADO_DEPENDENCIA': 'Pedidos'
            }
        },
        { 
            id: 'inventario',   
            icon: '📦', 
            label: 'Inventario',         
            href: '/articulos.html',    
            // ⚡ Enc. Dependencia puede ver el catálogo (solo lectura)
            roles: ['JEFATURA', 'OPERATIVO', 'AUDITOR', 'ENCARGADO_DEPENDENCIA']
        },
        { 
            id: 'trazabilidad', 
            icon: '🛡️', 
            label: 'Trazabilidad NT 226', 
            href: '/trazabilidad.html', 
            roles: ['JEFATURA', 'AUDITOR'] 
        },
        { 
            id: 'reportes',     
            icon: '📊', 
            label: 'Reportes',           
            href: '/reportes.html',     
            roles: ['JEFATURA', 'OPERATIVO', 'AUDITOR'] 
        },
        { 
            id: 'usuarios',     
            icon: '👥', 
            label: 'Gestión de Usuarios', 
            href: '/usuarios.html',     
            roles: ['JEFATURA'] 
        },
        { 
            id: 'config',       
            icon: '⚙️', 
            label: 'Configuración',      
            href: '/configuracion.html', 
            roles: ['JEFATURA'] 
        }
    ],
    
    /**
     * Inicializa el layout.
     * @param {string} paginaActiva - ID del item activo
     */
    init(paginaActiva) {
        const usuario = window.getUsuario ? window.getUsuario() 
                                          : JSON.parse(localStorage.getItem('usuario') || '{}');
        
        // Si no hay usuario, el session-helper ya redirigió (o va a redirigir)
        if (!usuario || !usuario.rol) {
            console.warn('⚠️ [LAYOUT] Sin usuario en localStorage');
            return;
        }
        
        this.renderHeader(usuario);
        this.renderSidebar(usuario.rol, paginaActiva);
        this.activarBotonLogout();
    },
    
    /**
     * Renderiza el header superior.
     */
    renderHeader(usuario) {
        const header = document.getElementById('app-header');
        if (!header) return;
        
        const rolLabel = this.getRolLabel(usuario.rol);
        
        header.innerHTML = `
            <div class="logo-area">
                <span class="logo-icon">🏥</span>
                <span class="system-title">Sistema Bodega DSM Santa Cruz</span>
            </div>
            <div class="user-area">
                <span class="user-name">${this.escape(usuario.nombreCompleto || 'Usuario')}</span>
                <span class="user-role" title="${this.escape(usuario.rol)}">${rolLabel}</span>
                <button class="btn-logout" id="btnLogout">Cerrar Sesión</button>
            </div>
        `;
    },
    
    /**
     * Renderiza el sidebar con el menú según el rol.
     */
    renderSidebar(rol, paginaActiva) {
        const sidebar = document.getElementById('app-sidebar');
        if (!sidebar) return;
        
        const itemsPermitidos = this.menuItems.filter(item => 
            item.roles.includes(rol)
        );
        
        sidebar.innerHTML = `
            <ul class="sidebar-menu">
                ${itemsPermitidos.map(item => {
                    // ⚡ Usar label específico por rol si existe
                    const label = (item.labelPorRol && item.labelPorRol[rol]) 
                        ? item.labelPorRol[rol] 
                        : item.label;
                    
                    return `
                        <li class="${item.id === paginaActiva ? 'active' : ''}"
                            onclick="window.location.href='${item.href}'"
                            title="${label}">
                            <span class="menu-icon">${item.icon}</span>
                            <span class="menu-label">${label}</span>
                        </li>
                    `;
                }).join('')}
            </ul>
        `;
    },
    
    /**
     * Convierte el código del rol en un label legible.
     */
    getRolLabel(rol) {
        const labels = {
            'JEFATURA': 'Jefatura',
            'OPERATIVO': 'Operativo',
            'AUDITOR': 'Auditor NT 226',
            'ENCARGADO_DEPENDENCIA': 'Encargado Dependencia'
        };
        return labels[rol] || rol;
    },
    
    /**
     * Activa el botón de cerrar sesión.
     */
    activarBotonLogout() {
        const btn = document.getElementById('btnLogout');
        if (btn) {
            btn.addEventListener('click', () => {
                if (window.cerrarSesion) {
                    window.cerrarSesion();
                } else {
                    localStorage.clear();
                    window.location.href = 'http://localhost:8081/login.html';
                }
            });
        }
    },
    
    /**
     * Escapa HTML para prevenir XSS.
     */
    escape(str) {
        if (str == null) return '';
        return String(str)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#039;');
    }
};