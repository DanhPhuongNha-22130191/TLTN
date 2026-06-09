-- Seed data for external user sub = fb96bcbf-8a57-405c-97ba-3250c7ddacdc
-- Postgres-compatible seed file

BEGIN;

-- 1) ensure a mapping table from external sub (Keycloak `sub`) to internal numeric user id
CREATE TABLE IF NOT EXISTS external_users (
  id BIGSERIAL PRIMARY KEY,
  external_sub VARCHAR(255) UNIQUE NOT NULL
);

-- 2) ensure some conversation rows to reference (ids 1..5)
CREATE TABLE IF NOT EXISTS conversations (
  id BIGINT PRIMARY KEY,
  type VARCHAR(50) NOT NULL,
  receiver_id BIGINT,
  group_id BIGINT,
  last_message_id BIGINT,
  last_message_at TIMESTAMP,
  unread_count INT NOT NULL DEFAULT 0,
  created_at TIMESTAMP NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMP
);

-- Create the external user if not exists
INSERT INTO external_users (external_sub)
  VALUES ('fb96bcbf-8a57-405c-97ba-3250c7ddacdc')
  ON CONFLICT (external_sub) DO NOTHING;

-- Create a few conversations with explicit ids if they don't already exist
INSERT INTO conversations (id, type, group_id, receiver_id, created_at, unread_count)
  VALUES
    (1, 'GROUP', 10, NULL, NOW(), 0),
    (3, 'GROUP', 20, NULL, NOW(), 0),
    (5, 'GROUP', 30, NULL, NOW(), 0)
  ON CONFLICT (id) DO NOTHING;

-- 3) Insert multiple seed messages for the external user (sender_id resolved via sub -> external_users.id)
-- Adjust the number of messages as desired

-- Ensure messages table exists (only minimal structure used by seed). If your real schema differs, remove this clause.
CREATE TABLE IF NOT EXISTS messages (
  id BIGSERIAL PRIMARY KEY,
  conversation_id BIGINT NOT NULL,
  sender_id BIGINT NOT NULL,
  content TEXT,
  file_url TEXT,
  file_name TEXT,
  file_size BIGINT,
  file_type VARCHAR(255),
  message_type VARCHAR(50) NOT NULL,
  reply_to_id BIGINT,
  is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
  deleted_at TIMESTAMP,
  deleted_by BIGINT,
  created_at TIMESTAMP NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMP
);

