-- ============================================================
-- TABLA: CONFIGURACION DEL SISTEMA
-- ============================================================
CREATE TABLE IF NOT EXISTS configuracion (
    clave               VARCHAR(100) PRIMARY KEY,
    valor               TEXT,
    descripcion         VARCHAR(255),
    tipo                VARCHAR(30) DEFAULT 'STRING',
    fecha_actualizacion TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_por     VARCHAR(12)
);

INSERT INTO configuracion (clave, valor, descripcion, tipo) VALUES
    ('dias_alerta_vencimiento_critico', '120', 'Días de alerta para vencimiento crítico', 'INT'),
    ('dias_alerta_vencimiento_proximo', '180', 'Días de alerta para vencimiento próximo', 'INT'),
    ('dias_alerta_vencimiento_vigilar', '365', 'Días de alerta para vencimiento a vigilar', 'INT'),
    ('timeout_sesion_minutos', '15', 'Minutos de inactividad antes de cerrar sesión', 'INT'),
    ('notif_critico_activo', 'true', 'Activar notificaciones de stock crítico por email', 'BOOLEAN'),
    ('notif_bloqueo_activo', 'true', 'Activar notificaciones de bloqueo sanitario por email', 'BOOLEAN'),
    ('notif_vencimiento_activo', 'true', 'Activar notificaciones de vencimiento por email', 'BOOLEAN')
ON CONFLICT (clave) DO NOTHING;