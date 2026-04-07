CREATE TABLE message_reaction (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    message_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    emoji VARCHAR(50) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_reaction_message_user_emoji UNIQUE (message_id, user_id, emoji),
    INDEX idx_reaction_message_id (message_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
