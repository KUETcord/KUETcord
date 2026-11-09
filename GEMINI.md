# KUETcord Workspace Rules (`GEMINI.md`)

This workspace follows the master directives defined in [`AGENTS.md`](file:///d:/Documents/KUETcord/AGENTS.md) and [`AI_Agent.MD`](file:///d:/Documents/KUETcord/AI_Agent.MD).

### Mandatory Golden Rules:
1. **Autonomous CI/CD Monitoring & Self-Healing Loop:** Whenever any push or PR occurs, listen to the GitHub Actions run (`gh run watch` / `gh run list`). If any check fails, extract logs (`gh run view --log-failed`), diagnose, patch locally, re-test, commit, and re-push, iterating until 100% green (`conclusion == "success"`).
2. **Never Commit to `main` or `develop`:** Create `feature/<module>-<task>` branches, rebase on `origin/develop`, and push with `--force-with-lease`.
3. **Zero Monetary Cost ($0.00):** Maintain all runtime flags for Render 512MB RAM free tier (`-XX:+UseSerialGC -XX:MaxRAMPercentage=75.0`). No paid cloud services.
4. **UI Craft:** Follow [`DESIGN_GUIDELINES.md`](file:///d:/Documents/KUETcord/DESIGN_GUIDELINES.md) (tight tracking `-0.02em`, `tabular-nums`, `:active scale(0.97)`, Sonner toasts).
5. **Discord Optimization Patterns:** Web-Queue-Worker, Viewport Selective Fan-out, Request Coalescing (SingleFlight), Bulkhead Thread Pools.
