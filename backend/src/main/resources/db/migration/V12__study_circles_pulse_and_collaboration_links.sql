-- V12: Study Circles, Campus Pulse and collaboration links (Member C, Week 3)
-- Member C owns V3, V6, V9, V12. Builds on the V3 baseline tables, which are never edited.

-- 1. Study circles: role whitelist, lookups for "my circles", course filter and the newest-first directory.
ALTER TABLE study_circle_members
    ADD CONSTRAINT chk_circle_member_role CHECK (role IN ('HOST', 'ATTENDEE'));

CREATE INDEX idx_circle_members_user ON study_circle_members (user_id);
CREATE INDEX idx_circles_course ON study_circles (course_offering_id) WHERE course_offering_id IS NOT NULL;
CREATE INDEX idx_circles_created ON study_circles (created_at DESC, id DESC);

-- 2. Study sessions: who scheduled it, a sane length, and the upcoming-sessions index.
ALTER TABLE study_sessions
    ADD COLUMN created_by BIGINT REFERENCES users (id) ON DELETE SET NULL;

ALTER TABLE study_sessions
    ADD CONSTRAINT chk_session_duration CHECK (duration_minutes BETWEEN 15 AND 480);

CREATE INDEX idx_sessions_circle_start ON study_sessions (study_circle_id, scheduled_start, id);

-- 3. Study resources: category whitelist and newest-first listing.
ALTER TABLE study_resources
    ADD CONSTRAINT chk_resource_category CHECK (category IN ('NOTES', 'QUESTION_BANK', 'SLIDES', 'RECORDING'));

CREATE INDEX idx_resources_circle_created ON study_resources (study_circle_id, created_at DESC, id DESC);

-- 4. Campus Pulse: category whitelist and the unfiltered newest-first feed
--    (idx_announcements_cat_date from V3 already serves the category filter).
ALTER TABLE announcements
    ADD CONSTRAINT chk_announcement_category CHECK (category IN ('SEMINAR', 'CLUB', 'ACADEMIC', 'CAREER'));

CREATE INDEX idx_announcements_published ON announcements (published_at DESC, id DESC);

-- 5. Milestones can be completed (emits MILESTONE_REACHED); record when and by whom.
ALTER TABLE project_milestones
    ADD COLUMN completed_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN completed_by BIGINT REFERENCES users (id) ON DELETE SET NULL;

-- 6. Opportunity -> Playground links: who formed the team and when; reverse lookup by playground.
ALTER TABLE opportunity_playgrounds
    ADD COLUMN linked_by BIGINT REFERENCES users (id) ON DELETE SET NULL,
    ADD COLUMN linked_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW();

CREATE INDEX idx_opp_playgrounds_playground ON opportunity_playgrounds (playground_id);
