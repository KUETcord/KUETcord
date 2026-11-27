-- V15: Study circle revision checklist (Member C, Week 5)
-- Member C owns V3, V6, V9, V12, V15. A shared, per-circle list of topics to revise before a CT;
-- any member can add an item and tick it off, and the row records who ticked it and when.

CREATE TABLE study_revision_items (
    id BIGSERIAL PRIMARY KEY,
    study_circle_id BIGINT NOT NULL REFERENCES study_circles (id) ON DELETE CASCADE,
    title VARCHAR(200) NOT NULL,
    done BOOLEAN NOT NULL DEFAULT FALSE,
    created_by BIGINT REFERENCES users (id) ON DELETE SET NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    completed_by BIGINT REFERENCES users (id) ON DELETE SET NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    -- A ticked item always says when it was ticked; an open item never does.
    CONSTRAINT chk_revision_item_completion CHECK (done = (completed_at IS NOT NULL)),
    CONSTRAINT chk_revision_item_title CHECK (length(btrim(title)) > 0)
);

-- The checklist reads oldest first, in the order it was typed from the syllabus.
CREATE INDEX idx_revision_items_circle_created ON study_revision_items (study_circle_id, created_at, id);
