# KUETcord — AI Agent & Developer Rules (`AGENTS.md`)

> **Scope:** Repository Root Directive for all AI Assistants (Antigravity, Cursor, Claude Code, GitHub Copilot) & Engineers.  
> **Mandate:** Any agent or engineer modifying this codebase must adhere strictly to the rules below.

---

## 1. Autonomous CI/CD Monitoring & Self-Healing Feedback Loop (CRITICAL)

Whenever an action or CI/CD workflow is triggered (e.g. on `git push`, PR creation, or workflow dispatch):
1. **Never Stop at Push:** The AI agent must **NOT** consider the task completed or conclude its turn immediately after pushing code.
2. **Listen Automatically:** Proactively listen to the triggered GitHub Actions workflow run until completion:
   ```bash
   gh run list --branch <current-branch> --limit 1
   gh run watch <run-id>
   ```
3. **Evaluate Result:**
   - If `conclusion == "success"`: Confirm all checks are green, record status, and proceed.
   - If `conclusion == "failure"` / `cancelled` / `timed_out`: Immediately initiate the autonomous self-healing loop.
4. **Self-Healing Loop (Diagnose -> Fix -> Verify -> Re-push):**
   - Extract failure logs: `gh run view <run-id> --log-failed`.
   - Diagnose root cause (backend Maven test/compile, frontend Vite/Vitest/typecheck, Docker build, or PR title lint).
   - Reproduce and patch locally. Verify with `cd backend && mvn test` and `cd frontend && npm run typecheck && npm run test`.
   - Commit with semantic message (`fix(ci): <imperative diagnosis>`) and push (`--force-with-lease`).
   - Re-enter listening state for the new run. Repeat until the workflow is **100% green**.
5. **Safety Cap:** Up to 5 autonomous healing iterations per run. If third-party infrastructure (e.g. GitHub runner outage) fails, present full diagnostic logs to the user.

---

## 2. Git & Branching Protocol (Strict GitFlow)

1. **Branch Protection:** Never commit directly to `main` or `develop`.
2. **Feature Branching:** Auto-create branches as `feature/<module>-<task>` off `develop`.
3. **Rebase Protocol:** Pull upstream changes via `git fetch origin && git rebase origin/develop` to maintain linear history.
4. **Safe Pushing:** Always push with `git push --force-with-lease`, never raw `--force`.
5. **Conventional Commits:** All commits must follow `<type>(<scope>): <imperative summary>`.
6. **PR Lifecycle:** Open PRs targeting `develop` with `.github/pull_request_template.md`. Squash and merge once CI is green.

---

## 3. Zero-Monetary-Cost ($0.00) Free-Tier Constraints

- **Primary Backend:** Render Free Tier Web Service (512MB RAM cap).
  - Must enforce JVM container flags: `-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+UseSerialGC -XX:MaxMetaspaceSize=128m -XX:ReservedCodeCacheSize=48m -Xss512k`.
  - Max RSS $\approx 290\text{MB}$ to prevent OOM exit 137.
- **Database:** Render Free PostgreSQL (1GB storage) or Neon Serverless PostgreSQL.
- **Frontend:** Vercel Free Tier or Render Static Site.
- **Prohibited:** Never introduce paid cloud services (AWS SQS, Redis Cloud, paid SendGrid). Use local Spring `@Scheduled` + PostgreSQL `SKIP LOCKED` outbox and local Caffeine caches.

---

## 4. Architectural & UI Craft Standards

1. **Architecture:** Modular monolith in Spring Boot 3.3.x + React 18 / TypeScript.
2. **Discord Optimization Patterns:**
   - Web-Queue-Worker: fast ACK $< 5\text{ms}$, async fan-out worker pool.
   - Active vs. Passive Viewport Selective Fan-out: full chat JSON for active viewers, 18-byte `{cid, d}` delta updates for passive guild members.
   - Request Coalescing (SingleFlight): collapse concurrent identical reads with `ConcurrentHashMap<String, CompletableFuture<T>>`.
   - Bulkhead Thread Pool Isolation: separate thread pools for WebSocket, fan-out, outbox, and heartbeats.
3. **Design Engineering (`DESIGN_GUIDELINES.md`):**
   - Headings $\ge 24\text{px}$: tight tracking `-0.02em`.
   - Counters, dates, timers: `tabular-nums`.
   - Buttons: active press feedback `scale(0.97)` with `100ms ease-out`.
   - Avatars: `flex-shrink: 0`.
   - Toasts: Sonner toasts only; never native browser `alert()` or raw unstyled banners.

---

## 5. Master References

- AI Agent Directive: [`AI_Agent.MD`](file:///d:/Documents/KUETcord/AI_Agent.MD)
- Git & PR Protocol: [`GIT_WORKFLOW_GUIDE.md`](file:///d:/Documents/KUETcord/GIT_WORKFLOW_GUIDE.md)
- Conventional Commits: [`COMMIT_GUIDELINES.md`](file:///d:/Documents/KUETcord/COMMIT_GUIDELINES.md)
- Master Plan: [`KUETcord_Project_Plan.md`](file:///d:/Documents/KUETcord/KUETcord_Project_Plan.md)
- Deployment Guide: [`DEPLOYMENT_GUIDE.md`](file:///d:/Documents/KUETcord/DEPLOYMENT_GUIDE.md)
- Local Dev Guide: [`LOCAL_DEVELOPMENT_GUIDE.md`](file:///d:/Documents/KUETcord/LOCAL_DEVELOPMENT_GUIDE.md)
