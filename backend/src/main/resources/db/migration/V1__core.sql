-- Clientra CMS : core tables (users, customers, addresses, tags, customer_tags)

CREATE TABLE users (
    id            BIGINT PRIMARY KEY AUTO_INCREMENT,
    full_name     VARCHAR(100) NOT NULL,
    email         VARCHAR(150) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role          ENUM('ADMIN','SALES_EXEC','SUPPORT_AGENT') NOT NULL,
    title         VARCHAR(80),
    phone         VARCHAR(15),
    is_active     TINYINT(1) NOT NULL DEFAULT 1,
    last_login_at DATETIME NULL,
    created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE customers (
    id             BIGINT PRIMARY KEY AUTO_INCREMENT,
    customer_code  VARCHAR(20) NULL UNIQUE,
    first_name     VARCHAR(60) NOT NULL,
    last_name      VARCHAR(60),
    email          VARCHAR(150) NOT NULL UNIQUE,
    phone          VARCHAR(15),
    company_name   VARCHAR(150),
    job_title      VARCHAR(100),
    customer_type  ENUM('INDIVIDUAL','BUSINESS') NOT NULL DEFAULT 'INDIVIDUAL',
    segment        ENUM('RETAIL','SMB','ENTERPRISE','OTHER') NOT NULL DEFAULT 'RETAIL',
    status         ENUM('LEAD','ACTIVE','INACTIVE','CHURNED') NOT NULL DEFAULT 'LEAD',
    source         ENUM('WEBSITE','REFERRAL','WALK_IN','CAMPAIGN','OTHER'),
    assigned_to    BIGINT NULL,
    lifetime_value DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    churn_score    INT NULL,
    notes          TEXT,
    version        BIGINT NOT NULL DEFAULT 0,
    deleted_at     DATETIME NULL,
    created_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_customers_owner FOREIGN KEY (assigned_to) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE addresses (
    id           BIGINT PRIMARY KEY AUTO_INCREMENT,
    customer_id  BIGINT NOT NULL,
    address_type ENUM('BILLING','SHIPPING') NOT NULL DEFAULT 'BILLING',
    line1        VARCHAR(200) NOT NULL,
    line2        VARCHAR(200),
    city         VARCHAR(100),
    state        VARCHAR(100),
    postal_code  VARCHAR(20),
    country      VARCHAR(60) DEFAULT 'India',
    is_primary   TINYINT(1) NOT NULL DEFAULT 0,
    CONSTRAINT fk_addresses_customer FOREIGN KEY (customer_id) REFERENCES customers(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE tags (
    id    BIGINT PRIMARY KEY AUTO_INCREMENT,
    name  VARCHAR(50) NOT NULL UNIQUE,
    color VARCHAR(7) NOT NULL DEFAULT '#14776B'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE customer_tags (
    customer_id BIGINT NOT NULL,
    tag_id      BIGINT NOT NULL,
    PRIMARY KEY (customer_id, tag_id),
    CONSTRAINT fk_ct_customer FOREIGN KEY (customer_id) REFERENCES customers(id) ON DELETE CASCADE,
    CONSTRAINT fk_ct_tag FOREIGN KEY (tag_id) REFERENCES tags(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
