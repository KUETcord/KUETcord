-- ==============================================================================
-- Flyway Migration V6: Contest & Challenge Board hardening
-- Owned by: Member C (Collaboration & Projects Lead)
--
-- V3 is already applied on shared databases, so it is never edited. Everything the
-- Week 1 opportunity backend needs on top of the V3 baseline is added here:
--   * moderator verification audit columns (who verified, when)
--   * CHECK constraints so invalid enums / team sizes / event windows cannot be
--     written even by code paths that bypass DTO validation
--   * indexes for the "recently published" candidate scan and bookmark counts
-- ==============================================================================

-- 1. Verification audit trail
ALTER TABLE opportunities
    ADD COLUMN verified_by BIGINT REFERENCES users(id) ON DELETE SET NULL,
    ADD COLUMN verified_at TIMESTAMP WITH TIME ZONE;

-- 2. Domain integrity (mirrors the Java enums in com.kuetcord.opportunities.model)
ALTER TABLE opportunities
    ADD CONSTRAINT chk_opp_category
        CHECK (category IN ('PROGRAMMING_CONTEST', 'HACKATHON', 'ROBOTICS', 'RESEARCH', 'CASE_COMPETITION')),
    ADD CONSTRAINT chk_opp_mode
        CHECK (mode IN ('ONLINE', 'ON_SITE', 'HYBRID')),
    ADD CONSTRAINT chk_opp_verification_status
        CHECK (verification_status IN ('UNVERIFIED', 'VERIFIED')),
    ADD CONSTRAINT chk_opp_team_size
        CHECK (team_size_min >= 1 AND team_size_max >= team_size_min),
    ADD CONSTRAINT chk_opp_event_window
        CHECK (event_end >= event_start);

-- 3. Query support
-- Candidate scan for ranking strategies reads newest listings first.
CREATE INDEX idx_opp_created_at ON opportunities (created_at DESC, id DESC);

-- uq_user_opportunity_bookmark leads with user_id; counting bookmarks per opportunity
-- needs its own index to avoid a sequential scan.
CREATE INDEX idx_opp_bookmarks_opportunity ON opportunity_bookmarks (opportunity_id);
