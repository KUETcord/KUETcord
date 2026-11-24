-- ==============================================================================
-- Flyway Migration V10: Add Teacher Role & CR-to-Teacher CT Approval Workflow
-- Owned by: Member A (Team Lead & Security Architect)
-- ==============================================================================

-- 1. Seed ROLE_TEACHER in global platform roles
INSERT INTO roles (name, description)
VALUES ('ROLE_TEACHER', 'Course teacher authorized to approve, reschedule, or cancel CT schedules')
ON CONFLICT (name) DO NOTHING;

-- 2. Assign a course teacher to each CT event for approval & notifications
ALTER TABLE ct_events
    ADD COLUMN assigned_teacher_id BIGINT REFERENCES users(id);

CREATE INDEX idx_ct_assigned_teacher
    ON ct_events (assigned_teacher_id, status);

-- 3. Support fast case-insensitive teacher name search
CREATE INDEX idx_users_display_name_lower
    ON users (LOWER(display_name));
