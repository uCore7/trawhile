-- Build-only schema subset for jOOQ code generation.
-- Generated from docs/schema.sql by scripts/generate-jooq-schema.sh.
-- Runtime schema remains the canonical docs/schema.sql and derived Flyway migration.
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

CREATE TYPE auth_level AS ENUM ('view', 'track', 'admin');

CREATE TABLE node_authorizations (
  id            UUID       PRIMARY KEY DEFAULT gen_random_uuid(),
  node_id       UUID       NOT NULL REFERENCES nodes(id) ON DELETE CASCADE,
  user_id       UUID       NOT NULL REFERENCES users(id),
  auth_level    auth_level NOT NULL,
  UNIQUE (node_id, user_id)
);
