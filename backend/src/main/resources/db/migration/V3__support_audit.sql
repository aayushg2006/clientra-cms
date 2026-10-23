-- Clientra CMS : support tickets and audit trail

CREATE TABLE support_tickets (
    id                BIGINT PRIMARY KEY AUTO_INCREMENT,
    ticket_number     VARCHAR(30) NULL UNIQUE,
    customer_id       BIGINT NOT NULL,
    order_id          BIGINT NULL,
    assigned_to       BIGINT NULL,
    created_by        BIGINT NULL,
    subject           VARCHAR(200) NOT NULL,
    description       TEXT,
    channel           ENUM('EMAIL','PHONE','CHAT','WEB','WALK_IN') NOT NULL DEFAULT 'EMAIL',
    category          ENUM('DELIVERY','PRODUCT_QUALITY','BILLING','ACCOUNT','OTHER') NOT NULL DEFAULT 'OTHER',
    priority          ENUM('LOW','MEDIUM','HIGH') NOT NULL DEFAULT 'MEDIUM',
    status            ENUM('OPEN','IN_PROGRESS','AWAITING_CUSTOMER','RESOLVED') NOT NULL DEFAULT 'OPEN',
    sla_due_at        DATETIME NOT NULL,
    sla_breached      TINYINT(1) NOT NULL DEFAULT 0,
    escalated         TINYINT(1) NOT NULL DEFAULT 0,
    first_response_at DATETIME NULL,
    resolved_at       DATETIME NULL,
    created_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_tickets_customer FOREIGN KEY (customer_id) REFERENCES customers(id),
    CONSTRAINT fk_tickets_order FOREIGN KEY (order_id) REFERENCES orders(id),
    CONSTRAINT fk_tickets_assignee FOREIGN KEY (assigned_to) REFERENCES users(id),
    CONSTRAINT fk_tickets_creator FOREIGN KEY (created_by) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE activity_logs (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id     BIGINT NULL,
    entity_type VARCHAR(30) NOT NULL,
    entity_id   BIGINT NOT NULL,
    customer_id BIGINT NULL,
    action      VARCHAR(50) NOT NULL,
    summary     VARCHAR(255),
    details     JSON NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_logs_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
