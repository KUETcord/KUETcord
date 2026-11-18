-- ==============================================================================
-- Flyway Migration V5: Align Messaging, Notification & Outbox Schemas with Contracts
-- Owned by: Member B (Real-Time Systems & Messaging Lead)
-- Requires review by: Member A (touches shared outbox_events table from V1)
-- ==============================================================================

-- 1. Direct DM pair uniqueness: one DIRECT conversation per unordered user pair.
--    direct_key = '<smallerUserId>:<largerUserId>', set only for type = 'DIRECT'.
--    Concurrent "open DM" requests race on this index instead of creating duplicates.
ALTER TABLE conversations ADD COLUMN direct_key VARCHAR(50);
CREATE UNIQUE INDEX uq_conversations_direct_key
    ON conversations(direct_key)
    WHERE direct_key IS NOT NULL;

-- 2. Private community channels: access requires explicit channel membership.
ALTER TABLE conversations ADD COLUMN is_private BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX idx_conversations_community ON conversations(community_id) WHERE community_id IS NOT NULL;

-- 3. "My conversations" listing and membership checks.
CREATE INDEX idx_conv_members_user_active ON conversation_members(user_id, active_status);

-- 4. Notifications: replace the V2 shape with the target-reference contract from
--    MEMBER_B_IMPLEMENTATION_PLAN.md §3. No application code writes this table yet,
--    so it is rebuilt rather than altered column by column.
DROP TABLE IF EXISTS notifications;

CREATE TABLE notifications (
    id BIGSERIAL PRIMARY KEY,
    recipient_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type VARCHAR(50) NOT NULL,
    title VARCHAR(200) NOT NULL,
    preview VARCHAR(300),
    target_type VARCHAR(50) NOT NULL,
    target_id BIGINT NOT NULL,
    source_event_id VARCHAR(100),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    read_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uq_notification_dedup UNIQUE (recipient_id, source_event_id, type)
);

CREATE INDEX idx_notif_inbox ON notifications(recipient_id, read_at, id DESC);

-- 5. Outbox: bounded-backoff retry support for the SKIP LOCKED worker.
--    Existing columns (retry_count, status) are kept as-is for Member A compatibility.
--    status values: 'PENDING', 'PROCESSING', 'PROCESSED', 'DEAD_LETTER'.
ALTER TABLE outbox_events ADD COLUMN next_attempt_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE outbox_events ADD COLUMN last_error_code VARCHAR(100);

CREATE INDEX idx_outbox_poll ON outbox_events(status, next_attempt_at, id);
