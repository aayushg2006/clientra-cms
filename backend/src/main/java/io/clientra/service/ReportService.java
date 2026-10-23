package io.clientra.service;

import io.clientra.config.AppProperties;
import io.clientra.config.CacheConfig;
import io.clientra.dto.InsightDtos.*;
import io.clientra.entity.Customer;
import io.clientra.entity.SupportTicket;
import io.clientra.entity.User;
import io.clientra.entity.enums.Role;
import io.clientra.entity.enums.TicketCategory;
import io.clientra.repository.*;
import io.clientra.util.Formats;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.*;
import java.time.format.TextStyle;
import java.util.*;
import java.util.stream.Collectors;

/** Reports & Analytics: retention, conversion, ticket performance, lifecycle funnel and executive performance. */
@Service
@RequiredArgsConstructor
public class ReportService {

    private final CustomerRepository customerRepo;
    private final OrderRepository orderRepo;
    private final SupportTicketRepository ticketRepo;
    private final InteractionRepository interactionRepo;
    private final UserRepository userRepo;
    private final TicketService ticketService;
    private final AppProperties props;
    private final Clock clock;

    /** Reporting window. Quarters follow the Indian financial year (Q1 = Apr-Jun). */
    public record Period(String key, String label, String range, Instant from, Instant to, Instant prevFrom, Instant prevTo) {
    }

    public Period period(String key) {
        ZoneId zone = props.timezone();
        LocalDate today = LocalDate.now(clock.withZone(zone));
        LocalDate qStart = today.withMonth(((today.getMonthValue() - 1) / 3) * 3 + 1).withDayOfMonth(1);
        LocalDate start;
        LocalDate end;
        String label;
        switch (key == null ? "quarter" : key) {
            case "last-quarter" -> {
                start = qStart.minusMonths(3);
                end = qStart;
                label = fyQuarter(start);
            }
            case "year" -> {
                start = today.withDayOfMonth(1).minusMonths(11);
                end = today.plusDays(1);
                label = "Last 12 months";
                key = "year";
            }
            default -> {
                start = qStart;
                end = qStart.plusMonths(3);
                label = fyQuarter(start);
                key = "quarter";
            }
        }
        LocalDate lastDay = end.minusDays(1);
        String range = shortMonth(start) + " – " + shortMonth(lastDay);
        long months = java.time.Period.between(start, end).toTotalMonths();
        if (months == 0) months = 1;
        return new Period(key, label, range, start.atStartOfDay(zone).toInstant(), end.atStartOfDay(zone).toInstant(),
                start.minusMonths(months).atStartOfDay(zone).toInstant(), start.atStartOfDay(zone).toInstant());
    }

    private static String fyQuarter(LocalDate qStart) {
        int m = qStart.getMonthValue();
        int q = m >= 4 ? (m - 4) / 3 + 1 : 4;
        int fyStart = m >= 4 ? qStart.getYear() : qStart.getYear() - 1;
        return "Q" + q + " FY " + fyStart + "-" + String.format("%02d", (fyStart + 1) % 100);
    }

    private static String shortMonth(LocalDate d) {
        return d.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
    }

    @Cacheable(cacheNames = CacheConfig.REPORTS, key = "#key == null ? 'quarter' : #key")
    @Transactional(readOnly = true)
    public ReportSummary summary(String key) {
        Period p = period(key);
        Instant now = Instant.now(clock);
        Instant to = p.to().isAfter(now) ? now : p.to();

        // Retention: customers who bought in the previous window and bought again in this one
        Set<Long> prevBuyers = new HashSet<>(orderRepo.customersWithPaidOrderBetween(p.prevFrom(), p.prevTo()));
        Set<Long> buyers = new HashSet<>(orderRepo.customersWithPaidOrderBetween(p.from(), p.to()));
        Double retention = prevBuyers.isEmpty() ? null
                : DashboardService.round1(prevBuyers.stream().filter(buyers::contains).count() * 100.0 / prevBuyers.size());
        Set<Long> prevPrevBuyers = new HashSet<>(orderRepo.customersWithPaidOrderBetween(
                p.prevFrom().minus(Duration.between(p.prevFrom(), p.prevTo())), p.prevFrom()));
        Double prevRetention = prevPrevBuyers.isEmpty() ? null
                : DashboardService.round1(prevPrevBuyers.stream().filter(prevBuyers::contains).count() * 100.0 / prevPrevBuyers.size());

        // Lead -> customer conversion across the whole book
        long totalCustomers = customerRepo.countByDeletedAtIsNull();
        long paying = orderRepo.countCustomersWithPaidOrder();
        Double conversion = totalCustomers == 0 ? null : DashboardService.round1(paying * 100.0 / totalCustomers);

        // Ticket performance in the window
        List<SupportTicket> resolved = ticketRepo.findResolvedSince(p.from()).stream()
                .filter(t -> t.getResolvedAt().isBefore(p.to())).toList();
        List<SupportTicket> prevResolved = ticketRepo.findResolvedSince(p.prevFrom()).stream()
                .filter(t -> t.getResolvedAt().isBefore(p.prevTo())).toList();
        Double avgRes = avgHours(resolved);
        Double prevAvgRes = avgHours(prevResolved);
        Double sla = compliance(resolved);
        Double prevSla = compliance(prevResolved);
        long breaches = resolved.stream().filter(t -> t.getResolvedAt().isAfter(t.getSlaDueAt())).count();

        return new ReportSummary(p.key(), p.label(), p.range(),
                new Kpi(retention, diff(retention, prevRetention), "pts"),
                new Kpi(conversion, null, paying + " of " + totalCustomers + " customers have bought"),
                new Kpi(avgRes, diff(avgRes, prevAvgRes), "h"),
                new Kpi(sla, diff(sla, prevSla), breaches + " breaches"), breaches,
                funnel(totalCustomers), categoriesTotal(p.from()), categories(p.from()),
                executives(p.from(), to), orderRepo.revenueBetween(p.from(), p.to()),
                orderRepo.customersWithPaidOrderBetween(p.from(), p.to()).size());
    }

