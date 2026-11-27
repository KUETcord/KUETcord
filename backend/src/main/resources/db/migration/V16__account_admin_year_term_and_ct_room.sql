-- ==============================================================================
-- Flyway Migration V16: My Account, Admin CRUD, Canonical Year-Term & CT Exam Room
-- Owned by: Member A (Team Lead & Security Architect)
-- ==============================================================================

-- 1. Extend users for My Account (Year-Term, Roll No, Bio, $0.00 WebP Avatar)
ALTER TABLE users
    ADD COLUMN year_term VARCHAR(10),
    ADD COLUMN roll_number VARCHAR(20),
    ADD COLUMN bio VARCHAR(280),
    ADD COLUMN avatar_url TEXT;

ALTER TABLE users
    ADD CONSTRAINT chk_users_year_term
    CHECK (year_term IS NULL OR year_term IN ('1-1','1-2','2-1','2-2','3-1','3-2','4-1','4-2'));

-- 2. Extend course_offerings with canonical year_term (1-1 .. 4-2) and target_batch
ALTER TABLE course_offerings
    ADD COLUMN year_term VARCHAR(10),
    ADD COLUMN target_batch VARCHAR(20);

UPDATE course_offerings
SET year_term = CASE LOWER(TRIM(semester))
    WHEN '1st' THEN '1-1'
    WHEN '1-1' THEN '1-1'
    WHEN '2nd' THEN '1-2'
    WHEN '1-2' THEN '1-2'
    WHEN '3rd' THEN '2-1'
    WHEN '2-1' THEN '2-1'
    WHEN '4th' THEN '2-2'
    WHEN '2-2' THEN '2-2'
    WHEN '5th' THEN '3-1'
    WHEN '3-1' THEN '3-1'
    WHEN '6th' THEN '3-2'
    WHEN '3-2' THEN '3-2'
    WHEN '7th' THEN '4-1'
    WHEN '4-1' THEN '4-1'
    WHEN '8th' THEN '4-2'
    WHEN '4-2' THEN '4-2'
    ELSE '3-1'
END
WHERE year_term IS NULL;

ALTER TABLE course_offerings
    ALTER COLUMN year_term SET DEFAULT '3-1',
    ALTER COLUMN year_term SET NOT NULL;

ALTER TABLE course_offerings
    ADD CONSTRAINT chk_courses_year_term
    CHECK (year_term IN ('1-1','1-2','2-1','2-2','3-1','3-2','4-1','4-2'));

CREATE INDEX idx_courses_dept_year_term
    ON course_offerings (department, year_term);

-- 3. Extend ct_events with exam_room and target_batch
ALTER TABLE ct_events
    ADD COLUMN exam_room VARCHAR(100),
    ADD COLUMN target_batch VARCHAR(20);
