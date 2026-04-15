CREATE TABLE refresh_tokens (
    id      BIGSERIAL PRIMARY KEY,
    user_id BIGINT    NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token   TEXT,
    exp     TIMESTAMP
);

CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens(user_id);
