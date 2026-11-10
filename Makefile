.PHONY: help build up down restart ps logs logs-db test shell-db db-dlq health metrics clean \
        api-jwt api-list api-filter-failed api-detail api-replay api-dlq

# Variables por defecto (pueden sobreescribirse: make api-list CLIENT_ID=CLIENT002)
CLIENT_ID     ?= CLIENT001
CLIENT_SECRET ?= $(shell echo $(CLIENT_ID) | tr '[:upper:]' '[:lower:]')-demo-secret
EVENT_ID      ?= EVT003
STATUS        ?= failed
BASE_URL      ?= http://localhost:8080

##@ Ayuda
help: ## Muestra esta ayuda con todos los comandos disponibles
	@awk 'BEGIN {FS = ":.*##"; printf "\n\033[1mUso:\033[0m\n  make \033[36m<target>\033[0m\n"} /^[a-zA-Z0-9_-]+:.*?##/ { printf "  \033[36m%-20s\033[0m %s\n", $$1, $$2 } /^##@/ { printf "\n\033[1m%s\033[0m\n", substr($$0, 5) } ' $(MAKEFILE_LIST)

##@ Entorno Docker (Build & Run)
build: ## Construye las imágenes Docker sin levantar los contenedores
	docker compose build

up: ## Construye y levanta la aplicación + PostgreSQL en segundo plano
	docker compose up --build -d
	@echo "✅ Servicios iniciados. API en $(BASE_URL) | Swagger UI en $(BASE_URL)/swagger-ui.html"

down: ## Detiene y elimina los contenedores de Docker Compose
	docker compose down

restart: ## Reinicia el contenedor de la aplicación
	docker compose restart app

ps: ## Muestra el estado de los contenedores
	docker compose ps

logs: ## Muestra los logs en tiempo real de la aplicación Spring Boot
	docker compose logs -f app

logs-db: ## Muestra los logs de PostgreSQL
	docker compose logs -f postgres

clean: ## Detiene contenedores y elimina volúmenes (resetea la base de datos)
	docker compose down -v --remove-orphans

##@ Testing & Base de Datos
test: ## Ejecuta las pruebas unitarias y de integración dentro de Docker (con caché .m2)
	docker compose --profile test run --rm test

shell-db: ## Abre una consola interactiva psql dentro del contenedor de PostgreSQL
	docker compose exec postgres psql -U cobre -d cobredb

db-dlq: ## Consulta directamente la tabla notification_dlq en PostgreSQL
	docker compose exec postgres psql -U cobre -d cobredb -c "SELECT id, event_id, client_id, event_type, retry_attempts_exhausted, last_http_status_code, status, created_at, last_reprocessed_at FROM notification_dlq ORDER BY id;"

##@ Observabilidad (Health & Metrics)
health: ## Consulta el estado de salud (/actuator/health)
	curl -sS $(BASE_URL)/actuator/health | python3 -m json.tool

metrics: ## Consulta las métricas expuestas en formato Prometheus (/actuator/prometheus)
	curl -sS $(BASE_URL)/actuator/prometheus | grep -E "cobre_|http_server_requests" || true

##@ Pruebas Rápidas de la API Self-Service
api-jwt: ## Genera un token JWT firmado (HS256) para el cliente (ej: make api-jwt CLIENT_ID=CLIENT001)
	curl -sS -X POST -H "Content-Type: application/json" -d '{"client_id":"$(CLIENT_ID)","client_secret":"$(CLIENT_SECRET)"}' "$(BASE_URL)/auth/token" | python3 -m json.tool

api-list: ## Lista todos los eventos del cliente autenticado con JWT (ej: make api-list CLIENT_ID=CLIENT001)
	@TOKEN=$$(curl -sS -X POST -H "Content-Type: application/json" -d '{"client_id":"$(CLIENT_ID)","client_secret":"$(CLIENT_SECRET)"}' "$(BASE_URL)/auth/token" | python3 -c "import sys,json; print(json.load(sys.stdin)['access_token'])"); \
	curl -sS -H "Authorization: Bearer $$TOKEN" "$(BASE_URL)/notification_events" | python3 -m json.tool

api-filter-failed: ## Filtra eventos por estado autenticado con JWT (ej: make api-filter-failed CLIENT_ID=CLIENT002 STATUS=failed)
	@TOKEN=$$(curl -sS -X POST -H "Content-Type: application/json" -d '{"client_id":"$(CLIENT_ID)","client_secret":"$(CLIENT_SECRET)"}' "$(BASE_URL)/auth/token" | python3 -c "import sys,json; print(json.load(sys.stdin)['access_token'])"); \
	curl -sS -H "Authorization: Bearer $$TOKEN" "$(BASE_URL)/notification_events?delivery_status=$(STATUS)" | python3 -m json.tool

api-detail: ## Obtiene el detalle de un evento autenticado con JWT (ej: make api-detail CLIENT_ID=CLIENT001 EVENT_ID=EVT001)
	@TOKEN=$$(curl -sS -X POST -H "Content-Type: application/json" -d '{"client_id":"$(CLIENT_ID)","client_secret":"$(CLIENT_SECRET)"}' "$(BASE_URL)/auth/token" | python3 -c "import sys,json; print(json.load(sys.stdin)['access_token'])"); \
	curl -sS -H "Authorization: Bearer $$TOKEN" "$(BASE_URL)/notification_events/$(EVENT_ID)" | python3 -m json.tool

api-replay: ## Solicita el reenvío (replay) de un evento fallido autenticado con JWT (ej: make api-replay CLIENT_ID=CLIENT002 EVENT_ID=EVT003)
	@TOKEN=$$(curl -sS -X POST -H "Content-Type: application/json" -d '{"client_id":"$(CLIENT_ID)","client_secret":"$(CLIENT_SECRET)"}' "$(BASE_URL)/auth/token" | python3 -c "import sys,json; print(json.load(sys.stdin)['access_token'])"); \
	curl -i -sS -X POST -H "Authorization: Bearer $$TOKEN" "$(BASE_URL)/notification_events/$(EVENT_ID)/replay"

api-dlq: ## Consulta los eventos en la tabla DLQ para el cliente autenticado con JWT (ej: make api-dlq CLIENT_ID=CLIENT003)
	@TOKEN=$$(curl -sS -X POST -H "Content-Type: application/json" -d '{"client_id":"$(CLIENT_ID)","client_secret":"$(CLIENT_SECRET)"}' "$(BASE_URL)/auth/token" | python3 -c "import sys,json; print(json.load(sys.stdin)['access_token'])"); \
	curl -sS -H "Authorization: Bearer $$TOKEN" "$(BASE_URL)/notification_events/dlq" | python3 -m json.tool

