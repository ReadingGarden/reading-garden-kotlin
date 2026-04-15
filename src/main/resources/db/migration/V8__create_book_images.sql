CREATE TABLE book_images (
    id         BIGSERIAL PRIMARY KEY,
    name       TEXT      NOT NULL,
    url        TEXT      NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    book_id    BIGINT    NOT NULL REFERENCES books(id) ON DELETE CASCADE
);

CREATE INDEX idx_book_images_book_id ON book_images(book_id);
