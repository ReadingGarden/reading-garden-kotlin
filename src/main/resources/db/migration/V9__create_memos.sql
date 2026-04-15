CREATE TABLE memos (
    id         BIGSERIAL PRIMARY KEY,
    book_id    BIGINT    NOT NULL REFERENCES books(id) ON DELETE CASCADE,
    content    TEXT      NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    user_id    BIGINT    NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    is_liked   BOOLEAN   NOT NULL DEFAULT false,
    quote      TEXT,
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_memos_book_id ON memos(book_id);
CREATE INDEX idx_memos_user_id ON memos(user_id);
