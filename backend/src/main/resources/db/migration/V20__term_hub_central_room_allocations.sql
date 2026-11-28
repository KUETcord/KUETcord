-- =====================================================================
-- V20: Central Room Arrangement (Roll Ranges) for Term Hub Schedules
-- Moves multi-room roll-range seating arrangement to the schedule header
-- so all exam rows in a Term Final schedule share central room allocations.
-- =====================================================================

ALTER TABLE term_final_schedules
    ADD COLUMN IF NOT EXISTS exam_room VARCHAR(255),
    ADD COLUMN IF NOT EXISTS room_allocations JSONB NOT NULL DEFAULT '[]'::jsonb;
