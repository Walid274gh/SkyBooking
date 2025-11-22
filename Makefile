# ==========================================
# MAKEFILE - SKYBOOKING ALGÉRIE
# Gestion du serveur backend
# ==========================================

.PHONY: help start stop restart build rebuild logs health status ip firewall test-api backup restore clean shell-backend shell-mongodb

# Couleurs pour l'affichage
RED := \033[0;31m
GREEN := \033[0;32m
YELLOW := \033[0;33m
BLUE := \033[0;34m
NC := \033[0m

help: ## Afficher l'aide
	@echo "$(BLUE)=========================================="
	@echo "SKYBOOKING SERVEUR - Commandes disponibles"
	@echo "==========================================$(NC)"
	@echo ""
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | \
		awk 'BEGIN {FS = ":.*?## "}; {printf "  $(GREEN)%-15s$(NC) %s\n", $$1, $$2}'
	@echo ""

# ========== GESTION DES SERVICES ==========

start: ## Démarrer le serveur backend
	@echo "$(YELLOW)🚀 Démarrage du serveur backend...$(NC)"
	@mkdir -p logs tickets invoices data/mongodb
	@docker-compose up -d
	@echo "$(GREEN)✅ Serveur démarré!$(NC)"
	@echo ""
	@make ip

stop: ## Arrêter le serveur
	@echo "$(YELLOW)🛑 Arrêt du serveur...$(NC)"
	@docker-compose down
	@echo "$(GREEN)✅ Serveur arrêté$(NC)"

restart: ## Redémarrer le serveur
	@echo "$(YELLOW)🔄 Redémarrage du serveur...$(NC)"
	@make stop
	@sleep 2
	@make start

build: ## Builder les images Docker
	@echo "$(YELLOW)🔨 Build des images Docker...$(NC)"
	@docker-compose build --no-cache
	@echo "$(GREEN)✅ Build terminé$(NC)"

rebuild: build start ## Rebuild et démarrer

# ========== LOGS ET DIAGNOSTICS ==========

logs: ## Afficher tous les logs
	@docker-compose logs --tail=100 -f

logs-backend: ## Logs du backend uniquement
	@docker-compose logs -f backend

logs-mongodb: ## Logs de MongoDB uniquement
	@docker-compose logs -f mongodb

logs-mongo-express: ## Logs de Mongo Express uniquement
	@docker-compose logs -f mongo-express

health: ## Vérifier la santé des services
	@echo "$(BLUE)🏥 Vérification de la santé des services...$(NC)"
	@echo ""
	@echo "$(YELLOW)MongoDB:$(NC)"
	@curl -s http://localhost:27017 >/dev/null 2>&1 && \
		echo "  $(GREEN)✅ En ligne$(NC)" || \
		echo "  $(RED)❌ Hors ligne$(NC)"
	@echo ""
	@echo "$(YELLOW)Backend REST:$(NC)"
	@curl -s http://localhost:8080/api/health >/dev/null 2>&1 && \
		echo "  $(GREEN)✅ En ligne$(NC)" || \
		echo "  $(RED)❌ Hors ligne$(NC)"
	@echo ""
	@echo "$(YELLOW)Mongo Express:$(NC)"
	@curl -s http://localhost:8081 >/dev/null 2>&1 && \
		echo "  $(GREEN)✅ En ligne$(NC)" || \
		echo "  $(RED)❌ Hors ligne$(NC)"

status: ## Afficher le statut des conteneurs
	@echo "$(BLUE)📊 Statut des conteneurs:$(NC)"
	@docker ps -a --filter "name=skybooking" \
		--format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"

ip: ## Afficher l'IP du serveur et les instructions
	@echo "$(BLUE)📡 Configuration réseau du serveur:$(NC)"
	@echo ""
	@echo "$(YELLOW)IP du serveur:$(NC)"
	@hostname -I | awk '{print "  " $$1}'
	@echo ""
	@echo "$(GREEN)━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━$(NC)"
	@echo "$(GREEN)Configuration pour les CLIENTS:$(NC)"
	@echo "$(GREEN)━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━$(NC)"
	@echo ""
	@echo "1️⃣  Sur chaque client, créer le fichier:"
	@echo "    $(YELLOW)frontend/.env.local$(NC)"
	@echo ""
	@echo "2️⃣  Contenu du fichier .env.local:"
	@echo "    $(BLUE)VITE_API_URL=http://$(shell hostname -I | awk '{print $$1}'):8080/api$(NC)"
	@echo ""
	@echo "3️⃣  Démarrer le frontend:"
	@echo "    $(YELLOW)cd frontend$(NC)"
	@echo "    $(YELLOW)npm install$(NC)"
	@echo "    $(YELLOW)npm run dev$(NC)"
	@echo ""
	@echo "$(GREEN)━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━$(NC)"
	@echo "$(BLUE)Services accessibles:$(NC)"
	@echo "$(GREEN)━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━$(NC)"
	@echo "  Backend API:    http://$(shell hostname -I | awk '{print $$1}'):8080/api"
	@echo "  Mongo Express:  http://$(shell hostname -I | awk '{print $$1}'):8081"
	@echo "  CORBA Naming:   iiop://$(shell hostname -I | awk '{print $$1}'):1050"
	@echo ""

# ========== TESTS ==========

test-api: ## Tester les endpoints de l'API
	@echo "$(BLUE)🧪 Test des endpoints API...$(NC)"
	@echo ""
	@echo "$(YELLOW)Health check:$(NC)"
	@curl -s http://localhost:8080/api/health | jq . || echo "$(RED)Échec$(NC)"
	@echo ""
	@echo "$(YELLOW)Recherche de vols (exemple):$(NC)"
	@curl -s "http://localhost:8080/api/flights/search?from=ALG&to=PAR&date=2025-06-01&seatClass=ECONOMY" \
		| jq . || echo "$(RED)Échec$(NC)"

# ========== PARE-FEU ==========

firewall: ## Afficher les commandes de configuration du pare-feu
	@echo "$(YELLOW)🔥 Configuration du pare-feu:$(NC)"
	@echo "$(RED)⚠️  Exécutez ces commandes manuellement:$(NC)"
	@echo ""
	@echo "$(BLUE)# Autoriser les ports nécessaires$(NC)"
	@echo "sudo ufw allow 8080/tcp   # Backend REST API"
	@echo "sudo ufw allow 1050/tcp   # CORBA Naming Service"
	@echo "sudo ufw allow 27017/tcp  # MongoDB (si accès direct)"
	@echo "sudo ufw allow 8081/tcp   # Mongo Express (interface admin)"
	@echo ""
	@echo "$(BLUE)# Recharger le pare-feu$(NC)"
	@echo "sudo ufw reload"
	@echo ""
	@echo "$(BLUE)# Vérifier le statut$(NC)"
	@echo "sudo ufw status"
	@echo ""

# ========== SAUVEGARDE ET RESTAURATION ==========

backup: ## Sauvegarder la base de données
	@echo "$(YELLOW)💾 Sauvegarde de MongoDB...$(NC)"
	@mkdir -p backups
	@docker exec skybooking-mongodb mongodump \
		--db=skybooking_db \
		--out=/data/backup
	@docker cp skybooking-mongodb:/data/backup \
		./backups/mongodb-$(shell date +%Y%m%d-%H%M%S)
	@echo "$(GREEN)✅ Sauvegarde terminée$(NC)"

restore: ## Restaurer la base de données (RESTORE_FILE=chemin)
	@if [ -z "$(RESTORE_FILE)" ]; then \
		echo "$(RED)❌ Erreur: RESTORE_FILE non défini$(NC)"; \
		echo "Usage: make restore RESTORE_FILE=./backups/mongodb-20250121-120000"; \
		exit 1; \
	fi
	@echo "$(YELLOW)♻️  Restauration de MongoDB...$(NC)"
	@docker cp $(RESTORE_FILE) skybooking-mongodb:/data/restore
	@docker exec skybooking-mongodb mongorestore \
		--db=skybooking_db \
		/data/restore/skybooking_db
	@echo "$(GREEN)✅ Restauration terminée$(NC)"

# ========== NETTOYAGE ==========

clean: ## Nettoyer les volumes et données (⚠️ destructif)
	@echo "$(RED)⚠️  ATTENTION: Ceci va supprimer tous les volumes et données!$(NC)"
	@echo -n "Êtes-vous sûr? [y/N] "; \
	read REPLY; \
	if [ "$$REPLY" = "y" ] || [ "$$REPLY" = "Y" ]; then \
		docker-compose down -v --remove-orphans; \
		docker system prune -f; \
		rm -rf logs/* tickets/* invoices/* data/mongodb/*; \
		echo "$(GREEN)✅ Nettoyage terminé$(NC)"; \
	else \
		echo "$(YELLOW)❌ Annulé$(NC)"; \
	fi

# ========== SHELL ET DEBUG ==========

shell-backend: ## Ouvrir un shell dans le conteneur backend
	@docker exec -it skybooking-backend /bin/bash

shell-mongodb: ## Ouvrir mongosh dans MongoDB
	@docker exec -it skybooking-mongodb mongosh skybooking_db