-- Indexes for the common list filters and dashboard aggregates

CREATE INDEX idx_customers_status_segment ON customers (status, segment);
CREATE INDEX idx_customers_phone ON customers (phone);
CREATE INDEX idx_customers_created ON customers (created_at);
CREATE INDEX idx_interactions_customer ON interactions (customer_id, interaction_at);
CREATE INDEX idx_interactions_follow_up ON interactions (follow_up_at, follow_up_done);
CREATE INDEX idx_orders_customer_payment ON orders (customer_id, payment_status);
CREATE INDEX idx_orders_date ON orders (order_date);
CREATE INDEX idx_tickets_status_sla ON support_tickets (status, sla_due_at);
CREATE INDEX idx_logs_entity ON activity_logs (entity_type, entity_id);
CREATE INDEX idx_logs_customer ON activity_logs (customer_id, created_at);

-- Human-friendly number ranges (ORD-2026-0918, TKT-1187) as in the prototype
ALTER TABLE orders AUTO_INCREMENT = 800;
ALTER TABLE support_tickets AUTO_INCREMENT = 1170;
