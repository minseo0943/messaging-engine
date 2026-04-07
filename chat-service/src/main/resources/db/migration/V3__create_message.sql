CREATE TABLE message (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    chat_room_id BIGINT       NOT NULL,
    sender_id    BIGINT       NOT NULL,
    sender_name  VARCHAR(50)  NOT NULL,
    content      TEXT         NOT NULL,
    type         VARCHAR(20)  NOT NULL DEFAULT 'TEXT',
    created_at   DATETIME(6)  NOT NULL,

    CONSTRAINT fk_message_chat_room FOREIGN KEY (chat_room_id) REFERENCES chat_room (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_message_chat_room_id ON message (chat_room_id);
CREATE INDEX idx_message_sender_id ON message (sender_id);
