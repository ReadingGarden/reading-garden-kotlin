CREATE TABLE books (
    id         BIGSERIAL    PRIMARY KEY,
    garden_id  BIGINT       REFERENCES gardens(id) ON DELETE SET NULL,
    title      VARCHAR(300) NOT NULL,
    author     VARCHAR(100) NOT NULL,
    publisher  VARCHAR(100) NOT NULL,
    status     INTEGER      NOT NULL DEFAULT 0,
    user_id    BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    page       INTEGER      NOT NULL DEFAULT 0,
    isbn       VARCHAR(30),
    tree       VARCHAR(30),
    image_url  TEXT,
    info       TEXT         NOT NULL DEFAULT '',
    created_at TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX idx_books_user_id   ON books(user_id);
CREATE INDEX idx_books_garden_id ON books(garden_id);
CREATE INDEX idx_books_status    ON books(status);

CREATE UNIQUE INDEX uq_books_isbn_user
    ON books (isbn, user_id)
    WHERE isbn IS NOT NULL;
