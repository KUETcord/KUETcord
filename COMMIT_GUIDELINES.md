# KUETcord — Semantic Commit Guidelines & Version Control Standard

> **Standard:** [Conventional Commits v1.0.0](https://www.conventionalcommits.org/)  
> **Target Audience:** All 3 Engineering Team Members & AI Coding Assistants  
> **Mandate:** Every git commit across all feature branches and pull requests must follow this exact semantic commit structure. Commits violating this specification must be amended before PR merge.

---

## 1. Commit Message Structure

Every commit message consists of a **Header**, an optional **Body**, and an optional **Footer**:

```text
<type>(<scope>): <short imperative description>

[optional body explaining motivation, architectural rationale, and trade-offs]

[optional footer: Closes #issue, ADR reference, Breaking Changes]
```

### 1.1 Summary Line Rules (Max 72 characters)
1. **`<type>`:** Lowercase category indicating the nature of the change (see Section 2).
2. **`(<scope>)`:** Optional but strongly encouraged noun indicating the module affected (see Section 3).
3. **Colon & Space:** Must be followed by `: ` (e.g. `feat(ct-hub): ...`).
4. **Imperative Mood:** Use `"add"` instead of `"added"` or `"adds"`; `"fix"` instead of `"fixed"`; `"implement"` instead of `"implemented"`.
5. **No Capitalization & No Trailing Period:** Keep the description lowercase (unless proper noun) and never end with a full stop.

---

## 2. Commit Types

| Type | Purpose | Example |
|---|---|---|
| `feat` | A new user-facing or platform capability | `feat(auth): add Google OIDC token verification endpoint` |
| `fix` | A bug fix | `fix(messaging): evict zombie socket on STOMP heartbeat timeout` |
| `perf` | A code change that improves performance | `perf(messages): add composite index for cursor pagination` |
| `refactor`| Code restructuring without feature addition or bug fix | `refactor(ct-hub): extract verification logic into State classes` |
| `test` | Adding missing tests or correcting existing tests | `test(kanban): assert 409 conflict on concurrent task update` |
| `docs` | Documentation only changes (READMEs, ADRs, guides) | `docs(readme): add reconnection sync API to central route index` |
| `style` | Formatting, whitespace, semi-colons (no logic change) | `style(chat): apply 65ch line length and tabular-nums to feed` |
| `ci` | CI/CD pipeline changes (GitHub Actions, workflows) | `ci(actions): add deliberate failing test step for CI proof` |
| `chore` | Dependency updates, build configs, pom.xml, package.json | `chore(deps): upgrade bucket4j to 8.10.1` |
| `revert` | Reverts a previous commit | `revert: revert "feat(chat): enable reactions"` |

---

## 3. Approved Scopes (Aligned to KUETcord Modules)

To prevent ambiguous scopes, developers must select from this standardized list:

### Member A Scope Areas
- `auth`: Institutional email/password, BCrypt, verification tokens, Google OIDC.
- `security`: Spring Security filter chain, JWT provider, CORS allowlist.
- `rate-limit`: Token Bucket filter (Bucket4j), IP throttling.
- `ct-hub`: Course offerings, CT calendar, verification lifecycle, CT history.

### Member B Scope Areas
- `commons`: Communities, channels, role memberships.
- `messaging`: Shared conversation engine, 1:1 DMs, group DMs, message persistence.
- `stomp`: WebSocket broker, STOMP heartbeats, dead-socket eviction, channel interceptors.
- `resilience`: Exponential backoff reconnection, gap catch-up sync (`/sync`), idempotency deduplication.
- `presence`: User presence engine (`SimpUserRegistry`), multi-tab tracking, typing indicators.
- `notifications`: Notification inbox, unread counts, Notify/Mute strategy, outbox processor (`SKIP LOCKED`).

### Member C Scope Areas
- `opportunities`: Contest & Challenge Board, categories, filters, bookmarks, source verification.
- `ranking`: Strategy pattern for opportunity ranking (deadline-first, recent).
- `playgrounds`: Project workspaces, member roles (`LEAD`, `CONTRIBUTOR`), contest links.
- `kanban`: Kanban board tasks, status transitions, `@Version` optimistic concurrency control.
- `study-circles`: Study circles, scheduled sessions, UTC timestamp conversion, Pomodoro timer.
- `pulse`: Campus Pulse announcements, university notices.

### Shared & Cross-Cutting Scopes
- `db`: Database migrations (Flyway `V1`, `V2`, `V3`), schema constraints, indexes.
- `docker`: `Dockerfile.backend`, `Dockerfile.frontend`, `docker-compose.yml`.
- `ui`: Shared UI components, layout shell, `DESIGN_GUIDELINES.md` compliance, Tailwind styling.
- `docs`: Central `README.md`, Personal `README_<MEMBER>.md`, guides, or ADRs.

---

## 4. Good vs. Bad Commit Examples

| Bad Commit (Do Not Use) | Semantic Alternative (Required) | Why It Matters |
|---|---|---|
| `update stuff` | `feat(ct-hub): add course-wise CT calendar filters` | Clear intent and affected module |
| `fixed bug` | `fix(auth): reject non-kuet domain emails during registration` | Explains what bug was resolved |
| `WIP on chat` | `feat(messaging): implement cursor pagination for message history` | Atomic, deliverable increment |
| `added animations` | `style(ui): apply scale(0.97) active press feedback on CT buttons` | Precise visual change per guidelines |
| `tests done` | `test(outbox): verify SKIP LOCKED concurrency across worker threads` | Identifies exact scenario covered |
| `docs` | `docs(readme_a): document CT State Pattern and ADR-003` | Identifies target documentation |

---

## 5. Multi-Line Commit Message Template

When introducing architectural patterns, concurrency controls, or breaking changes, developers must provide a descriptive body:

```text
feat(kanban): enforce optimistic locking on task updates

Add JPA @Version column to project_tasks table and check version
in TaskService. When two members attempt to update the same task
simultaneously, the second write throws OptimisticLockException
and returns HTTP 409 Conflict with a reload prompt.

- Add version BIGINT column to project_tasks in V3 migration
- Throw OptimisticLockException on stale expectedVersion
- Add TaskOptimisticLockTest covering race condition
- Update Personal README_MEMBER_C.md with ADR-008

Closes #42
```

---

## 6. Git Workflow & Local Hook Automation

### 6.1 Creating a Semantic Commit
```bash
# Stage your modified files
git add backend/src/main/java/.../CtService.java

# Commit with semantic message
git commit -m "feat(ct-hub): implement State Pattern for CT verification lifecycle"
```

### 6.2 Pre-Commit Enforcement (Husky / Git Hook)
Developers can install a lightweight commit message validator locally in `.git/hooks/commit-msg`:

```bash
#!/bin/sh
# Regex for Conventional Commits
commit_regex='^(feat|fix|perf|refactor|test|docs|style|ci|chore|revert)(\([a-z0-9_-]+\))?: .{1,72}$'

if ! grep -qE "$commit_regex" "$1"; then
    echo "========================================================================"
    echo "ERROR: Invalid commit message format!"
    echo "Follow Conventional Commits: <type>(<scope>): <description>"
    echo "Example: feat(ct-hub): add course filter to CT calendar"
    echo "See COMMIT_GUIDELINES.md for approved types and scopes."
    echo "========================================================================"
    exit 1
fi
```