    private List<FunnelStage> funnel(long leads) {
        Map<Long, Long> interactions = new HashMap<>();
        interactionRepo.countPerCustomer().forEach(r -> interactions.put((Long) r[0], (Long) r[1]));
        Set<Long> withOrder = new HashSet<>(orderRepo.customersWithAnyOrder());
        Map<Long, Long> paidOrders = new HashMap<>();
        orderRepo.paidStatsByCustomer().forEach(r -> paidOrders.put((Long) r[0], (Long) r[1]));

        long contacted = interactions.size();
        Set<Long> qualified = new HashSet<>(withOrder);
        interactions.forEach((id, n) -> {
            if (n >= 2) qualified.add(id);
        });
        long firstOrder = paidOrders.size();
        long repeat = paidOrders.values().stream().filter(n -> n >= 2).count();
        return List.of(stage("Leads captured", leads, leads), stage("Contacted", contacted, leads),
                stage("Qualified", qualified.size(), leads), stage("First order", firstOrder, leads),
                stage("Repeat customers", repeat, leads));
    }

    private static FunnelStage stage(String name, long n, long base) {
        return new FunnelStage(name, n, base == 0 ? 0 : Math.round(n * 100.0 / base));
    }

    private long categoriesTotal(Instant from) {
        return ticketRepo.countByCategorySince(from).stream().mapToLong(r -> (Long) r[1]).sum();
    }

    private List<CategoryShare> categories(Instant from) {
        Map<TicketCategory, Long> counts = new EnumMap<>(TicketCategory.class);
        ticketRepo.countByCategorySince(from).forEach(r -> counts.put((TicketCategory) r[0], (Long) r[1]));
        long total = counts.values().stream().mapToLong(Long::longValue).sum();
        return counts.entrySet().stream()
                .sorted(Map.Entry.<TicketCategory, Long>comparingByValue().reversed())
                .map(e -> new CategoryShare(Formats.label(e.getKey()), e.getValue(), total == 0 ? 0 : Math.round(e.getValue() * 100.0 / total)))
                .toList();
    }

    private List<ExecutivePerformance> executives(Instant from, Instant to) {
        Map<Long, Long> owned = new HashMap<>();
        customerRepo.countByOwner().forEach(r -> owned.put((Long) r[0], (Long) r[1]));
        Map<Long, Long> interactions = new HashMap<>();
        interactionRepo.countByUserSince(from).forEach(r -> interactions.put((Long) r[0], (Long) r[1]));
        Map<Long, Object[]> paid = new HashMap<>();
        orderRepo.paidStatsByUserSince(from).forEach(r -> paid.put((Long) r[0], r));
        Set<Long> payingCustomers = orderRepo.paidStatsByCustomer().stream().map(r -> (Long) r[0]).collect(Collectors.toSet());

        Map<Long, List<Long>> customersByOwner = new HashMap<>();
        for (Customer c : customerRepo.findAllActive()) {
            if (c.getOwner() != null) customersByOwner.computeIfAbsent(c.getOwner().getId(), k -> new ArrayList<>()).add(c.getId());
        }

        List<ExecutivePerformance> rows = new ArrayList<>();
        for (User u : userRepo.findByActiveTrueOrderByFullName()) {
            if (u.getRole() == Role.SUPPORT_AGENT) continue;
            long ownedCount = owned.getOrDefault(u.getId(), 0L);
            Object[] p = paid.get(u.getId());
            if (ownedCount == 0 && p == null) continue;
            List<Long> mine = customersByOwner.getOrDefault(u.getId(), List.of());
            double conversion = mine.isEmpty() ? 0 : Math.round(mine.stream().filter(payingCustomers::contains).count() * 100.0 / mine.size());
            rows.add(new ExecutivePerformance(u.getId(), u.getFullName(), ownedCount, interactions.getOrDefault(u.getId(), 0L),
                    p == null ? 0 : (Long) p[1], p == null ? BigDecimal.ZERO : (BigDecimal) p[2], conversion));
        }
        rows.sort(Comparator.comparing(ExecutivePerformance::revenue).reversed());
        return rows;
    }

    private static Double avgHours(List<SupportTicket> tickets) {
        if (tickets.isEmpty()) return null;
        return DashboardService.round1(tickets.stream()
                .mapToLong(t -> Duration.between(t.getCreatedAt(), t.getResolvedAt()).toMinutes()).average().orElse(0) / 60.0);
    }

    private static Double compliance(List<SupportTicket> tickets) {
        if (tickets.isEmpty()) return null;
        long ok = tickets.stream().filter(t -> !t.getResolvedAt().isAfter(t.getSlaDueAt())).count();
        return DashboardService.round1(ok * 100.0 / tickets.size());
    }

    private static Double diff(Double now, Double before) {
        return now == null || before == null ? null : DashboardService.round1(now - before);
    }

    public Double overallSlaCompliance() {
        return ticketService.slaCompliance(Instant.now(clock).minus(Duration.ofDays(30)));
    }
}
