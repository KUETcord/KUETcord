-- ==============================================================================
-- Flyway Migration V2: Baseline Messaging, Discord Server/Group RBAC & Notifications
-- Owned by: Member B (Real-Time Systems & Messaging Lead)
-- ==============================================================================

-- 1. Communities and Discord Server RBAC
CREATE TABLE communities (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    visibility VARCHAR(30) NOT NULL DEFAULT 'PUBLIC', -- 'PUBLIC', 'PRIVATE'
    owner_id BIGINT NOT NULL REFERENCES users(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE TABLE community_roles (
    id BIGSERIAL PRIMARY KEY,
    community_id BIGINT NOT NULL REFERENCES communities(id) ON DELETE CASCADE,
    name VARCHAR(50) NOT NULL,
    color VARCHAR(10) DEFAULT '#99AAB5',
    position INT NOT NULL DEFAULT 0,
    permissions BIGINT NOT NULL DEFAULT 0, -- Bitmask: MANAGE_SERVER, MANAGE_ROLES, KICK, BAN, etc.
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_comm_roles_comm ON community_roles(community_id);

CREATE TABLE community_members (
    id BIGSERIAL PRIMARY KEY,
    community_id BIGINT NOT NULL REFERENCES communities(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    nickname VARCHAR(50),
    joined_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_community_user UNIQUE (community_id, user_id)
);

CREATE TABLE community_member_roles (
    community_id BIGINT NOT NULL REFERENCES communities(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_id BIGINT NOT NULL REFERENCES community_roles(id) ON DELETE CASCADE,
    assigned_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    PRIMARY KEY (community_id, user_id, role_id)
);

CREATE TABLE community_bans (
    id BIGSERIAL PRIMARY KEY,
    community_id BIGINT NOT NULL REFERENCES communities(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    banned_by BIGINT NOT NULL REFERENCES users(id),
    reason VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_comm_ban UNIQUE (community_id, user_id)
);

-- 2. Conversations Engine (Shared Across Modules) & Discord Group DM RBAC
CREATE TABLE conversations (
    id BIGSERIAL PRIMARY KEY,
    type VARCHAR(30) NOT NULL, -- 'CHANNEL', 'DIRECT', 'GROUP_DM', 'PLAYGROUND', 'STUDY_CIRCLE'
    title VARCHAR(150),
    community_id BIGINT REFERENCES communities(id) ON DELETE CASCADE,
    creator_id BIGINT REFERENCES users(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_conversations_type ON conversations(type);

CREATE TABLE conversation_members (
    id BIGSERIAL PRIMARY KEY,
    conversation_id BIGINT NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    group_role VARCHAR(30) NOT NULL DEFAULT 'GROUP_MEMBER', -- 'GROUP_OWNER', 'GROUP_ADMIN', 'GROUP_MEMBER'
    joined_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    active_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE', -- 'ACTIVE', 'LEFT', 'REMOVED'
    left_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uq_conv_member UNIQUE (conversation_id, user_id)
);

-- 3. Messages with Idempotency Key Unique Index
CREATE TABLE messages (
    id BIGSERIAL PRIMARY KEY,
    conversation_id BIGINT NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    sender_id BIGINT NOT NULL REFERENCES users(id),
    content TEXT NOT NULL,
    idempotency_key VARCHAR(64),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    edited_at TIMESTAMP WITH TIME ZONE,
    deleted_at TIMESTAMP WITH TIME ZONE
);

CREATE UNIQUE INDEX uq_msg_idempotency 
    ON messages(conversation_id, sender_id, idempotency_key) 
    WHERE idempotency_key IS NOT NULL;

CREATE INDEX idx_messages_cursor 
    ON messages(conversation_id, id DESC);

CREATE INDEX idx_messages_sync 
    ON messages(conversation_id, id ASC);

CREATE TABLE message_mentions (
    id BIGSERIAL PRIMARY KEY,
    message_id BIGINT NOT NULL REFERENCES messages(id) ON DELETE CASCADE,
    mentioned_user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_message_mention UNIQUE (message_id, mentioned_user_id)
);

CREATE TABLE conversation_read_states (
    id BIGSERIAL PRIMARY KEY,
    conversation_id BIGINT NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    last_read_message_id BIGINT NOT NULL REFERENCES messages(id) ON DELETE CASCADE,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_conv_read_state UNIQUE (conversation_id, user_id)
);

-- 4. Notification Center and Preferences
CREATE TABLE notification_preferences (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    conversation_id BIGINT NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    mode VARCHAR(30) NOT NULL DEFAULT 'ALL_MESSAGES', -- 'ALL_MESSAGES', 'MENTIONS_ONLY', 'MUTED'
    mute_until TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_user_conv_pref UNIQUE (user_id, conversation_id)
);

CREATE TABLE notifications (
    id BIGSERIAL PRIMARY KEY,
    recipient_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type VARCHAR(50) NOT NULL,
    title VARCHAR(150) NOT NULL,
    content TEXT,
    reference_id VARCHAR(100),
    read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_notification_dedup UNIQUE (recipient_id, reference_id, type)
);

CREATE INDEX idx_notif_inbox ON notifications(recipient_id, read, created_at DESC);

