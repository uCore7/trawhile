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
# Option C: terminal-first Codex devcontainer
#   make devcontainer-up              — build/start workspace + PostgreSQL for Codex
#   make devcontainer-codex           — run Codex inside the workspace container
#   make devcontainer-codex-task TASK=tasks/... 
#                                     — run Codex non-interactively on a task file
#   make host-auth-check              — verify host auth.json is ChatGPT-backed
#   make devcontainer-sync-auth       — copy host Codex auth cache into container
#   make devcontainer-shell           — open a shell inside the workspace container
#   make devcontainer-down            — stop the devcontainer services
#
# Housekeeping
#   make help                         — list all targets
#   make development-clean            — remove all containers and volumes
#
# ──────────────────────────────────────────────────────────────────────────────

.PHONY: help \
	development-db development-db-stop development-db-logs \
	development-up development-down development-build development-logs development-clean \
	host-auth-check \
	devcontainer-up devcontainer-down devcontainer-logs devcontainer-shell devcontainer-codex devcontainer-codex-task devcontainer-sync-auth \
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

# ── Dev: terminal-first Codex devcontainer ────────────────────────────────────

host-auth-check: ## Verify host auth.json contains ChatGPT-managed Codex auth
	@if [ ! -f "$$HOME/.codex/auth.json" ]; then \
		echo "Host auth cache not found at $$HOME/.codex/auth.json"; \
		echo 'Configure `cli_auth_credentials_store = "file"` in $$HOME/.codex/config.toml and run `codex login` on the host first.'; \
		exit 1; \
	fi
	@if ! command -v jq >/dev/null 2>&1; then \
		echo "jq is required for host-auth-check"; \
		exit 1; \
	fi
	@AUTH_FILE="$${CODEX_HOME:-$$HOME/.codex}/auth.json"; \
	jq '{auth_mode, has_tokens: (.tokens != null), has_refresh_token: ((.tokens.refresh_token // "") != ""), last_refresh}' "$$AUTH_FILE"; \
	auth_mode=$$(jq -r '.auth_mode // empty' "$$AUTH_FILE"); \
	has_refresh_token=$$(jq -r '((.tokens.refresh_token // "") != "")' "$$AUTH_FILE"); \
	if [ "$$auth_mode" != "chatgpt" ] || [ "$$has_refresh_token" != "true" ]; then \
		echo ""; \
		echo "Host auth.json is not ready for devcontainer sync."; \
		echo "Expected auth_mode=chatgpt and has_refresh_token=true."; \
		exit 1; \
	fi

devcontainer-up: ## Build and start the Codex devcontainer workspace + PostgreSQL
	docker compose -f .devcontainer/docker-compose.yml up -d --build
	@echo ""
	@echo "  Devcontainer workspace ready."
	@echo "  Run Codex TUI:   make devcontainer-codex"
	@echo "  Run a task file: make devcontainer-codex-task TASK=tasks/00-base-it.md"
	@echo "  Check host auth: make host-auth-check"
	@echo "  Sync host auth:  make devcontainer-sync-auth"
	@echo "  Open a shell:    make devcontainer-shell"
	@echo "  Stop services:   make devcontainer-down"

devcontainer-shell: ## Open a shell inside the Codex devcontainer workspace
	docker compose -f .devcontainer/docker-compose.yml exec workspace bash

devcontainer-codex: ## Run Codex inside the devcontainer workspace
	docker compose -f .devcontainer/docker-compose.yml exec workspace bash -lc '\
		cd /workspaces/timetracker && \
		if ! codex login status >/dev/null 2>&1; then \
			echo "Codex is not logged in inside the devcontainer."; \
			echo "Run: make devcontainer-sync-auth"; \
			exit 1; \
		fi && \
		exec codex'

devcontainer-codex-task: ## Run Codex in batch mode on TASK=path/to/task-file inside the devcontainer
	@if [ -z "$(TASK)" ]; then \
		echo "Usage: make devcontainer-codex-task TASK=tasks/00-base-it.md [ARGS='--json']"; \
		exit 1; \
	fi
	docker compose -f .devcontainer/docker-compose.yml exec workspace bash -lc '\
		cd /workspaces/timetracker && \
		./scripts/run-codex-task.sh "$(TASK)" $(ARGS)'

devcontainer-sync-auth: host-auth-check ## Copy host ~/.codex/auth.json into the devcontainer CODEX_HOME
	@if [ ! -f "$$HOME/.codex/auth.json" ]; then \
		echo "Host auth cache not found at $$HOME/.codex/auth.json"; \
		exit 1; \
	fi
	@mkdir -p .tmp
	@cp "$$HOME/.codex/auth.json" .tmp/devcontainer-auth.json
	@chmod 600 .tmp/devcontainer-auth.json
	docker compose -f .devcontainer/docker-compose.yml exec workspace bash -lc '\
		install -d -m 700 "$${CODEX_HOME:-$$HOME/.codex}" && \
		install -m 600 /workspaces/timetracker/.tmp/devcontainer-auth.json "$${CODEX_HOME:-$$HOME/.codex}/auth.json"'
	@rm -f .tmp/devcontainer-auth.json
	@echo "Host Codex auth cache copied into the devcontainer."

devcontainer-logs: ## Tail devcontainer logs
	docker compose -f .devcontainer/docker-compose.yml logs -f

devcontainer-down: ## Stop the Codex devcontainer services
	docker compose -f .devcontainer/docker-compose.yml down

# ── Spec/regeneration workflows ───────────────────────────────────────────────

process-phase2: ## Run the Phase 2 orchestration script
	./scripts/phase2.sh run

process-phase2-check: ## Validate the current Phase 2 baseline
	./scripts/phase2.sh check
