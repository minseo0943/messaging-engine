CREATE TABLE chat_room (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(100)  NOT NULL,
    description VARCHAR(500),
    creator_id  BIGINT        NOT NULL,
    created_at  DATETIME(6)   NOT NULL,
    updated_at  DATETIME(6)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
