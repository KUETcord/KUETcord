-- V21: Promote ct_events into a unified multi-type assessment engine (CT, LABTEST, QUIZ)
-- Powering CT Hub (/ct-hub), Labtest Hub (/labtest-hub), Quiz Hub (/quiz-hub), and /calendar sync.

ALTER TABLE ct_events
    ADD COLUMN IF NOT EXISTS assessment_type VARCHAR(20) NOT NULL DEFAULT 'CT';

ALTER TABLE ct_events
    DROP CONSTRAINT IF EXISTS chk_ct_events_assessment_type;

ALTER TABLE ct_events
    ADD CONSTRAINT chk_ct_events_assessment_type
    CHECK (assessment_type IN ('CT', 'LABTEST', 'QUIZ'));

CREATE INDEX IF NOT EXISTS idx_ct_events_type_dept_term_time
    ON ct_events (assessment_type, target_department, target_year_term, scheduled_at);
