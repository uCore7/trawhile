-- trawhile — V1: full schema
-- Table order respects FK dependencies.

CREATE TABLE company_settings (
  id                         UUID    PRIMARY KEY DEFAULT gen_random_uuid(),
  name                       TEXT    NOT NULL,
  timezone                   TEXT    NOT NULL DEFAULT 'UTC',
  freeze_date                DATE,
  retention_years            INTEGER NOT NULL DEFAULT 7,
  node_retention_extra_years INTEGER NOT NULL DEFAULT 1,
  purge_schedule             TEXT    NOT NULL DEFAULT 'annual'
);

CREATE TABLE nodes (
  id             UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
  parent_id      UUID        REFERENCES nodes(id),
  name           TEXT        NOT NULL,
  description    TEXT,
  is_active      BOOLEAN     NOT NULL DEFAULT TRUE,
  sort_order     INTEGER     NOT NULL DEFAULT 0,
  created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  deactivated_at TIMESTAMPTZ
);

CREATE TABLE users (
  id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
  is_active       BOOLEAN     NOT NULL DEFAULT TRUE,
  is_system_admin BOOLEAN     NOT NULL DEFAULT FALSE,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE user_profile (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id     UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
  name        TEXT NOT NULL,
  picture_url TEXT
);

CREATE TABLE user_oauth_providers (
  id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  profile_id UUID NOT NULL REFERENCES user_profile(id) ON DELETE CASCADE,
  provider   TEXT NOT NULL,
  subject    TEXT NOT NULL,
  UNIQUE (provider, subject)
);

CREATE TABLE pending_memberships (
  id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
  email      TEXT        NOT NULL UNIQUE,
  invited_by UUID        NOT NULL REFERENCES users(id),
  invited_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TYPE auth_level AS ENUM ('view', 'track', 'admin');

CREATE TABLE node_authorizations (
  id            UUID       PRIMARY KEY DEFAULT gen_random_uuid(),
  node_id       UUID       NOT NULL REFERENCES nodes(id) ON DELETE CASCADE,
  user_id       UUID       NOT NULL REFERENCES users(id),
  authorization auth_level NOT NULL,
  UNIQUE (node_id, user_id)
);

CREATE TABLE time_entries (
  id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id    UUID        NOT NULL REFERENCES users(id),
  node_id    UUID        NOT NULL REFERENCES nodes(id),
  started_at TIMESTAMPTZ NOT NULL,
  ended_at   TIMESTAMPTZ,
  timezone   TEXT        NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX time_entries_one_active_per_user
  ON time_entries (user_id)
  WHERE ended_at IS NULL;

CREATE TABLE quick_access (
  id         UUID    PRIMARY KEY DEFAULT gen_random_uuid(),
  profile_id UUID    NOT NULL REFERENCES user_profile(id) ON DELETE CASCADE,
  node_id    UUID    NOT NULL REFERENCES nodes(id) ON DELETE CASCADE,
  sort_order INTEGER NOT NULL DEFAULT 0,
  UNIQUE (profile_id, node_id)
);

CREATE TABLE node_colors (
  id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  profile_id UUID NOT NULL REFERENCES user_profile(id) ON DELETE CASCADE,
  node_id    UUID NOT NULL REFERENCES nodes(id) ON DELETE CASCADE,
  color      TEXT NOT NULL,
  UNIQUE (profile_id, node_id)
);

CREATE TABLE requests (
  id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
  requester_id UUID        NOT NULL REFERENCES users(id),
  node_id      UUID        NOT NULL REFERENCES nodes(id),
  template     TEXT        NOT NULL,
  body         TEXT,
  status       TEXT        NOT NULL DEFAULT 'open',
  created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  resolved_at  TIMESTAMPTZ,
  resolved_by  UUID        REFERENCES users(id)
);

CREATE TABLE security_events (
  id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id     UUID        REFERENCES users(id),
  event_type  TEXT        NOT NULL,
  details     JSONB,
  ip_address  TEXT,
  occurred_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE purge_jobs (
  id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
  job_type        TEXT        NOT NULL UNIQUE,
  status          TEXT        NOT NULL DEFAULT 'idle',
  cutoff_date     DATE,
  started_at      TIMESTAMPTZ,
  completed_at    TIMESTAMPTZ,
  deleted_counts  JSONB,
  last_updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
