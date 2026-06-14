# ── Typical dev workflows ──────────────────────────────────────────────────────
#
# Option A: native (hot reload, debugger)
#   make development-db               — start PostgreSQL in Docker
#   ./scripts/mvn-local.sh spring-boot:run
#                                     — Spring Boot on :8080
#   cd src/main/frontend && ng serve  — Angular on :4200 (proxies API to :8080)
#
# Option B: full Docker stack (production-like)
#   make development-up               — build JAR + image, start db + app + caddy
#                                       app available at http://localhost
#
# Housekeeping
#   make help                         — list all targets
#   make development-clean            — remove all containers and volumes
#
# ──────────────────────────────────────────────────────────────────────────────

.PHONY: help \
	development-db development-db-stop development-db-logs \
	development-up development-down development-build development-logs development-clean \
	process-phase2 process-phase2-check

DB_PASSWORD ?= dev

help: ## Show this help
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | \
		awk 'BEGIN {FS = ":.*?## "}; {printf "  %-30s %s\n", $$1, $$2}'

# ── Dev: DB only (Spring Boot + Angular run natively) ──────────────────────────

development-db: ## Start PostgreSQL only (for native Spring Boot + ng serve)
	DB_PASSWORD=$(DB_PASSWORD) docker compose -f docker-compose.dev.yml up -d
	@echo ""
	@echo "  PostgreSQL ready at localhost:5432"
	@echo "  JDBC URL: jdbc:postgresql://localhost:5432/trawhile"
	@echo "  Username: tt  Password: $(DB_PASSWORD)"
	@echo ""
	@echo "  Run Spring Boot: ./scripts/mvn-local.sh spring-boot:run"
	@echo "  Run Angular:     cd src/main/frontend && ng serve"

development-db-stop: ## Stop the dev PostgreSQL container
	docker compose -f docker-compose.dev.yml down

development-db-logs: ## Tail dev PostgreSQL logs
	docker compose -f docker-compose.dev.yml logs -f db

# ── Dev: full stack in Docker (production-like) ────────────────────────────────

development-build: ## Build the application JAR and Docker image
	./scripts/mvn-local.sh --batch-mode package -DskipTests
	docker build -t trawhile:latest .

development-up: development-build ## Build and start the full stack (db + app + caddy)
	docker compose up -d
	@echo ""
	@echo "  App running at http://localhost (Caddy on port 80)"

development-down: ## Stop the full stack
	docker compose down

development-logs: ## Tail full stack logs (all services)
	docker compose logs -f

development-clean: ## Remove all dev volumes and containers
	docker compose -f docker-compose.dev.yml down -v
	docker compose down -v

# ── Spec/regeneration workflows ───────────────────────────────────────────────

process-phase2: ## Run the Phase 2 orchestration script
	./scripts/phase2.sh run

process-phase2-check: ## Validate the current Phase 2 baseline
	./scripts/phase2.sh check
