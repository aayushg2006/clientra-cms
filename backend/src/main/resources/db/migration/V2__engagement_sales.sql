-- Clientra CMS : interactions, products, orders, order_items

CREATE TABLE interactions (
    id             BIGINT PRIMARY KEY AUTO_INCREMENT,
    customer_id    BIGINT NOT NULL,
    user_id        BIGINT NOT NULL,
    type           ENUM('CALL','EMAIL','MEETING','NOTE','DEMO','QUOTE') NOT NULL,
    subject        VARCHAR(200) NOT NULL,
    notes          TEXT,
    interaction_at DATETIME NOT NULL,
    follow_up_at   DATETIME NULL,
    follow_up_done TINYINT(1) NOT NULL DEFAULT 0,
    created_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_interactions_customer FOREIGN KEY (customer_id) REFERENCES customers(id),
    CONSTRAINT fk_interactions_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE products (
    id         BIGINT PRIMARY KEY AUTO_INCREMENT,
    sku        VARCHAR(40) NOT NULL UNIQUE,
    name       VARCHAR(150) NOT NULL,
    category   VARCHAR(80),
    unit_price DECIMAL(10,2) NOT NULL,
    gst_rate   DECIMAL(5,2) NOT NULL DEFAULT 5.00,
    is_active  TINYINT(1) NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE orders (
    id             BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_number   VARCHAR(30) NULL UNIQUE,
    customer_id    BIGINT NOT NULL,
    created_by     BIGINT NOT NULL,
    order_date     DATETIME NOT NULL,
    status         ENUM('CONFIRMED','SHIPPED','DELIVERED','CANCELLED') NOT NULL DEFAULT 'CONFIRMED',
    total_amount   DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    tax_amount     DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    payment_status ENUM('PENDING','PAID','REFUNDED') NOT NULL DEFAULT 'PENDING',
    paid_at        DATETIME NULL,
    created_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_orders_customer FOREIGN KEY (customer_id) REFERENCES customers(id),
    CONSTRAINT fk_orders_user FOREIGN KEY (created_by) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE order_items (
    id         BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_id   BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    quantity   INT NOT NULL,
    unit_price DECIMAL(10,2) NOT NULL,
    line_total DECIMAL(12,2) NOT NULL,
    CONSTRAINT fk_items_order FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE,
    CONSTRAINT fk_items_product FOREIGN KEY (product_id) REFERENCES products(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
