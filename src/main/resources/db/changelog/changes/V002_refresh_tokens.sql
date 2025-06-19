CREATE TABLE IF NOT EXISTS refresh_tokens
(
    id          BIGSERIAL PRIMARY KEY,
    user_id     UUID         NOT NULL,
    token       TEXT         NOT NULL UNIQUE,
    expiry_date TIMESTAMP    NOT NULL,
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES game_users (id) ON DELETE CASCADE
    );

CREATE INDEX IF NOT EXISTS idx_refresh_tokens_user_id ON refresh_tokens (user_id);