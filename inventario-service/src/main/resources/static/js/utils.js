/* ============================================================
   UTILS - Helpers compartidos
   Sistema Bodega DSM Santa Cruz
   ============================================================
   Cargar ANTES de cualquier otro script que use estos helpers.
   Uso: <script src="/js/utils.js"></script>
   ============================================================ */

(function (global) {
    'use strict';

    /**
     * Escapa caracteres HTML para prevenir XSS.
     * Uso: cuando insertas datos del backend con innerHTML.
     */
    function escapeHtml(str) {
        if (str == null) return '';
        return String(str)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#39;');
    }

    /**
     * Escapa para uso seguro en atributos (entre comillas simples).
     */
    function escapeAttr(str) {
        if (str == null) return '';
        return String(str)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#39;');
    }

    /**
     * Debounce: retrasa la ejecución de una función.
     */
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

    /**
     * Formatea una fecha ISO a dd/mm/yyyy.
     */
    function formatFecha(fechaStr) {
        if (!fechaStr) return '—';
        try {
            const fecha = new Date(fechaStr.length === 10 ? fechaStr + 'T00:00:00' : fechaStr);
            const dia = String(fecha.getDate()).padStart(2, '0');
            const mes = String(fecha.getMonth() + 1).padStart(2, '0');
            const anio = fecha.getFullYear();
            return `${dia}/${mes}/${anio}`;
        } catch (e) {
            return '—';
        }
    }

    /**
     * Formatea un monto a $ con separadores de miles.
     */
    function formatMonto(num) {
        if (num == null || isNaN(num)) return '$0';
        return '$' + Number(num).toLocaleString('es-CL');
    }

    /**
     * Formatea la unidad de medida para visualización.
     * "UNIDAD" → "Unidad", "CAJA" → "Caja", etc.
     */
    function formatUnidad(unidad) {
        if (!unidad) return '';
        const map = {
            'UNIDAD': 'Unidad',
            'UNIDADES': 'Unidades',
            'CAJA': 'Caja',
            'CAJAS': 'Cajas',
            'PAQUETE': 'Paquete',
            'PAQUETES': 'Paquetes',
            'BOLSA': 'Bolsa',
            'BOLSAS': 'Bolsas',
            'KILO': 'Kilo',
            'KILOS': 'Kilos',
            'FRASCO': 'Frasco',
            'FRASCOS': 'Frascos',
            'SET': 'Set',
            'KIT': 'Kit',
            'BLISTER': 'Blíster',
            'PAR': 'Par',
            'UNIDAD (PAR)': 'Par',
            'PIEZA': 'Pieza',
            'PIEZAS': 'Piezas',
            'METRO': 'Metro',
            'METROS': 'Metros',
            'LITRO': 'Litro',
            'LITROS': 'Litros',
            'MILILITRO': 'Mililitro',
            'MILILITROS': 'Mililitros',
            'GRAMO': 'Gramo',
            'GRAMOS': 'Gramos',
            'ROLLO': 'Rollo',
            'ROLLOS': 'Rollos',
            'DISPENSADOR': 'Dispensador',
            'CAJA (X 100 U)': 'Caja',
            'CAJA (X 50 U)': 'Caja'
        };
        return map[unidad.toUpperCase()] || unidad;
    }

    /**
     * Formatea un RUT chileno con puntos y guion.
     * "212432441" → "21.243.244-1"
     */
    function formatearRut(rut) {
        if (!rut) return '';
        const limpio = String(rut).replace(/[^0-9kK]/g, '').toUpperCase();
        if (limpio.length <= 1) return limpio;
        const cuerpo = limpio.slice(0, -1);
        const dv = limpio.slice(-1);
        const cuerpoFormateado = cuerpo.replace(/\B(?=(\d{3})+(?!\d))/g, '.');
        return `${cuerpoFormateado}-${dv}`;
    }

    // ============================================================
    // EXPOSICIÓN GLOBAL
    // ============================================================

    // Objeto Utils con todas las funciones
    global.Utils = {
        escapeHtml,
        escapeAttr,
        debounce,
        formatFecha,
        formatMonto,
        formatUnidad,
        formatearRut
    };

    // Funciones globales para compatibilidad con el código existente
    global.escapeHtml = escapeHtml;
    global.escapeAttr = escapeAttr;
    global.debounce = global.debounce || debounce;
    global.formatFecha = global.formatFecha || formatFecha;
    global.formatMonto = global.formatMonto || formatMonto;
    global.formatUnidad = global.formatUnidad || formatUnidad;
    global.formatearRut = global.formatearRut || formatearRut;

})(window);