-- ==============================================================================
-- Flyway Migration V3: Contests, Playgrounds, Study Circles & Campus Pulse
-- Owned by: Member C (Collaboration & Projects Lead)
-- ==============================================================================

-- 1. Contest & Challenge Board (Strictly isolated from CT Hub)
CREATE TABLE opportunities (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(150) NOT NULL,
    category VARCHAR(50) NOT NULL, -- 'HACKATHON', 'PROGRAMMING_CONTEST', 'ROBOTICS', 'CASE_COMPETITION'
    organizer VARCHAR(100) NOT NULL,
    source_url VARCHAR(500) NOT NULL,
    registration_deadline TIMESTAMP WITH TIME ZONE NOT NULL,
    event_start TIMESTAMP WITH TIME ZONE NOT NULL,
    event_end TIMESTAMP WITH TIME ZONE NOT NULL,
    mode VARCHAR(30) NOT NULL DEFAULT 'ONLINE', -- 'ONLINE', 'ON_SITE', 'HYBRID'
    eligibility TEXT,
    team_size_min INT NOT NULL DEFAULT 1,
    team_size_max INT NOT NULL DEFAULT 4,
    description TEXT,
    verification_status VARCHAR(30) NOT NULL DEFAULT 'UNVERIFIED',
    submitted_by BIGINT NOT NULL REFERENCES users(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_opp_deadline ON opportunities(registration_deadline);
CREATE INDEX idx_opp_category ON opportunities(category);
CREATE INDEX idx_opp_cat_status_deadline ON opportunities(category, verification_status, registration_deadline ASC);

CREATE TABLE opportunity_bookmarks (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    opportunity_id BIGINT NOT NULL REFERENCES opportunities(id) ON DELETE CASCADE,
    bookmarked_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_user_opportunity_bookmark UNIQUE (user_id, opportunity_id)
);

-- 2. Project Playgrounds (Workspaces & Teams)
CREATE TABLE playgrounds (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(150) NOT NULL,
    problem_statement TEXT,
    tags VARCHAR(255),
    visibility VARCHAR(30) NOT NULL DEFAULT 'PUBLIC', -- 'PUBLIC', 'PRIVATE'
    status VARCHAR(30) NOT NULL DEFAULT 'PLANNING', -- 'PLANNING', 'IN_PROGRESS', 'COMPLETED', 'ARCHIVED'
    repo_url VARCHAR(255),
    demo_url VARCHAR(255),
    conversation_id BIGINT UNIQUE REFERENCES conversations(id) ON DELETE SET NULL,
    owner_id BIGINT NOT NULL REFERENCES users(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE TABLE playground_members (
    id BIGSERIAL PRIMARY KEY,
    playground_id BIGINT NOT NULL REFERENCES playgrounds(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role VARCHAR(30) NOT NULL DEFAULT 'CONTRIBUTOR', -- 'LEAD', 'CONTRIBUTOR', 'VIEWER'
    joined_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_playground_user UNIQUE (playground_id, user_id)
);

CREATE TABLE opportunity_playgrounds (
    opportunity_id BIGINT NOT NULL REFERENCES opportunities(id) ON DELETE CASCADE,
    playground_id BIGINT NOT NULL REFERENCES playgrounds(id) ON DELETE CASCADE,
    PRIMARY KEY (opportunity_id, playground_id)
);

-- 3. Kanban Project Tasks (with JPA @Version Concurrency Control)
CREATE TABLE project_tasks (
    id BIGSERIAL PRIMARY KEY,
    playground_id BIGINT NOT NULL REFERENCES playgrounds(id) ON DELETE CASCADE,
    title VARCHAR(150) NOT NULL,
    description TEXT,
    assignee_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'BACKLOG', -- 'BACKLOG', 'TODO', 'IN_PROGRESS', 'REVIEW', 'DONE'
    priority VARCHAR(20) NOT NULL DEFAULT 'MEDIUM', -- 'LOW', 'MEDIUM', 'HIGH', 'CRITICAL'
    due_date TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0, -- JPA Optimistic Locking column
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_tasks_playground_status ON project_tasks(playground_id, status);

CREATE TABLE project_milestones (
    id BIGSERIAL PRIMARY KEY,
    playground_id BIGINT NOT NULL REFERENCES playgrounds(id) ON DELETE CASCADE,
    title VARCHAR(100) NOT NULL,
    due_date TIMESTAMP WITH TIME ZONE NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE TABLE project_decisions (
    id BIGSERIAL PRIMARY KEY,
    playground_id BIGINT NOT NULL REFERENCES playgrounds(id) ON DELETE CASCADE,
    decision TEXT NOT NULL,
    rationale TEXT,
    author_id BIGINT NOT NULL REFERENCES users(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- 4. Study Circles Hub
CREATE TABLE study_circles (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    course_offering_id BIGINT REFERENCES course_offerings(id) ON DELETE SET NULL,
    conversation_id BIGINT UNIQUE REFERENCES conversations(id) ON DELETE SET NULL,
    creator_id BIGINT NOT NULL REFERENCES users(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE TABLE study_circle_members (
    id BIGSERIAL PRIMARY KEY,
    study_circle_id BIGINT NOT NULL REFERENCES study_circles(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role VARCHAR(30) NOT NULL DEFAULT 'ATTENDEE', -- 'HOST', 'ATTENDEE'
    joined_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_circle_user UNIQUE (study_circle_id, user_id)
);

CREATE TABLE study_sessions (
    id BIGSERIAL PRIMARY KEY,
    study_circle_id BIGINT NOT NULL REFERENCES study_circles(id) ON DELETE CASCADE,
    title VARCHAR(150) NOT NULL,
    scheduled_start TIMESTAMP WITH TIME ZONE NOT NULL,
    duration_minutes INT NOT NULL DEFAULT 60,
    meeting_link VARCHAR(255),
    agenda TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE TABLE study_resources (
    id BIGSERIAL PRIMARY KEY,
    study_circle_id BIGINT NOT NULL REFERENCES study_circles(id) ON DELETE CASCADE,
    title VARCHAR(150) NOT NULL,
    resource_url VARCHAR(500) NOT NULL,
    category VARCHAR(50) NOT NULL, -- 'NOTES', 'QUESTION_BANK', 'SLIDES', 'RECORDING'
    contributed_by BIGINT NOT NULL REFERENCES users(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- 5. Campus Pulse Notices
CREATE TABLE announcements (
    id BIGSERIAL PRIMARY KEY,
    organization VARCHAR(100) NOT NULL, -- 'CSE_DEPT', 'ROBOTICS_CLUB', 'CENTRAL_STUDENT_UNION'
    category VARCHAR(50) NOT NULL, -- 'SEMINAR', 'CLUB', 'ACADEMIC', 'CAREER'
    title VARCHAR(200) NOT NULL,
    body TEXT NOT NULL,
    source_url VARCHAR(500),
    published_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    author_id BIGINT NOT NULL REFERENCES users(id)
);

CREATE INDEX idx_announcements_cat_date ON announcements(category, published_at DESC);

