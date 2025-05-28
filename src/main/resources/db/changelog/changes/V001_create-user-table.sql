CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE IF NOT EXISTS game_users
(
    id                 UUID PRIMARY KEY    DEFAULT uuid_generate_v4(),
    username           VARCHAR(255) NOT NULL UNIQUE,
    password           VARCHAR(255) NOT NULL,
    email              VARCHAR(255) NOT NULL UNIQUE,
    profile_avatar_url VARCHAR(1024),
    enabled            BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS user_roles
(
    user_id UUID         NOT NULL,
    role    VARCHAR(255) NOT NULL,
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES game_users (id),
    PRIMARY KEY (user_id, role)
);


CREATE INDEX IF NOT EXISTS idx_user_roles_user_id ON user_roles (user_id);
