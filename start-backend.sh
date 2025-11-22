#!/bin/bash
# ==========================================
# SCRIPT DE DÉMARRAGE BACKEND
# ==========================================

set -e

echo "=========================================="
echo "🚀 DÉMARRAGE SKYBOOKING BACKEND"
echo "=========================================="

# Fonction d'attente pour CORBA Naming Service
wait_for_naming_service() {
  echo "⏳ Attente du Naming Service CORBA..."
  local max_attempts=20
  local attempt=0
  
  while [ $attempt -lt $max_attempts ]; do
    # Tenter de lister le Naming Service
    if java -cp "lib/*" \
      -Djava.naming.factory.initial=com.sun.jndi.cosnaming.CNCtxFactory \
      -Djava.naming.provider.url=iiop://localhost:${CORBA_NAMING_PORT} \
      com.skybooking.utils.NamingServiceChecker 2>/dev/null; then
      echo "✅ Naming Service prêt"
      return 0
    fi
    attempt=$((attempt + 1))
    echo "   Tentative $attempt/$max_attempts..."
    sleep 1
  done
  
  echo "❌ Naming Service n'a pas démarré après $max_attempts tentatives"
  return 1
}

# Fonction d'attente pour serveur CORBA
wait_for_corba_server() {
  echo "⏳ Vérification du serveur CORBA..."
  local max_attempts=15
  local attempt=0
  
  while [ $attempt -lt $max_attempts ]; do
    # Vérifier si le serveur CORBA répond
    if java -cp "lib/*" \
      -Djava.naming.factory.initial=com.sun.jndi.cosnaming.CNCtxFactory \
      -Djava.naming.provider.url=iiop://localhost:${CORBA_NAMING_PORT} \
      com.skybooking.utils.CorbaServerChecker 2>/dev/null; then
      echo "✅ Serveur CORBA prêt"
      return 0
    fi
    attempt=$((attempt + 1))
    echo "   Tentative $attempt/$max_attempts..."
    sleep 2
  done
  
  echo "⚠️ Serveur CORBA pourrait ne pas être complètement prêt"
  return 0  # Continue quand même
}

# Gestionnaire de signaux pour arrêt propre
cleanup() {
  echo ""
  echo "⚠️ Signal d'arrêt reçu, nettoyage..."
  
  if [ ! -z "$REST_PID" ]; then
    echo "   Arrêt du pont REST (PID: $REST_PID)..."
    kill -TERM $REST_PID 2>/dev/null || true
  fi
  
  if [ ! -z "$SERVER_PID" ]; then
    echo "   Arrêt du serveur CORBA (PID: $SERVER_PID)..."
    kill -TERM $SERVER_PID 2>/dev/null || true
  fi
  
  if [ ! -z "$ORBD_PID" ]; then
    echo "   Arrêt du Naming Service (PID: $ORBD_PID)..."
    kill -TERM $ORBD_PID 2>/dev/null || true
  fi
  
  echo "✅ Arrêt propre effectué"
  exit 0
}

trap cleanup SIGTERM SIGINT SIGQUIT

# ==========================================
# DÉMARRAGE DES SERVICES
# ==========================================

echo ""
echo "ℹ️  MongoDB est déjà vérifié par Docker Compose (healthcheck)"
echo ""

# 1. Démarrer le service de nommage CORBA
echo "🔷 Démarrage du service de nommage CORBA..."
orbd -ORBInitialPort ${CORBA_NAMING_PORT} -ORBInitialHost 0.0.0.0 \
  > /app/logs/orbd.log 2>&1 &
ORBD_PID=$!

# Vérifier que le processus est démarré
sleep 2
if ! kill -0 $ORBD_PID 2>/dev/null; then
  echo "❌ Échec du démarrage du Naming Service"
  cat /app/logs/orbd.log
  exit 1
fi

echo "✅ Naming Service démarré (PID: $ORBD_PID)"

# Attendre que le Naming Service soit prêt
if ! wait_for_naming_service; then
  echo "❌ Le Naming Service ne répond pas"
  cat /app/logs/orbd.log
  exit 1
fi

# 2. Démarrer le serveur CORBA
echo ""
echo "🔷 Démarrage du serveur CORBA..."
java ${JAVA_OPTS} \
  -cp "lib/*" \
  -Djava.naming.factory.initial=com.sun.jndi.cosnaming.CNCtxFactory \
  -Djava.naming.provider.url=iiop://localhost:${CORBA_NAMING_PORT} \
  com.skybooking.server.FlightBookingServer \
  > /app/logs/corba-server.log 2>&1 &
SERVER_PID=$!

# Vérifier que le processus est démarré
sleep 2
if ! kill -0 $SERVER_PID 2>/dev/null; then
  echo "❌ Échec du démarrage du serveur CORBA"
  cat /app/logs/corba-server.log
  exit 1
fi

echo "✅ Serveur CORBA démarré (PID: $SERVER_PID)"

# Attendre que le serveur CORBA soit prêt
wait_for_corba_server

# 3. Démarrer le pont REST
echo ""
echo "🔷 Démarrage du pont REST..."
java ${JAVA_OPTS} \
  -cp "lib/*" \
  -Djava.naming.factory.initial=com.sun.jndi.cosnaming.CNCtxFactory \
  -Djava.naming.provider.url=iiop://localhost:${CORBA_NAMING_PORT} \
  com.skybooking.rest.CorbaRestBridge \
  > /app/logs/rest-bridge.log 2>&1 &
REST_PID=$!

# Vérifier que le processus est démarré
sleep 2
if ! kill -0 $REST_PID 2>/dev/null; then
  echo "❌ Échec du démarrage du pont REST"
  cat /app/logs/rest-bridge.log
  exit 1
fi

echo ""
echo "=========================================="
echo "✅ TOUS LES SERVICES SONT DÉMARRÉS"
echo "=========================================="
echo ""
echo "📊 Services actifs:"
echo "   • MongoDB: mongodb:27017"
echo "   • CORBA Naming: port ${CORBA_NAMING_PORT}"
echo "   • REST API: http://localhost:${REST_API_PORT}"
echo ""
echo "🔢 PIDs:"
echo "   • ORBD: $ORBD_PID"
echo "   • CORBA Server: $SERVER_PID"
echo "   • REST Bridge: $REST_PID"
echo ""
echo "📝 Logs disponibles dans /app/logs/"
echo ""

# Surveillance continue des processus
monitor_services() {
  while true; do
    sleep 30
    
    # Vérifier chaque service
    if ! kill -0 $ORBD_PID 2>/dev/null; then
      echo "❌ ORBD a crashé ! Logs:"
      tail -n 20 /app/logs/orbd.log
      exit 1
    fi
    
    if ! kill -0 $SERVER_PID 2>/dev/null; then
      echo "❌ Serveur CORBA a crashé ! Logs:"
      tail -n 20 /app/logs/corba-server.log
      exit 1
    fi
    
    if ! kill -0 $REST_PID 2>/dev/null; then
      echo "❌ Pont REST a crashé ! Logs:"
      tail -n 20 /app/logs/rest-bridge.log
      exit 1
    fi
  done
}

# Démarrer la surveillance en arrière-plan
monitor_services &
MONITOR_PID=$!

# Attendre le processus principal (REST)
wait $REST_PID
