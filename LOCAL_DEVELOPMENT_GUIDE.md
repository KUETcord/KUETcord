# KUETcord — Local Development Setup & Teammate Onboarding Guide

> **Target Audience:** All 3 Engineering Team Members & Evaluators  
> **Repository:** [https://github.com/KUETcord/KUETcord](https://github.com/KUETcord/KUETcord)  
> **Goal:** Pull the codebase, configure the environment, and spin up the complete full-stack platform locally in under 3 minutes.

---

## 1. Prerequisites & Required Tooling

Ensure the following tools are installed on your operating system:

| Tool | Recommended Version | Verification Command | Notes |
|---|---|---|---|
| **Git** | `2.40+` | `git --version` | Required for branching, rebasing, and pushing |
| **Java Development Kit (JDK)** | `Java 21 LTS` | `java -version` | Eclipse Temurin 21 recommended |
| **Node.js & npm** | `Node 20+`, `npm 10+` | `node -v && npm -v` | Required for Vite frontend and Vitest |
| **Docker & Docker Compose** | `Docker 24+`, `Compose v2+` | `docker compose version` | Required for containers and PostgreSQL |
| **k6 (Optional)** | `0.48+` | `k6 version` | Required for running load benchmark scripts |

---

## 2. Step 1: Clone the Official Repository

Clone the repository and switch to the active integration branch (**`develop`**):

```bash
# Clone via HTTPS:
git clone https://github.com/KUETcord/KUETcord.git

# Enter repository directory:
cd KUETcord

# Switch to develop branch (where active integration occurs):
git checkout develop

# Verify git remote and branch:
git status
```

---

## 3. Step 2: Environment Configuration (`.env`)

KUETcord uses a unified `.env` file to configure database credentials, JWT secrets, and port mappings across Docker and local services.

Create your local `.env` file by copying the provided sanitized template:

```bash
# On Linux / macOS / Git Bash:
cp .env.example .env

# On Windows PowerShell:
copy .env.example .env
```

### Key Environment Variables Explained:
```ini
# PostgreSQL Configuration
POSTGRES_DB=kuetcord_db
POSTGRES_USER=kuetcord_admin
POSTGRES_PASSWORD=kuetcord_secure_pass
POSTGRES_PORT=5432

# Backend Spring Boot Configuration
BACKEND_PORT=8080
SPRING_PROFILES_ACTIVE=docker
JWT_SECRET=c2VjdXJlX2p3dF90ZXN0X3NlY3JldF9mb3JfZ2l0aHViX2FjdGlvbnNfMTIzNDU2Nzg5MA==
ALLOWED_ORIGINS=http://localhost:5173,http://localhost:3000

# Frontend React + Vite Configuration
FRONTEND_PORT=5173
VITE_API_BASE_URL=http://localhost:8080/api/v1
VITE_WS_BASE_URL=ws://localhost:8080/ws
```

---

## 4. Step 3: Option A — 1-Command Full Stack Launch (Recommended)

To start the entire platform (PostgreSQL, Backend, Frontend, Mailpit, Prometheus, and Grafana) inside isolated Docker containers:

```bash
docker compose up --build
```

### Accessing Local Services:

| Service | Local URL | Credentials / Notes |
|---|---|---|
| **Frontend Web App** | `http://localhost:5173` | React 18 + Vite UI with instant hot reloading |
| **Backend REST API** | `http://localhost:8080/api/v1` | Spring Boot 3.3.x Modular Monolith |
| **Actuator Health Probe** | `http://localhost:8080/actuator/health` | Returns `{"status":"UP"}` |
| **Prometheus Metrics** | `http://localhost:8080/actuator/prometheus` | JVM, HTTP, and STOMP metrics feed |
| **Mailpit Web UI** | `http://localhost:8025` | Web interface to inspect outgoing verification emails |
| **Prometheus Dashboard** | `http://localhost:9090` | Metrics scraper UI and targets status |
| **Grafana Dashboard** | `http://localhost:3000` | Login: `admin` / `admin` (Pre-configured JVM/HTTP dashboards) |

### Pre-Seeded Test Fixtures (Ready for Viva Demo)
The database automatically seeds 4 deterministic test accounts on startup from `backend/src/main/resources/db/seed/test_fixtures.sql`:

* **Team Lead / Admin:** `rahim.admin@stud.kuet.ac.bd` (Password: `Kuetcord!2026`)
* **CR / Academic Lead:** `karim.cr@stud.kuet.ac.bd` (Password: `Kuetcord!2026`)
* **Student A:** `sadia.student@stud.kuet.ac.bd` (Password: `Kuetcord!2026`)
* **Student B:** `tanvir.student@stud.kuet.ac.bd` (Password: `Kuetcord!2026`)

To stop all containers:
```bash
docker compose down
```

---

## 5. Step 4: Option B — Native Local Development (Fast Code Reload)

If you are actively editing Java backend code or React frontend components and want instant compiler feedback without rebuilding Docker images:

### 1. Start only database and mail dependencies in Docker:
```bash
docker compose up -d postgres mailpit
```

### 2. Run the Spring Boot Backend natively:
```bash
cd backend

# On Linux / macOS:
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# On Windows:
mvn spring-boot:run -Dspring-boot.run.profiles=dev
# Or run KuetcordApplication.java directly inside IntelliJ IDEA / VS Code
```
Backend will start on `http://localhost:8080`.

### 3. Run the React Frontend natively with Vite HMR:
Open a second terminal window:
```bash
cd frontend

# Install Node dependencies (first time only):
npm install

# Start Vite dev server:
npm run dev
```
Frontend will start on `http://localhost:5173` with sub-50ms Hot Module Replacement (HMR).

---

## 6. Step 5: Database Migrations & Verification (Flyway)

Flyway manages schema migrations and runs automatically every time the backend starts.
Migration scripts are located at: `backend/src/main/resources/db/migration/`
* `V1__baseline_identity_and_cthub.sql`: Users, Auth, CT Events, Course Offerings.
* `V2__baseline_messaging_and_notifications.sql`: Communities, Discord Server RBAC, Group DMs, Messages, Notifications.
* `V3__baseline_collaboration_and_opportunities.sql`: Opportunities, Playgrounds, Kanban Tasks, Study Circles.

### Connecting to PostgreSQL via CLI / GUI:
* **Host:** `localhost`
* **Port:** `5432`
* **Database:** `kuetcord_db`
* **Username:** `kuetcord_admin`
* **Password:** `kuetcord_secure_pass`

CLI connection command:
```bash
docker exec -it kuetcord_postgres psql -U kuetcord_admin -d kuetcord_db
```

---

## 7. Step 6: Executing the Multi-Tier Test Suite

Run automated quality checks before submitting Pull Requests:

```bash
# -----------------------------------------------------------------
# 1. Run Backend Unit & Integration Tests (JUnit 5 + MockMvc)
# -----------------------------------------------------------------
cd backend
mvn clean test                  # Runs 26 Unit Tests
mvn verify                      # Runs 14 Integration & Concurrency Tests
cd ..

# -----------------------------------------------------------------
# 2. Run Frontend TypeScript Compilation & Vitest Suites
# -----------------------------------------------------------------
cd frontend
npm run typecheck               # Strict TypeScript validation (tsc --noEmit)
npm run test                    # Component, A11y, and break-ui tests
npm run build                   # Verifies production Vite bundle builds cleanly
cd ..

# -----------------------------------------------------------------
# 3. Execute k6 Load Benchmark Tests
# -----------------------------------------------------------------
k6 run tests/load/k6_chat_pagination.js    # Message history cursor seek test
k6 run tests/load/k6_ct_read.js            # CT Hub composite index query test
k6 run tests/load/k6_kanban_tasks.js       # Kanban board task loading test
```

---

## 8. Step 7: Teammate GitFlow Protocol

All team members must follow this daily Git routine:

### Starting Your Day:
```bash
# 1. Ensure you are on develop and sync upstream:
git checkout develop
git pull --ff-only origin develop

# 2. Create your isolated feature branch:
git checkout -b feature/<module>-<short-task-name>
# Example: git checkout -b feature/ct-state-machine
```

### Before Submitting a PR:
```bash
# 1. Verify tests pass locally:
cd backend && mvn test-compile && mvn test && cd ../frontend && npm run typecheck && npm run test && cd ..

# 2. Commit with Conventional Commits:
git add .
git commit -m "feat(ct-hub): add verification state machine and optimistic lock"

# 3. Pull latest integration changes via rebase:
git fetch origin
git rebase origin/develop

# 4. Push safely:
git push -u origin feature/<module>-<short-task-name>
# Or if rebased:
git push --force-with-lease origin feature/<module>-<short-task-name>

# 5. Open Pull Request targeting develop:
# Visit: https://github.com/KUETcord/KUETcord/compare/develop...feature/<module>-<short-task-name>
```

---

## 9. Common Setup Troubleshooting

### Issue 1: Port `5432` or `8080` already in use?
* **Cause:** A local PostgreSQL service or Tomcat instance is running on your host machine.
* **Fix:** Stop the local service or change the external port mapping in your `.env` file (e.g. `POSTGRES_PORT=5433` or `BACKEND_PORT=8081`).

### Issue 2: Docker fails with `permission denied` or volume errors?
* **Fix:** Reset Docker volumes and restart:
  ```bash
  docker compose down -v
  docker compose up --build
  ```

### Issue 3: Mailpit not receiving verification emails?
* **Fix:** Verify `SPRING_MAIL_HOST=mailpit` and `SPRING_MAIL_PORT=1025` in `.env`. Open `http://localhost:8025` in your browser to view incoming verification emails.

