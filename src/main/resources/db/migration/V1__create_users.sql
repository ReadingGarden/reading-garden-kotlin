CREATE TABLE users (
    id         BIGSERIAL    PRIMARY KEY,
    email      VARCHAR(300) NOT NULL,
    password   TEXT         NOT NULL,
    created_at TIMESTAMP    NOT NULL DEFAULT now(),
    nick       VARCHAR(30)  NOT NULL,
    image      VARCHAR(30)  NOT NULL DEFAULT '',
    fcm        TEXT         NOT NULL DEFAULT '',
    social_id  VARCHAR(100) NOT NULL DEFAULT '',
    social_type VARCHAR(30) NOT NULL DEFAULT '',
    auth_number VARCHAR(10),
    updated_at TIMESTAMP    NOT NULL DEFAULT now(),

    CONSTRAINT uq_users_email UNIQUE (email)
);

CREATE UNIQUE INDEX uq_users_social
    ON users (social_id, social_type)
    WHERE social_type <> '';
