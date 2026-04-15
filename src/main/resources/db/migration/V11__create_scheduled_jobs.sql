CREATE TABLE scheduled_jobs (
    id             VARCHAR(191) PRIMARY KEY,
    job_type       VARCHAR(50)  NOT NULL,
    target_user_id BIGINT       REFERENCES users(id) ON DELETE CASCADE,
    scheduled_at   TIMESTAMPTZ  NOT NULL,
    payload        JSONB,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_scheduled_jobs_type ON scheduled_jobs(job_type);
