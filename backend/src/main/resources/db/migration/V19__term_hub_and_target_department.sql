-- =====================================================================
-- V19: Term Hub (Term Final Examination Schedules) & Cross-Dept Target Batch
-- Owned by Member A (leaves V17 for Member B and V18 for Member C).
--
-- Cross-department rule:
-- Every academic hub tracks target_department + target_year_term (the student
-- batch's department, e.g., CSE 1-1) separately from the course's offering
-- prefix (e.g., EEE 1107), so CSE students see their EEE/MATH/PHY exams & CTs.
-- =====================================================================

-- 1. Cross-department target batch columns on ct_events
ALTER TABLE ct_events
    ADD COLUMN IF NOT EXISTS target_department VARCHAR(16),
    ADD COLUMN IF NOT EXISTS target_year_term VARCHAR(10);

UPDATE ct_events e
SET target_department = COALESCE(e.target_department, c.department),
    target_year_term  = COALESCE(e.target_year_term, c.year_term)
FROM course_offerings c
WHERE e.course_offering_id = c.id
  AND (e.target_department IS NULL OR e.target_year_term IS NULL);

UPDATE ct_events
SET target_department = 'CSE'
WHERE target_department IS NULL;

ALTER TABLE ct_events
    ALTER COLUMN target_department SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_ct_events_target_dept_term
    ON ct_events (target_department, target_year_term);

-- 2. Term Final Examination Schedules (Header)
CREATE TABLE IF NOT EXISTS term_final_schedules (
    id                 BIGSERIAL PRIMARY KEY,
    title              VARCHAR(180) NOT NULL,
    target_department  VARCHAR(16)  NOT NULL,
    year_term          VARCHAR(10)  NOT NULL,
    batch              VARCHAR(20)  NOT NULL,
    session            VARCHAR(20)  NOT NULL,
    exam_start_time    TIME         NOT NULL,
    exam_end_time      TIME         NOT NULL,
    created_by         BIGINT       NOT NULL REFERENCES users(id),
    created_at         TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_term_final_year_term CHECK (year_term IN ('1-1','1-2','2-1','2-2','3-1','3-2','4-1','4-2')),
    CONSTRAINT chk_term_final_time_order CHECK (exam_end_time > exam_start_time)
);

CREATE INDEX IF NOT EXISTS idx_term_schedules_dept_term
    ON term_final_schedules (target_department, year_term, batch);

-- 3. Term Final Examination Entries (Per-Subject Rows; Day auto-calculated from exam_date)
CREATE TABLE IF NOT EXISTS term_final_entries (
    id                 BIGSERIAL PRIMARY KEY,
    schedule_id        BIGINT       NOT NULL REFERENCES term_final_schedules(id) ON DELETE CASCADE,
    exam_date          DATE         NOT NULL,
    course_offering_id BIGINT       REFERENCES course_offerings(id) ON DELETE SET NULL,
    course_code        VARCHAR(30)  NOT NULL,
    course_title       VARCHAR(180) NOT NULL,
    exam_room          VARCHAR(120)
);

CREATE INDEX IF NOT EXISTS idx_term_entries_schedule_date
    ON term_final_entries (schedule_id, exam_date);

CREATE INDEX IF NOT EXISTS idx_term_entries_date
    ON term_final_entries (exam_date);
