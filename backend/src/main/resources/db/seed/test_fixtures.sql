-- ==============================================================================
-- KUETcord Deterministic Presentation & Testing Fixtures
-- Run this seed script to populate realistic data for the 15-minute viva demo
-- ==============================================================================

-- 1. Seed Users (All passwords are 'Password123!' hashed with BCrypt 12 rounds)
-- Hash: $2a$12$K8pQ0q2v6QGZ5oY9N1/cXe5m9mNlZgQ9jUoE/GzO7aH6V3b8o9vC6
INSERT INTO users (id, email, display_name, username, password_hash, department, batch, email_verified_at, status) VALUES
(1, 'student.1907001@stud.kuet.ac.bd', 'Tashfiq Rahman (CR)', 'tashfiq_cr', '$2a$12$K8pQ0q2v6QGZ5oY9N1/cXe5m9mNlZgQ9jUoE/GzO7aH6V3b8o9vC6', 'CSE', '2k19', NOW(), 'ACTIVE'),
(2, 'student.1907002@stud.kuet.ac.bd', 'Sadia Afrin', 'sadia_afrin', '$2a$12$K8pQ0q2v6QGZ5oY9N1/cXe5m9mNlZgQ9jUoE/GzO7aH6V3b8o9vC6', 'CSE', '2k19', NOW(), 'ACTIVE'),
(3, 'student.2005010@stud.kuet.ac.bd', 'Tanvir Ahmed', 'tanvir_eee', '$2a$12$K8pQ0q2v6QGZ5oY9N1/cXe5m9mNlZgQ9jUoE/GzO7aH6V3b8o9vC6', 'EEE', '2k20', NOW(), 'ACTIVE'),
(4, 'moderator@stud.kuet.ac.bd', 'Campus Safety Moderator', 'kuet_mod', '$2a$12$K8pQ0q2v6QGZ5oY9N1/cXe5m9mNlZgQ9jUoE/GzO7aH6V3b8o9vC6', 'CSE', 'Faculty/Staff', NOW(), 'ACTIVE')
ON CONFLICT (id) DO NOTHING;

-- Reset identity sequence
SELECT setval('users_id_seq', (SELECT MAX(id) FROM users));

-- 2. Seed User Roles
INSERT INTO user_roles (user_id, role_id) VALUES
(1, 1), -- Tashfiq: ROLE_STUDENT
(1, 2), -- Tashfiq: ROLE_CR
(2, 1), -- Sadia: ROLE_STUDENT
(3, 1), -- Tanvir: ROLE_STUDENT
(4, 1), -- Moderator: ROLE_STUDENT
(4, 3)  -- Moderator: ROLE_MODERATOR
ON CONFLICT DO NOTHING;

-- 3. Seed Course Offerings
INSERT INTO course_offerings (id, course_code, course_title, department, semester, academic_year) VALUES
(1, 'CSE 3101', 'Theory of Computation', 'CSE', '5th', '2025-2026'),
(2, 'CSE 3103', 'Microprocessors & Microcontrollers', 'CSE', '5th', '2025-2026'),
(3, 'EEE 3110', 'Signals & Systems Analysis', 'EEE', '5th', '2025-2026')
ON CONFLICT (id) DO NOTHING;

SELECT setval('course_offerings_id_seq', (SELECT MAX(id) FROM course_offerings));

-- 4. Seed Course Follows
INSERT INTO course_follows (user_id, course_offering_id) VALUES
(1, 1),
(1, 2),
(2, 1),
(2, 2),
(3, 3)
ON CONFLICT DO NOTHING;

-- 5. Seed CT Events
INSERT INTO ct_events (id, course_offering_id, title, scheduled_at, duration_minutes, covered_topics, status, submitted_by, verified_by, version) VALUES
(1, 1, 'CT-1: DFA, NFA & Regular Expressions', NOW() + INTERVAL '3 days', 45, 'Automata equivalence, Pumping Lemma for Regular Languages', 'VERIFIED', 2, 1, 0),
(2, 2, 'CT-2: 8086 Instruction Set & Addressing Modes', NOW() + INTERVAL '7 days', 45, 'Register indirect, string primitives, memory segmentation', 'UNVERIFIED', 2, NULL, 0),
(3, 1, 'CT-2: Context-Free Grammars & Pushdown Automata', NOW() + INTERVAL '14 days', 45, 'CFG simplification, Chomsky Normal Form, PDA acceptance', 'VERIFIED', 1, 1, 0)
ON CONFLICT (id) DO NOTHING;

SELECT setval('ct_events_id_seq', (SELECT MAX(id) FROM ct_events));

-- 6. Seed Community Server with Discord RBAC
INSERT INTO communities (id, name, description, visibility, owner_id) VALUES
(1, 'KUET CSE 2k19 Official', 'Official hub for CSE 2k19 academic discussions and team projects', 'PUBLIC', 1)
ON CONFLICT (id) DO NOTHING;

SELECT setval('communities_id_seq', (SELECT MAX(id) FROM communities));

INSERT INTO community_roles (id, community_id, name, color, position, permissions, is_default) VALUES
(1, 1, 'Server Owner', '#E74C3C', 10, 127, FALSE), -- Full permissions
(2, 1, 'Batch Moderator', '#2ECC71', 5, 45, FALSE),  -- KICK, MANAGE_MESSAGES
(3, 1, 'Student Member', '#99AAB5', 1, 1, TRUE)       -- SEND_MESSAGES
ON CONFLICT (id) DO NOTHING;

