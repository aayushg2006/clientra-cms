-- In-app notifications and password reset OTPs

CREATE TABLE notifications (
    id         BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id    BIGINT NOT NULL,
    type       ENUM('FOLLOW_UP','SLA_BREACH','TICKET_ASSIGNED','TICKET_UPDATE','ORDER_PAID','CHURN_RISK','SYSTEM') NOT NULL,
    title      VARCHAR(150) NOT NULL,
    message    VARCHAR(500),
    link       VARCHAR(200),
    is_read    TINYINT(1) NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_notifications_user ON notifications (user_id, is_read, created_at);

CREATE TABLE password_reset_tokens (
    id         BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id    BIGINT NOT NULL,
    otp_hash   VARCHAR(255) NOT NULL,
    expires_at DATETIME NOT NULL,
    used       TINYINT(1) NOT NULL DEFAULT 0,
    attempts   INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_reset_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
