CREATE TABLE gardens (
    id         BIGSERIAL    PRIMARY KEY,
    title      VARCHAR(30)  NOT NULL,
    info       VARCHAR(200) NOT NULL DEFAULT '',
    color      VARCHAR(20)  NOT NULL DEFAULT 'red',
    created_at TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at TIMESTAMP    NOT NULL DEFAULT now()
);
