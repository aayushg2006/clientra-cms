package io.clientra.service;

import io.clientra.config.AppProperties;
import io.clientra.dto.CustomerDtos.ChurnRiskDto;
import io.clientra.entity.Customer;
import io.clientra.entity.enums.CustomerStatus;
import io.clientra.entity.enums.NotificationType;
import io.clientra.entity.enums.TicketStatus;
import io.clientra.repository.CustomerRepository;
import io.clientra.repository.OrderRepository;
import io.clientra.repository.SupportTicketRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Churn-risk score (0-100) from RFM features plus support friction:
 * Recency of the last paid order, Frequency of paid orders, Monetary lifetime value, and open tickets.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChurnRiskService {

    public static final int HIGH = 60;
    public static final int MEDIUM = 35;
    private static final List<TicketStatus> OPEN = List.of(TicketStatus.OPEN, TicketStatus.IN_PROGRESS, TicketStatus.AWAITING_CUSTOMER);

    private final CustomerRepository customerRepo;
    private final OrderRepository orderRepo;
    private final SupportTicketRepository ticketRepo;
    private final NotificationService notifications;
    private final AppProperties props;
    private final Clock clock;

    /** Raw inputs for one customer, so the score can be computed for a single profile or in bulk. */
    public record Features(CustomerStatus status, Instant lastPaidOrder, long paidOrders, BigDecimal lifetimeValue,
                           long openTickets, Instant customerSince) {
    }

    public ChurnRiskDto score(Features f) {
        List<String> reasons = new ArrayList<>();
        Instant now = Instant.now(clock);
        Long recencyDays = f.lastPaidOrder() == null ? null : Duration.between(f.lastPaidOrder(), now).toDays();

        if (f.status() == CustomerStatus.CHURNED) {
            return new ChurnRiskDto(100, "HIGH", recencyDays, f.paidOrders(), f.lifetimeValue(), f.openTickets(),
                    List.of("Customer is marked as churned"));
        }
        if (f.status() == CustomerStatus.LEAD && f.paidOrders() == 0) {
            return new ChurnRiskDto(0, "LOW", null, 0, BigDecimal.ZERO, f.openTickets(), List.of("Lead - not yet a paying customer"));
        }

        int score = 0;
        if (recencyDays == null) {
            score += 45;
            reasons.add("No paid orders yet");
        } else if (recencyDays > 180) {
            score += 45;
            reasons.add("No purchase in " + recencyDays + " days");
        } else if (recencyDays > 90) {
            score += 30;
            reasons.add("Last purchase " + recencyDays + " days ago");
        } else if (recencyDays > 30) {
            score += 15;
        }

        if (f.paidOrders() == 0) score += 25;
        else if (f.paidOrders() < 3) {
            score += 15;
            reasons.add("Only " + f.paidOrders() + " paid order" + (f.paidOrders() == 1 ? "" : "s"));
        } else if (f.paidOrders() < 6) score += 8;

        BigDecimal ltv = f.lifetimeValue() == null ? BigDecimal.ZERO : f.lifetimeValue();
        if (ltv.signum() == 0) score += 15;
        else if (ltv.compareTo(BigDecimal.valueOf(50_000)) < 0) score += 10;
        else if (ltv.compareTo(BigDecimal.valueOf(200_000)) < 0) score += 5;

        if (f.openTickets() >= 2) {
            score += 10;
            reasons.add(f.openTickets() + " open support tickets");
        } else if (f.openTickets() == 1) {
            score += 5;
            reasons.add("1 open support ticket");
        }

        if (f.status() == CustomerStatus.INACTIVE) {
            score += 10;
            reasons.add("Marked inactive");
        }

        score = Math.max(0, Math.min(100, score));
        String level = score >= HIGH ? "HIGH" : score >= MEDIUM ? "MEDIUM" : "LOW";
        if (reasons.isEmpty()) reasons.add("Buying regularly");
        return new ChurnRiskDto(score, level, recencyDays, f.paidOrders(), ltv, f.openTickets(), reasons);
    }

    @Transactional(readOnly = true)
    public ChurnRiskDto scoreFor(Customer c) {
        Object[] stats = orderRepo.paidStatsForCustomer(c.getId()).get(0);
        long open = ticketRepo.countByCustomerIdAndStatusIn(c.getId(), OPEN);
        return score(new Features(c.getStatus(), (Instant) stats[1], (Long) stats[0], c.getLifetimeValue(), open,
                c.getCreatedAt()));
    }

    /** Recomputes every customer's score (nightly at 02:00 business time and on demand from the dashboard). */
    @Scheduled(cron = "0 0 2 * * *", zone = "${app.timezone}")
    @Transactional
    public int recalculateAll() {
        Map<Long, Object[]> paid = new HashMap<>();
        orderRepo.paidStatsByCustomer().forEach(r -> paid.put((Long) r[0], r));
        Map<Long, Long> open = new HashMap<>();
        ticketRepo.openCountByCustomer().forEach(r -> open.put((Long) r[0], (Long) r[1]));

        int flagged = 0;
        for (Customer c : customerRepo.findAllActive()) {
            Object[] p = paid.get(c.getId());
            ChurnRiskDto risk = score(new Features(c.getStatus(), p == null ? null : (Instant) p[2], p == null ? 0 : (Long) p[1],
                    c.getLifetimeValue(), open.getOrDefault(c.getId(), 0L), c.getCreatedAt()));
            Integer previous = c.getChurnScore();
            c.setChurnScore(risk.score());
            boolean newlyHigh = risk.score() >= HIGH && (previous == null || previous < HIGH);
            if (newlyHigh && c.getStatus() == CustomerStatus.ACTIVE && c.getOwner() != null) {
                flagged++;
                notifications.notify(c.getOwner(), NotificationType.CHURN_RISK, "At-risk customer: " + c.getFullName(),
                        "Churn risk " + risk.score() + "/100 - " + String.join(", ", risk.reasons()),
                        "/customers/" + c.getId(), false);
            }
        }
        log.info("Churn scores recalculated, {} customers newly flagged high-risk", flagged);
        return flagged;
    }

    public String zoneId() {
        return props.timezone().getId();
    }
}
