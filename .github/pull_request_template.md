## Description
<!-- Briefly describe what this PR does, why it is needed, and link the issue -->
Closes #<!-- issue number -->

## Type of Change
<!-- Select all that apply using [x] -->
- [ ] `feat`: New feature or user capability
- [ ] `fix`: Bug fix
- [ ] `perf`: Performance improvement
- [ ] `refactor`: Code refactoring without behavior change
- [ ] `test`: Adding or updating tests
- [ ] `docs`: Documentation updates (README, guides, ADRs)
- [ ] `style`: Visual styling, micro-interactions, layout craft
- [ ] `ci`: CI/CD workflows or Docker updates
- [ ] `chore`: Dependency updates, build configurations

## Module Affected
- [ ] **Member A:** Identity / Security / Rate Limiting / CT Hub
- [ ] **Member B:** Campus Commons / Messaging / STOMP / Presence / Notifications
- [ ] **Member C:** Contest Board / Playgrounds / Kanban / Study Circles / Pulse
- [ ] **Shared:** Platform / DB Migrations / Docker / App Shell

---

## Quality & Engineering Checklist

### 1. Design Engineering Standards ([DESIGN_GUIDELINES.md](../DESIGN_GUIDELINES.md))
- [ ] Headings $\ge 24\text{px}$ use tight tracking (`letter-spacing: -0.02em`).
- [ ] Numbers, dates, timers, and unread counters use `font-variant-numeric: tabular-nums`.
- [ ] Interactive buttons provide press feedback (`transform: scale(0.97)` on `:active`).
- [ ] User avatars enforce `flex-shrink: 0` to prevent oval squishing.
- [ ] Modals scale from `scale(0.95)` centered; popovers scale from trigger with `ease-out` under 200ms (never `ease-in`).
- [ ] Toasts implemented using [Sonner](https://sonner.emilkowal.ski).
- [ ] Tested against worst-case realistic data (`break-ui`): long strings wrap, empty states render cleanly.

### 2. Distributed Systems & Resilience Standards
- [ ] Message / mutation idempotency key supported and enforced where applicable.
- [ ] JPA `@Version` concurrency control checked (throws 409 Conflict on collision).
- [ ] STOMP heartbeats (10s/10s) and `/sync` reconnection recovery maintained.
- [ ] Outbox table inserts and domain state commits occur in the exact same transaction.
- [ ] No N+1 database queries introduced (inspected SQL execution).

### 3. Dual Documentation Mandate
- [ ] **Central [README.md](../README.md):** Updated API Route Index, environment variables, or architecture if altered.
- [ ] **Personal `README_MEMBER_<X>.md`:** Updated sprint log, technical deep-dive specs, test evidence, and ADRs.

### 4. Software Testing & Quality Verification
<!-- Select all testing types covered in this PR -->
- [ ] **Unit Tests:** Algorithmic logic, State transitions, Strategy ranking, or validators tested in isolation (`JUnit 5` / `Vitest`).
- [ ] **Contract & Schema Tests:** DTO validation (`@Valid`), OpenAPI contract adherence, or Flyway migration applied cleanly.
- [ ] **Integration Tests:** REST controllers (`MockMvc`), JPA repositories, or WebSocket frame exchange tested against real/ephemeral database.
- [ ] **Concurrency & Race Conditions:** JPA `@Version` 409 conflict, Transactional Outbox `SKIP LOCKED`, or message idempotency deduplication verified.
- [ ] **Resilience & Chaos:** WebSocket disconnect zombie eviction (25s), `/sync` catch-up recovery, or retry backoff tested.
- [ ] **Security & Pen-Testing:** Domain allowlist checked, Google OIDC token verified, IDOR prevented, or rate limiting tested.
- [ ] **Performance & Load:** k6 load test executed (p95 $\le 200\text{ms}$), or query verified with `EXPLAIN ANALYZE` (zero N+1 queries).
- [ ] **Visual, A11y & Adversarial UI:** Tested with `break-ui` (long unbroken strings, empty states), keyboard navigation, and `tabular-nums`.
- [ ] **Automated CI Pipeline:** Backend verify (`mvn clean verify`), Frontend verify (`npm run typecheck && npm run test`), and `pr-title-lint` pass.

---

## Test Evidence / Screenshots
<!-- Attach terminal test output, k6 results, or UI screenshots/GIFs -->
```text
[Paste test execution logs here]
```