-- Insert 30 seed messages
INSERT INTO messages (conversation_id, sender_id, content, message_type, is_deleted, created_at, updated_at)
VALUES
((1), (SELECT id FROM external_users WHERE external_sub='fb96bcbf-8a57-405c-97ba-3250c7ddacdc'), 'Seed message 1 from fb96...', 'TEXT', false, NOW() - INTERVAL '30 minutes', NOW() - INTERVAL '30 minutes'),
((3), (SELECT id FROM external_users WHERE external_sub='fb96bcbf-8a57-405c-97ba-3250c7ddacdc'), 'Seed message 3 from fb96...', 'TEXT', false, NOW() - INTERVAL '28 minutes', NOW() - INTERVAL '28 minutes'),
((5), (SELECT id FROM external_users WHERE external_sub='fb96bcbf-8a57-405c-97ba-3250c7ddacdc'), 'Seed message 5 from fb96...', 'TEXT', false, NOW() - INTERVAL '26 minutes', NOW() - INTERVAL '26 minutes'),
((1), (SELECT id FROM external_users WHERE external_sub='fb96bcbf-8a57-405c-97ba-3250c7ddacdc'), 'Seed message 6 from fb96...', 'TEXT', false, NOW() - INTERVAL '25 minutes', NOW() - INTERVAL '25 minutes'),
((3), (SELECT id FROM external_users WHERE external_sub='fb96bcbf-8a57-405c-97ba-3250c7ddacdc'), 'Seed message 8 from fb96...', 'TEXT', false, NOW() - INTERVAL '23 minutes', NOW() - INTERVAL '23 minutes'),
((5), (SELECT id FROM external_users WHERE external_sub='fb96bcbf-8a57-405c-97ba-3250c7ddacdc'), 'Seed message 10 from fb96...', 'TEXT', false, NOW() - INTERVAL '21 minutes', NOW() - INTERVAL '21 minutes'),
((1), (SELECT id FROM external_users WHERE external_sub='fb96bcbf-8a57-405c-97ba-3250c7ddacdc'), 'Seed message 11 from fb96...', 'TEXT', false, NOW() - INTERVAL '20 minutes', NOW() - INTERVAL '20 minutes'),
((3), (SELECT id FROM external_users WHERE external_sub='fb96bcbf-8a57-405c-97ba-3250c7ddacdc'), 'Seed message 13 from fb96...', 'TEXT', false, NOW() - INTERVAL '18 minutes', NOW() - INTERVAL '18 minutes'),
((5), (SELECT id FROM external_users WHERE external_sub='fb96bcbf-8a57-405c-97ba-3250c7ddacdc'), 'Seed message 15 from fb96...', 'TEXT', false, NOW() - INTERVAL '16 minutes', NOW() - INTERVAL '16 minutes'),
((1), (SELECT id FROM external_users WHERE external_sub='fb96bcbf-8a57-405c-97ba-3250c7ddacdc'), 'Seed message 16 from fb96...', 'TEXT', false, NOW() - INTERVAL '15 minutes', NOW() - INTERVAL '15 minutes'),
((3), (SELECT id FROM external_users WHERE external_sub='fb96bcbf-8a57-405c-97ba-3250c7ddacdc'), 'Seed message 18 from fb96...', 'TEXT', false, NOW() - INTERVAL '13 minutes', NOW() - INTERVAL '13 minutes'),
((5), (SELECT id FROM external_users WHERE external_sub='fb96bcbf-8a57-405c-97ba-3250c7ddacdc'), 'Seed message 20 from fb96...', 'TEXT', false, NOW() - INTERVAL '11 minutes', NOW() - INTERVAL '11 minutes'),
((1), (SELECT id FROM external_users WHERE external_sub='fb96bcbf-8a57-405c-97ba-3250c7ddacdc'), 'Seed message 21 from fb96...', 'TEXT', false, NOW() - INTERVAL '10 minutes', NOW() - INTERVAL '10 minutes'),
((3), (SELECT id FROM external_users WHERE external_sub='fb96bcbf-8a57-405c-97ba-3250c7ddacdc'), 'Seed message 23 from fb96...', 'TEXT', false, NOW() - INTERVAL '8 minutes', NOW() - INTERVAL '8 minutes'),
((5), (SELECT id FROM external_users WHERE external_sub='fb96bcbf-8a57-405c-97ba-3250c7ddacdc'), 'Seed message 25 from fb96...', 'TEXT', false, NOW() - INTERVAL '6 minutes', NOW() - INTERVAL '6 minutes'),
((1), (SELECT id FROM external_users WHERE external_sub='fb96bcbf-8a57-405c-97ba-3250c7ddacdc'), 'Seed message 26 from fb96...', 'TEXT', false, NOW() - INTERVAL '5 minutes', NOW() - INTERVAL '5 minutes'),
((3), (SELECT id FROM external_users WHERE external_sub='fb96bcbf-8a57-405c-97ba-3250c7ddacdc'), 'Seed message 28 from fb96...', 'TEXT', false, NOW() - INTERVAL '3 minutes', NOW() - INTERVAL '3 minutes'),
((5), (SELECT id FROM external_users WHERE external_sub='fb96bcbf-8a57-405c-97ba-3250c7ddacdc'), 'Seed message 30 from fb96...', 'TEXT', false, NOW() - INTERVAL '1 minute', NOW() - INTERVAL '1 minute');

COMMIT;

-- Usage: run this SQL against your Postgres DB for the chat-service (e.g. psql -h $DB_HOST -p $DB_PORT -d $DB_NAME -U $DB_USER -f seed_messages_for_sub_fb96bcbf-8a57-405c-97ba-3250c7ddacdc.sql)