SELECT setval('community_roles_id_seq', (SELECT MAX(id) FROM community_roles));

INSERT INTO community_members (community_id, user_id, nickname) VALUES
(1, 1, 'Tashfiq [CR]'),
(1, 2, 'Sadia A.'),
(1, 4, 'KUET Moderator')
ON CONFLICT DO NOTHING;

INSERT INTO community_member_roles (community_id, user_id, role_id) VALUES
(1, 1, 1),
(1, 4, 2),
(1, 2, 3)
ON CONFLICT DO NOTHING;

-- 7. Seed Group DM Conversation with Discord Group RBAC
INSERT INTO conversations (id, type, title, creator_id) VALUES
(1, 'GROUP_DM', 'CSE 3220 Software Engineering Lab Team', 1)
ON CONFLICT (id) DO NOTHING;

SELECT setval('conversations_id_seq', (SELECT MAX(id) FROM conversations));

INSERT INTO conversation_members (conversation_id, user_id, group_role, active_status) VALUES
(1, 1, 'GROUP_OWNER', 'ACTIVE'),
(1, 2, 'GROUP_ADMIN', 'ACTIVE'),
(1, 3, 'GROUP_MEMBER', 'ACTIVE')
ON CONFLICT DO NOTHING;

-- 8. Seed Sample Messages with Idempotency Keys
INSERT INTO messages (id, conversation_id, sender_id, content, idempotency_key, created_at) VALUES
(1, 1, 1, 'Hey team, did everyone clone the KUETcord repo?', 'init-msg-001', NOW() - INTERVAL '15 minutes'),
(2, 1, 2, 'Yes! Docker compose builds cleanly and PostgreSQL starts up.', 'init-msg-002', NOW() - INTERVAL '12 minutes'),
(3, 1, 3, 'Tested the STOMP WebSocket connection too. Heartbeats are running 10s/10s.', 'init-msg-003', NOW() - INTERVAL '8 minutes')
ON CONFLICT (id) DO NOTHING;

SELECT setval('messages_id_seq', (SELECT MAX(id) FROM messages));

-- 9. Seed Opportunity
INSERT INTO opportunities (id, title, category, organizer, source_url, registration_deadline, event_start, event_end, mode, eligibility, team_size_min, team_size_max, description, verification_status, submitted_by) VALUES
(1, 'KUET Inter-University Hackathon 2026', 'HACKATHON', 'KUET Computer Club', 'https://hackathon.kuet.ac.bd', NOW() + INTERVAL '10 days', NOW() + INTERVAL '20 days', NOW() + INTERVAL '22 days', 'HYBRID', 'Undergraduate Engineering Students', 3, 4, 'Build innovative software solving real university and civic challenges.', 'VERIFIED', 1)
ON CONFLICT (id) DO NOTHING;

SELECT setval('opportunities_id_seq', (SELECT MAX(id) FROM opportunities));

-- 10. Seed Project Playground and Kanban Tasks
INSERT INTO playgrounds (id, title, problem_statement, tags, visibility, status, repo_url, owner_id) VALUES
(1, 'Team KUETcord: Campus Workflow System', 'Unifying CT schedules, competitions, and peer collaboration for KUET students.', 'Spring Boot, React, PostgreSQL, WebSocket', 'PUBLIC', 'IN_PROGRESS', 'https://github.com/kuetcord/kuetcord', 1)
ON CONFLICT (id) DO NOTHING;

SELECT setval('playgrounds_id_seq', (SELECT MAX(id) FROM playgrounds));

INSERT INTO playground_members (playground_id, user_id, role) VALUES
(1, 1, 'LEAD'),
(1, 2, 'CONTRIBUTOR'),
(1, 3, 'CONTRIBUTOR')
ON CONFLICT DO NOTHING;

INSERT INTO project_tasks (id, playground_id, title, description, assignee_id, status, priority, due_date, version) VALUES
(1, 1, 'Implement STOMP Heartbeat Dead-Socket Reaper', 'Detect inactive sockets after 2.5 intervals and evict zombie sessions.', 2, 'DONE', 'CRITICAL', NOW() - INTERVAL '1 day', 1),
(2, 1, 'Build Reconnect /sync Catch-Up Protocol', 'Backfill missed messages upon network reconnect without full reload.', 2, 'IN_PROGRESS', 'HIGH', NOW() + INTERVAL '2 days', 0),
(3, 1, 'Integrate JPA @Version Concurrency on Kanban Cards', 'Detect concurrent drag-and-drop operations and prompt reload banner on 409.', 3, 'TODO', 'HIGH', NOW() + INTERVAL '3 days', 0),
(4, 1, 'Author k6 Load Test Suite for 50 Concurrent VUs', 'Assert p95 latency under 200ms for chat pagination and CT queries.', 1, 'BACKLOG', 'MEDIUM', NOW() + INTERVAL '5 days', 0)
ON CONFLICT (id) DO NOTHING;

SELECT setval('project_tasks_id_seq', (SELECT MAX(id) FROM project_tasks));

