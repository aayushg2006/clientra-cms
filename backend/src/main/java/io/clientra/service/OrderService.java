package io.clientra.service;

import io.clientra.config.AppProperties;
import io.clientra.config.CacheConfig;
import io.clientra.dto.OrderDtos.*;
import io.clientra.entity.*;
import io.clientra.entity.enums.*;
import io.clientra.exception.BusinessRuleException;
import io.clientra.exception.ResourceNotFoundException;
import io.clientra.repository.OrderRepository;
import io.clientra.repository.ProductRepository;
import io.clientra.repository.UserRepository;
import io.clientra.security.CurrentUser;
import io.clientra.util.Formats;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepo;
    private final ProductRepository productRepo;
    private final UserRepository userRepo;
    private final CustomerService customerService;
    private final ChurnRiskService churn;
    private final NotificationService notifications;
    private final AuditService audit;
    private final AppProperties props;
    private final Clock clock;

    @Transactional(readOnly = true)
    public Page<OrderSummary> list(String q, PaymentStatus payment, OrderStatus status, Long customerId, Pageable pageable) {
        Specification<Order> spec = (root, query, cb) -> cb.conjunction();
        if (payment != null) spec = spec.and((r, qq, cb) -> cb.equal(r.get("paymentStatus"), payment));
        if (status != null) spec = spec.and((r, qq, cb) -> cb.equal(r.get("status"), status));
        if (customerId != null) spec = spec.and((r, qq, cb) -> cb.equal(r.get("customer").get("id"), customerId));
        if (q != null && !q.isBlank()) {
            String like = "%" + q.trim().toLowerCase() + "%";
            spec = spec.and((r, qq, cb) -> cb.or(cb.like(cb.lower(r.get("orderNumber")), like),
                    cb.like(cb.lower(cb.concat(cb.concat(r.get("customer").get("firstName"), " "),
                            cb.coalesce(r.get("customer").get("lastName"), ""))), like)));
        }
        return orderRepo.findAll(spec, pageable).map(OrderSummary::from);
    }

    /** KPI tiles on the Orders page; quarters follow the calendar in the business time zone. */
    @Transactional(readOnly = true)
    public OrderKpis kpis() {
        ZoneId zone = props.timezone();
        LocalDate today = LocalDate.now(clock.withZone(zone));
        LocalDate qStart = today.withMonth(((today.getMonthValue() - 1) / 3) * 3 + 1).withDayOfMonth(1);
        Instant from = qStart.atStartOfDay(zone).toInstant();
        Instant to = qStart.plusMonths(3).atStartOfDay(zone).toInstant();
        Instant ever = Instant.EPOCH;
        BigDecimal paid = orderRepo.sumByPaymentStatusBetween(PaymentStatus.PAID, from, to);
        BigDecimal pending = orderRepo.sumByPaymentStatusBetween(PaymentStatus.PENDING, ever, to.plus(Duration.ofDays(3650)));
        BigDecimal aov = orderRepo.averageOrderValueBetween(from, to).setScale(0, RoundingMode.HALF_UP);
        return new OrderKpis(paid, pending, aov, orderRepo.countBetween(from, to), orderRepo.revenueBetween(from, to));
    }

    @Transactional(readOnly = true)
    public OrderDetail get(Long id) {
        return OrderDetail.from(orderRepo.findDetailById(id).orElseThrow(() -> new ResourceNotFoundException("Order", id)));
    }

    @Transactional
    @CacheEvict(cacheNames = {CacheConfig.DASHBOARD, CacheConfig.REPORTS}, allEntries = true)
    public OrderDetail create(CreateOrderRequest req) {
        Customer customer = customerService.find(req.customerId());
        // merge repeated lines for the same product
        Map<Long, Integer> quantities = new LinkedHashMap<>();
        req.items().forEach(l -> quantities.merge(l.productId(), l.quantity(), Integer::sum));

        Order order = new Order();
        order.setCustomer(customer);
        order.setCreatedBy(userRepo.getReferenceById(CurrentUser.require().id()));
        order.setOrderDate(Instant.now(clock));
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal tax = BigDecimal.ZERO;
        for (var e : quantities.entrySet()) {
            Product p = productRepo.findById(e.getKey()).orElseThrow(() -> new ResourceNotFoundException("Product", e.getKey()));
            if (!p.isActive()) throw new BusinessRuleException("INACTIVE_PRODUCT", p.getName() + " is no longer available");
            OrderItem item = new OrderItem();
            item.setProduct(p);
            item.setQuantity(e.getValue());
            item.setUnitPrice(p.getUnitPrice());
            item.setLineTotal(p.getUnitPrice().multiply(BigDecimal.valueOf(e.getValue())));
            order.addItem(item);
            total = total.add(item.getLineTotal());
            tax = tax.add(includedGst(item.getLineTotal(), p.getGstRate()));
        }
        order.setTotalAmount(total);
        order.setTaxAmount(tax);
        order = orderRepo.save(order);
        order.setOrderNumber(String.format("ORD-%d-%04d", LocalDate.now(clock.withZone(props.timezone())).getYear(), order.getId()));
        audit.log("ORDER", order.getId(), customer.getId(), "CREATED",
                "Order " + order.getOrderNumber() + " placed — " + Formats.inr(total),
                Map.of("items", quantities.size(), "total", total));
        if (req.paymentStatus() == PaymentStatus.PAID) {
            applyPaid(order);
        }
        return OrderDetail.from(order);
    }

    /**
     * Payment status change. Marking paid is idempotent: paying twice never double-counts lifetime value.
     * PAID adds to LTV and converts a LEAD into an ACTIVE customer; REFUNDED reverses the LTV.
     */
    @Transactional
    @CacheEvict(cacheNames = {CacheConfig.DASHBOARD, CacheConfig.REPORTS}, allEntries = true)
    public OrderDetail changePayment(Long id, PaymentStatus target) {
        Order order = orderRepo.findDetailById(id).orElseThrow(() -> new ResourceNotFoundException("Order", id));
        PaymentStatus current = order.getPaymentStatus();
        if (current == target) return OrderDetail.from(order);
        switch (target) {
            case PAID -> {
                if (current == PaymentStatus.REFUNDED) throw new BusinessRuleException("A refunded order cannot be paid again");
                if (order.getStatus() == OrderStatus.CANCELLED) throw new BusinessRuleException("A cancelled order cannot be paid");
                applyPaid(order);
            }
            case REFUNDED -> {
                if (current != PaymentStatus.PAID) throw new BusinessRuleException("Only paid orders can be refunded");
                Customer c = order.getCustomer();
                c.setLifetimeValue(c.getLifetimeValue().subtract(order.getTotalAmount()).max(BigDecimal.ZERO));
                order.setPaymentStatus(PaymentStatus.REFUNDED);
                order.setStatus(OrderStatus.CANCELLED);
                audit.log("ORDER", id, c.getId(), "REFUNDED", "Order " + order.getOrderNumber() + " refunded — "
                        + Formats.inr(order.getTotalAmount()), Map.of("amount", order.getTotalAmount()));
            }
            case PENDING -> throw new BusinessRuleException("Payment cannot be moved back to pending");
        }
        return OrderDetail.from(order);
    }

    private void applyPaid(Order order) {
        Customer c = order.getCustomer();
        order.setPaymentStatus(PaymentStatus.PAID);
        order.setPaidAt(Instant.now(clock));
        c.setLifetimeValue(c.getLifetimeValue().add(order.getTotalAmount()));
        boolean converted = false;
        if (c.getStatus() == CustomerStatus.LEAD || c.getStatus() == CustomerStatus.INACTIVE) {
            converted = true;
            CustomerStatus old = c.getStatus();
            c.setStatus(CustomerStatus.ACTIVE);
            audit.log("CUSTOMER", c.getId(), c.getId(), "STATUS_CHANGED",
                    "Status changed from " + Formats.label(old) + " to Active (order paid)", Map.of("from", old, "to", "ACTIVE"));
        }
        audit.log("ORDER", order.getId(), c.getId(), "PAID", "Order " + order.getOrderNumber() + " marked paid — "
                + Formats.inr(order.getTotalAmount()), Map.of("amount", order.getTotalAmount()));
        c.setChurnScore(churn.scoreFor(c).score());
        if (c.getOwner() != null && !c.getOwner().getId().equals(CurrentUser.id())) {
            notifications.notify(c.getOwner(), NotificationType.ORDER_PAID, "Payment received from " + c.getFullName(),
                    "Order " + order.getOrderNumber() + " — " + Formats.inr(order.getTotalAmount())
                            + (converted ? ". Customer is now Active." : ""), "/customers/" + c.getId(), false);
        }
    }

    @Transactional
    @CacheEvict(cacheNames = {CacheConfig.DASHBOARD, CacheConfig.REPORTS}, allEntries = true)
    public OrderDetail changeStatus(Long id, OrderStatus target) {
        Order order = orderRepo.findDetailById(id).orElseThrow(() -> new ResourceNotFoundException("Order", id));
        OrderStatus current = order.getStatus();
        if (current == target) return OrderDetail.from(order);
        boolean allowed = switch (current) {
            case CONFIRMED -> target == OrderStatus.SHIPPED || target == OrderStatus.CANCELLED;
            case SHIPPED -> target == OrderStatus.DELIVERED || target == OrderStatus.CANCELLED;
            case DELIVERED, CANCELLED -> false;
        };
        if (!allowed) {
            throw new BusinessRuleException("Cannot move an order from " + Formats.label(current) + " to " + Formats.label(target));
        }
        if (target == OrderStatus.CANCELLED && order.getPaymentStatus() == PaymentStatus.PAID) {
            throw new BusinessRuleException("Refund the payment before cancelling a paid order");
        }
        order.setStatus(target);
        audit.log("ORDER", id, order.getCustomer().getId(), "STATUS_CHANGED",
                "Order " + order.getOrderNumber() + " → " + Formats.label(target), Map.of("from", current, "to", target));
        return OrderDetail.from(order);
    }

    static BigDecimal includedGst(BigDecimal gross, BigDecimal ratePercent) {
        BigDecimal divisor = BigDecimal.ONE.add(ratePercent.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP));
        return gross.subtract(gross.divide(divisor, 2, RoundingMode.HALF_UP));
    }
}
