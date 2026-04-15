CREATE TABLE push_settings (
    id        BIGSERIAL PRIMARY KEY,
    user_id   BIGINT    NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    app_ok    BOOLEAN   NOT NULL DEFAULT true,
    book_ok   BOOLEAN   NOT NULL DEFAULT false,
    push_time TIMESTAMP,

    CONSTRAINT uq_push_settings_user_id UNIQUE (user_id)
);
