CREATE TABLE garden_members (
    id        BIGSERIAL PRIMARY KEY,
    garden_id BIGINT    NOT NULL REFERENCES gardens(id) ON DELETE CASCADE,
    user_id   BIGINT    NOT NULL REFERENCES users(id)   ON DELETE CASCADE,
    is_leader BOOLEAN   NOT NULL DEFAULT false,
    join_date TIMESTAMP NOT NULL DEFAULT now(),
    is_main   BOOLEAN   NOT NULL DEFAULT false,

    CONSTRAINT uq_garden_members_garden_user UNIQUE (garden_id, user_id)
);

CREATE INDEX idx_garden_members_user_id   ON garden_members(user_id);
CREATE INDEX idx_garden_members_garden_id ON garden_members(garden_id);
