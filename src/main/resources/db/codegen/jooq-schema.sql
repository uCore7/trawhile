-- Build-only schema subset for jOOQ code generation.
-- Generated from spec/schema.sql by scripts/generate-jooq-schema.sh.
-- Runtime schema remains the canonical spec/schema.sql and derived Flyway migration.
-- Runtime PostgreSQL functions are intentionally omitted here because the OSS jOOQ DDLDatabase parser cannot parse CREATE FUNCTION.

CREATE TABLE nodes (
  id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
  parent_id       UUID        REFERENCES nodes(id),
  display_name    TEXT        NOT NULL,
  description     TEXT,
  color           TEXT,                              -- CSS hex e.g. '#4A90D9'
  icon            TEXT,                              -- PrimeIcons identifier e.g. 'pi-briefcase'
  logo            BYTEA,                             -- up to 256 KB decoded; SR-02-F03.F02
  logo_mime_type  TEXT,                              -- one of 'image/png', 'image/jpeg', 'image/svg+xml', 'image/webp'
  is_active       BOOLEAN     NOT NULL DEFAULT TRUE,
  sort_order      INTEGER     NOT NULL DEFAULT 0,
  deactivated_at  TIMESTAMPTZ,                       -- non-NULL ⇔ is_active = false
  created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CHECK (octet_length(coalesce(logo, ''::bytea)) <= 262144),                -- 256 KB cap (SR-02-F03.F02)
  CHECK ((logo IS NULL) = (logo_mime_type IS NULL)),                        -- logo and mime travel together
  CHECK (deactivated_at IS NULL OR is_active = false)
);

CREATE TABLE users (
  id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
  display_name  TEXT,                                -- NOT NULL on active rows; NULL on anonymised
  email         VARCHAR(320),                        -- NOT NULL on active rows; NULL on anonymised; RFC 5321 max length
  anonymised_at TIMESTAMPTZ,                         -- non-NULL ⇔ user is anonymised
  created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CHECK (anonymised_at IS NOT NULL OR display_name IS NOT NULL),
  CHECK (anonymised_at IS NOT NULL OR email IS NOT NULL)
);

CREATE TABLE user_oauth_providers (
  id        UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id   UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  provider  VARCHAR(255) NOT NULL,                   -- registration id from SR-00-C02.F02
  subject   VARCHAR(512) NOT NULL,                   -- OIDC `sub` claim
  linked_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  UNIQUE (provider, subject),
  UNIQUE (user_id, provider)
);

CREATE TABLE user_profile (
  user_id              UUID  PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
  last_report_filters  JSONB
);

CREATE TABLE pending_invitations (
  id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id     UUID         NOT NULL UNIQUE REFERENCES users(id),
  email       VARCHAR(320) NOT NULL UNIQUE,                       -- RFC 5321 max length
  invited_by  UUID         REFERENCES users(id) ON DELETE SET NULL,
  invited_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  expires_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW() + INTERVAL '90 days'
);

CREATE TYPE auth_level AS ENUM ('view', 'track', 'admin');

CREATE TABLE node_authorizations (
  id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
  node_id     UUID        NOT NULL REFERENCES nodes(id) ON DELETE CASCADE,
  user_id     UUID        NOT NULL REFERENCES users(id),
  auth_level  auth_level  NOT NULL,
  granted_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  UNIQUE (node_id, user_id)
);

CREATE TABLE time_records (
  id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id      UUID        NOT NULL REFERENCES users(id),
  node_id      UUID        NOT NULL REFERENCES nodes(id),
  started_at   TIMESTAMPTZ NOT NULL,
  ended_at     TIMESTAMPTZ,
  description  TEXT,                                  -- max 256 Unicode chars (SR-03-F03.C01)
  created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CHECK (ended_at IS NULL OR ended_at >= started_at),
  CHECK (description IS NULL OR char_length(description) <= 256)
);

CREATE TABLE quick_access (
  id          UUID    PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id     UUID    NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  node_id     UUID    NOT NULL REFERENCES nodes(id) ON DELETE CASCADE,
  sort_order  INTEGER NOT NULL DEFAULT 0,
  UNIQUE (user_id, node_id)
);

CREATE TABLE api_keys (
  id             UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id        UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  name           TEXT        NOT NULL,
  scope_node_id  UUID        NOT NULL REFERENCES nodes(id),
  scope_level    auth_level  NOT NULL,
  key_hash       VARCHAR(64) NOT NULL UNIQUE,         -- SHA-256 hex of the raw key value (exactly 64 hex chars)
  created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  expires_at     TIMESTAMPTZ NOT NULL,
  last_used_at   TIMESTAMPTZ,
  revoked_at     TIMESTAMPTZ,
  CHECK (char_length(name) <= 64),                                          -- SR-08-F01.C01
  CHECK (expires_at > created_at)
  -- The 1-year max lifetime (SR-08-F01.C02) is enforced by the application
  -- service of SR-08-F01.F01 at create time, and the persistence port (SR-08-F03.C02)
  -- exposes no path that mutates expires_at post-creation; no DB CHECK is needed.
);

CREATE TABLE webhook_subscriptions (
  id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id             UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  endpoint_url        TEXT        NOT NULL,
  signing_secret_hash TEXT        NOT NULL,
  status              TEXT        NOT NULL DEFAULT 'active',                -- 'active' | 'paused' | 'revoked'
  created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  rotated_at          TIMESTAMPTZ
);

CREATE TABLE webhook_deliveries (
  id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
  subscription_id UUID        NOT NULL REFERENCES webhook_subscriptions(id) ON DELETE CASCADE,
  event_type      TEXT        NOT NULL,
  payload         JSONB       NOT NULL,
  status          TEXT        NOT NULL DEFAULT 'pending',                   -- 'pending' | 'delivered' | 'transient_failure' | 'permanent_failure'
  attempt_count   INTEGER     NOT NULL DEFAULT 0,
  next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  last_status     TEXT,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  delivered_at    TIMESTAMPTZ
);

CREATE TABLE purge_jobs (
  id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
  job_type        VARCHAR(64) NOT NULL UNIQUE,                              -- see comment below for the enum
  status          TEXT        NOT NULL DEFAULT 'idle',                      -- 'idle' | 'active'
  cutoff_date     DATE,
  started_at      TIMESTAMPTZ,
  completed_at    TIMESTAMPTZ,
  deleted_counts  JSONB,
  last_updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CHECK (job_type IN ('time_record_retention', 'node_retention', 'invitation_expiry', 'open_record_auto_close'))
);
