#!/bin/bash
echo "🛑 Deteniendo servicios..."

# Matar procesos de Maven/Spring
pkill -f "spring-boot:run"
pkill -f "AuthServiceApplication"
pkill -f "InventarioServiceApplication"

echo "✅ Servicios detenidos"
echo "(Docker sigue corriendo. Para detenerlo: cd docker && docker compose down)"