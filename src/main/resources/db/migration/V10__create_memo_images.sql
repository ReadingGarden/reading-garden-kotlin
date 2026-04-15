CREATE TABLE memo_images (
    id         BIGSERIAL PRIMARY KEY,
    name       TEXT      NOT NULL,
    url        TEXT      NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    memo_id    BIGINT    NOT NULL REFERENCES memos(id) ON DELETE CASCADE
);

CREATE INDEX idx_memo_images_memo_id ON memo_images(memo_id);
