-- Conversation and internal notes on support tickets

CREATE TABLE ticket_comments (
    id            BIGINT PRIMARY KEY AUTO_INCREMENT,
    ticket_id     BIGINT NOT NULL,
    author_id     BIGINT NULL,
    from_customer TINYINT(1) NOT NULL DEFAULT 0,
    internal      TINYINT(1) NOT NULL DEFAULT 0,
    body          TEXT NOT NULL,
    created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_comments_ticket FOREIGN KEY (ticket_id) REFERENCES support_tickets(id) ON DELETE CASCADE,
    CONSTRAINT fk_comments_author FOREIGN KEY (author_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_comments_ticket ON ticket_comments (ticket_id, created_at);
