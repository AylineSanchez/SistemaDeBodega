#!/bin/bash

# Colores
GREEN='\033[0;32m'
ORANGE='\033[0;33m'
RED='\033[0;31m'
NC='\033[0m'

echo -e "${ORANGE}🚀 Iniciando Sistema Bodega DSM...${NC}\n"

# 1. Docker
echo -e "${GREEN}▶ [1/3] Levantando PostgreSQL...${NC}"
cd ~/Descargas/PRACTICA1/dsm-bodega/docker
docker compose up -d
sleep 3

# 2. auth-service (background)
echo -e "${GREEN}▶ [2/3] Iniciando auth-service...${NC}"
cd ~/Descargas/PRACTICA1/dsm-bodega/auth-service
export MAIL_USERNAME="sistemadegestiondebodega@gmail.com"
export MAIL_PASSWORD="tepaoubvbchpiufn"
nohup ./mvnw spring-boot:run > /tmp/auth-service.log 2>&1 &
echo "   PID: $!"
echo "   Log: /tmp/auth-service.log"

# 3. inventario-service (background)
echo -e "${GREEN}▶ [3/3] Iniciando inventario-service...${NC}"
cd ~/Descargas/PRACTICA1/dsm-bodega/inventario-service
export JWT_SECRET="c3VwZXItc2VjcmV0LWtleS1kc20tYm9kZWdhLTIwMjYtc2FudGEtY3J1ei1jaGlsZS1qd3Qtc2VjdXJlLWtleS1mb3ItYXV0aC1zZXJ2aWNl"
nohup ./mvnw spring-boot:run > /tmp/inventario-service.log 2>&1 &
echo "   PID: $!"
echo "   Log: /tmp/inventario-service.log"

echo ""
echo "⏳ Esperando 45 segundos a que arranquen los servicios..."
sleep 45

# Verificar
echo ""
echo -e "${ORANGE}🔍 Verificando servicios...${NC}"
echo -n "   Docker:              " && (docker ps | grep -q dsm_bodega_db && echo "✅ OK" || echo "❌ FALLO")
echo -n "   auth-service (8081): " && (curl -s http://localhost:8081/api/auth/health | grep -q success && echo "✅ OK" || echo "❌ FALLO")
echo -n "   inventario (8082):   " && (curl -s -o /dev/null -w "%{http_code}" http://localhost:8082/api/categorias | grep -q 401 && echo "✅ OK" || echo "❌ FALLO")

echo ""
echo -e "${GREEN}✅ Sistema iniciado.${NC}"
echo ""
echo -e "🔗 ${ORANGE}Login:${NC} http://localhost:8081/login.html"
echo ""
echo -e "📋 ${ORANGE}Para ver logs:${NC}"
echo "   tail -f /tmp/auth-service.log"
echo "   tail -f /tmp/inventario-service.log"
echo ""
echo -e "🛑 ${ORANGE}Para detener:${NC} ./stop-all.sh"