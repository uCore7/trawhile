-- trawhile — V3: seed company_settings singleton
-- Update name and timezone via the settings UI after first login.

INSERT INTO company_settings (name, timezone, retention_years, node_retention_extra_years, purge_schedule)
VALUES ('My Company', 'UTC', 7, 1, 'annual');
