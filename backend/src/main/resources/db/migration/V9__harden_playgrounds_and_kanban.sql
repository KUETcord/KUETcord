-- V9: Project Playgrounds & Kanban hardening (Member C, Week 2)
-- Builds on the V3 baseline tables. Enum-like columns get CHECK constraints so a bad write fails in the
-- database even if it bypasses the API, and the Kanban board gets the columns it needs for ordering and
-- optimistic concurrency on the playground itself.

-- 1. Playgrounds: optimistic locking for metadata edits (title, status, links) by co-leads.
ALTER TABLE playgrounds
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE playgrounds
    ADD CONSTRAINT chk_pg_visibility CHECK (visibility IN ('PUBLIC', 'PRIVATE')),
    ADD CONSTRAINT chk_pg_status CHECK (status IN ('PLANNING', 'IN_PROGRESS', 'COMPLETED', 'ARCHIVED'));

-- 2. Members: role whitelist and a lookup index for "my playgrounds".
ALTER TABLE playground_members
    ADD CONSTRAINT chk_pg_member_role CHECK (role IN ('LEAD', 'CONTRIBUTOR', 'VIEWER'));

CREATE INDEX idx_pg_members_user ON playground_members (user_id);

-- 3. Kanban tasks: card order inside a column, task author, value whitelists.
ALTER TABLE project_tasks
    ADD COLUMN position INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN created_by BIGINT REFERENCES users (id) ON DELETE SET NULL;

ALTER TABLE project_tasks
    ADD CONSTRAINT chk_task_status CHECK (status IN ('BACKLOG', 'TODO', 'IN_PROGRESS', 'REVIEW', 'DONE')),
    ADD CONSTRAINT chk_task_priority CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    ADD CONSTRAINT chk_task_position CHECK (position >= 0);

-- Give rows that existed before V9 a stable order inside their column (oldest first).
UPDATE project_tasks t
SET position = ranked.rn - 1
FROM (SELECT id, ROW_NUMBER() OVER (PARTITION BY playground_id, status ORDER BY created_at, id) AS rn
      FROM project_tasks) ranked
WHERE ranked.id = t.id;

CREATE INDEX idx_tasks_assignee ON project_tasks (assignee_id) WHERE assignee_id IS NOT NULL;

-- 4. Milestones: optional description, author, status whitelist, timeline index.
ALTER TABLE project_milestones
    ADD COLUMN description TEXT,
    ADD COLUMN created_by BIGINT REFERENCES users (id) ON DELETE SET NULL;

ALTER TABLE project_milestones
    ADD CONSTRAINT chk_milestone_status CHECK (status IN ('PENDING', 'COMPLETED'));

CREATE INDEX idx_milestones_playground_due ON project_milestones (playground_id, due_date, id);

-- 5. Decision log: newest-first listing per playground.
CREATE INDEX idx_decisions_playground_created ON project_decisions (playground_id, created_at DESC, id DESC);
