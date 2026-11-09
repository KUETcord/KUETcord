-- ==============================================================================
-- Flyway Migration V1: Baseline Identity, Spring Security RBAC & CT Hub Engine
-- Owned by: Member A (Team Lead & Security Architect)
-- ==============================================================================

-- 1. Users Table
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    display_name VARCHAR(100) NOT NULL,
    username VARCHAR(50) NOT NULL UNIQUE,
    password_hash VARCHAR(255), -- Nullable for Google-only sign-ins
    department VARCHAR(50),
    batch VARCHAR(20),
    email_verified_at TIMESTAMP WITH TIME ZONE,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_username ON users(username);

-- 2. Global Platform Roles Table (Spring Security RBAC)
CREATE TABLE roles (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE, -- 'ROLE_STUDENT', 'ROLE_CR', 'ROLE_MODERATOR', 'ROLE_ADMIN'
    description VARCHAR(255)
);

-- 3. User Roles Join Table (M:N)
CREATE TABLE user_roles (
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_id BIGINT NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, role_id)
);

INSERT INTO roles (name, description) VALUES
('ROLE_STUDENT', 'Standard authenticated KUET student'),
('ROLE_CR', 'Class Representative authorized to manage CT events'),
('ROLE_MODERATOR', 'Campus safety and content moderator'),
('ROLE_ADMIN', 'System administrator with root privileges');

-- 4. External Auth Identities (OIDC)
CREATE TABLE auth_identities (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    provider VARCHAR(50) NOT NULL, -- 'GOOGLE' or 'LOCAL'
    provider_subject VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_auth_provider_sub UNIQUE (provider, provider_subject)
);

-- 5. Email Verification Tokens (Hashed at rest)
CREATE TABLE email_verification_tokens (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(255) NOT NULL UNIQUE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    consumed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_evt_user ON email_verification_tokens(user_id);

-- 6. Course Offerings
CREATE TABLE course_offerings (
    id BIGSERIAL PRIMARY KEY,
    course_code VARCHAR(20) NOT NULL,
    course_title VARCHAR(150) NOT NULL,
    department VARCHAR(50) NOT NULL,
    semester VARCHAR(20) NOT NULL,
    academic_year VARCHAR(10) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_course_semester UNIQUE (course_code, semester, academic_year)
);

CREATE INDEX idx_courses_dept_sem ON course_offerings(department, semester);

-- 7. Course Follows (M:N)
CREATE TABLE course_follows (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    course_offering_id BIGINT NOT NULL REFERENCES course_offerings(id) ON DELETE CASCADE,
    followed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_user_course_follow UNIQUE (user_id, course_offering_id)
);

-- 8. CT Events (State Pattern & JPA @Version Optimistic Locking)
CREATE TABLE ct_events (
    id BIGSERIAL PRIMARY KEY,
    course_offering_id BIGINT NOT NULL REFERENCES course_offerings(id) ON DELETE CASCADE,
    title VARCHAR(150) NOT NULL,
    scheduled_at TIMESTAMP WITH TIME ZONE NOT NULL,
    duration_minutes INT NOT NULL DEFAULT 45,
    covered_topics TEXT,
    status VARCHAR(30) NOT NULL DEFAULT 'UNVERIFIED', -- 'UNVERIFIED', 'VERIFIED', 'RESCHEDULED', 'CANCELLED'
    submitted_by BIGINT NOT NULL REFERENCES users(id),
    verified_by BIGINT REFERENCES users(id),
    version BIGINT NOT NULL DEFAULT 0, -- JPA Optimistic Locking column
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ct_course_schedule ON ct_events(course_offering_id, scheduled_at);
CREATE INDEX idx_ct_status ON ct_events(status);

-- 9. CT Event History & Audit Log
CREATE TABLE ct_event_history (
    id BIGSERIAL PRIMARY KEY,
    ct_event_id BIGINT NOT NULL REFERENCES ct_events(id) ON DELETE CASCADE,
    previous_status VARCHAR(30),
    new_status VARCHAR(30) NOT NULL,
    previous_scheduled_at TIMESTAMP WITH TIME ZONE,
    new_scheduled_at TIMESTAMP WITH TIME ZONE,
    reason TEXT,
    changed_by BIGINT NOT NULL REFERENCES users(id),
    changed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- 10. Transactional Outbox Table (Used across modules)
CREATE TABLE outbox_events (
    id BIGSERIAL PRIMARY KEY,
    aggregate_type VARCHAR(50) NOT NULL,
    aggregate_id VARCHAR(50) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING', -- 'PENDING', 'PROCESSED', 'DEAD_LETTER'
    retry_count INT NOT NULL DEFAULT 0,
    processed_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_outbox_pending ON outbox_events(status, occurred_at) WHERE status = 'PENDING';

