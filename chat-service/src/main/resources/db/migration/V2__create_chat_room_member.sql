CREATE TABLE chat_room_member (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    chat_room_id BIGINT      NOT NULL,
    user_id      BIGINT      NOT NULL,
    nickname     VARCHAR(50) NOT NULL,
    joined_at    DATETIME(6) NOT NULL,

    CONSTRAINT fk_member_chat_room FOREIGN KEY (chat_room_id) REFERENCES chat_room (id) ON DELETE CASCADE,
    CONSTRAINT uk_chat_room_user UNIQUE (chat_room_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_member_user_id ON chat_room_member (user_id);
