CREATE TABLE book_reads (
    id           BIGSERIAL PRIMARY KEY,
    book_id      BIGINT    NOT NULL REFERENCES books(id) ON DELETE CASCADE,
    current_page INTEGER   NOT NULL DEFAULT 0,
    start_date   TIMESTAMP,
    end_date     TIMESTAMP,
    created_at   TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_book_reads_book_id ON book_reads(book_id);
