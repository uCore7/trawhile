-- Build-only schema subset for jOOQ code generation.
-- Generated from spec/schema.sql by scripts/generate-jooq-schema.sh.
-- Runtime schema remains the canonical spec/schema.sql and derived Flyway migration.
-- Runtime PostgreSQL functions are intentionally omitted here because the OSS jOOQ DDLDatabase parser cannot parse CREATE FUNCTION.

CREATE TABLE nodes (
  id             UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
  parent_id      UUID        REFERENCES nodes(id),
  name           TEXT        NOT NULL,
  description    TEXT,
  is_active      BOOLEAN     NOT NULL DEFAULT TRUE,
  sort_order     INTEGER     NOT NULL DEFAULT 0,
  created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  deactivated_at TIMESTAMPTZ,                        -- set on deactivation, cleared on reactivation
  color          TEXT,                               -- CSS hex color e.g. '#4A90D9'; optional
  icon           TEXT,                               -- PrimeIcons identifier e.g. 'pi-briefcase'; optional
  logo           BYTEA,                              -- uploaded image, max 256 KB; takes precedence over icon
  logo_mime_type TEXT                                -- MIME type of logo e.g. 'image/png'
);

CREATE TABLE users (
  id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE user_profile (
  id                    UUID    PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id               UUID    NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
  name                  TEXT    NOT NULL,
  last_report_settings  JSONB                            -- last used report filter state; persisted for multi-device consistency
);

CREATE TYPE auth_level AS ENUM ('view', 'track', 'admin');

CREATE TABLE node_authorizations (
  id            UUID       PRIMARY KEY DEFAULT gen_random_uuid(),
  node_id       UUID       NOT NULL REFERENCES nodes(id) ON DELETE CASCADE,
  user_id       UUID       NOT NULL REFERENCES users(id),
  auth_level    auth_level NOT NULL,
  UNIQUE (node_id, user_id)
);

CREATE TABLE time_records (
  id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id     UUID        NOT NULL REFERENCES users(id),
  node_id     UUID        NOT NULL REFERENCES nodes(id),
  started_at  TIMESTAMPTZ NOT NULL,
  ended_at    TIMESTAMPTZ,
  timezone    TEXT        NOT NULL,             -- IANA string from browser; private (discloses coarse location) — protected by per-owner access control on time_records
  description TEXT,                             -- optional short note by the member
  created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE quick_access (
  id         UUID    PRIMARY KEY DEFAULT gen_random_uuid(),
  profile_id UUID    NOT NULL REFERENCES user_profile(id) ON DELETE CASCADE,
  node_id    UUID    NOT NULL REFERENCES nodes(id) ON DELETE CASCADE,
  sort_order INTEGER NOT NULL DEFAULT 0,
  UNIQUE (profile_id, node_id)
);

CREATE TABLE requests (
  id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
  requester_id UUID        NOT NULL REFERENCES users(id),
  node_id      UUID        NOT NULL REFERENCES nodes(id),
  template     TEXT        NOT NULL,  -- system template ID or 'free_text'
  body         TEXT,
  status       TEXT        NOT NULL DEFAULT 'open',  -- 'open' | 'closed'
  created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  resolved_at  TIMESTAMPTZ,
  resolved_by  UUID        REFERENCES users(id)
);
