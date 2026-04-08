-- trawhile — V4: seed purge_jobs singleton rows

INSERT INTO purge_jobs (job_type, status) VALUES ('activity', 'idle');
INSERT INTO purge_jobs (job_type, status) VALUES ('node',     'idle');
