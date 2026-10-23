package io.clientra.service;

import io.clientra.config.AppProperties;
import io.clientra.config.CacheConfig;
import io.clientra.dto.InsightDtos.*;
import io.clientra.dto.InteractionDtos.InteractionDto;
import io.clientra.entity.enums.Role;
import io.clientra.entity.enums.Segment;
import io.clientra.entity.enums.TicketStatus;
import io.clientra.repository.*;
import io.clientra.security.AppUserPrincipal;
import io.clientra.security.CurrentUser;
import io.clientra.util.Formats;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.*;
import java.time.format.TextStyle;
import java.util.*;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final CustomerRepository customerRepo;
    private final OrderRepository orderRepo;
    private final SupportTicketRepository ticketRepo;
    private final InteractionRepository interactionRepo;
    private final ActivityLogRepository logRepo;
    private final AppProperties props;
    private final Clock clock;

    /** Aggregate KPIs; cached in Redis for 60 seconds and evicted whenever orders, customers or tickets change. */
    @Cacheable(cacheNames = CacheConfig.DASHBOARD, key = "'kpis'")
    @Transactional(readOnly = true)
    public DashboardKpis kpis() {
        ZoneId zone = props.timezone();
        Instant now = Instant.now(clock);
        LocalDate today = LocalDate.now(clock.withZone(zone));
        LocalDate monthStart = today.withDayOfMonth(1);
        Instant thisMonth = monthStart.atStartOfDay(zone).toInstant();
        Instant lastMonth = monthStart.minusMonths(1).atStartOfDay(zone).toInstant();
        // same point in the previous month, for a fair month-to-date comparison
        Instant lastMonthSameDay = monthStart.minusMonths(1).atStartOfDay(zone)
                .plus(Duration.between(thisMonth, now)).toInstant();

        long total = customerRepo.countByDeletedAtIsNull();
        long atMonthStart = customerRepo.countByDeletedAtIsNullAndCreatedAtBefore(thisMonth);
        Double growth = atMonthStart == 0 ? null : round1((total - atMonthStart) * 100.0 / atMonthStart);
        long newThisMonth = customerRepo.countByDeletedAtIsNullAndCreatedAtBetween(thisMonth, now.plusSeconds(1));
        long converted = logRepo.countLeadConversions(thisMonth, now.plusSeconds(1));

        BigDecimal revenueMtd = orderRepo.revenueBetween(thisMonth, now.plusSeconds(1));
        BigDecimal revenuePrev = orderRepo.revenueBetween(lastMonth, lastMonthSameDay);
        Double revenueChange = revenuePrev.signum() == 0 ? null
                : round1(revenueMtd.subtract(revenuePrev).doubleValue() * 100 / revenuePrev.doubleValue());

        List<TicketStatus> active = List.of(TicketStatus.OPEN, TicketStatus.IN_PROGRESS);
        long openTickets = ticketRepo.countByStatusIn(TicketService.UNRESOLVED);
        long nearBreach = ticketRepo.countNearBreach(active, now, now.plus(Duration.ofHours(4)));

        return new DashboardKpis(total, growth, newThisMonth, converted, month(monthStart), revenueMtd, revenueChange,
                month(monthStart.minusMonths(1)), openTickets, nearBreach, monthly(monthStart, zone), segments(), atRisk());
    }

    private List<MonthPoint> monthly(LocalDate monthStart, ZoneId zone) {
        LocalDate first = monthStart.minusMonths(5);
        Instant from = first.atStartOfDay(zone).toInstant();
        Map<YearMonth, BigDecimal> revenue = new TreeMap<>();
        Map<YearMonth, Long> customers = new TreeMap<>();
        for (int i = 0; i < 6; i++) {
            YearMonth ym = YearMonth.from(first.plusMonths(i));
            revenue.put(ym, BigDecimal.ZERO);
            customers.put(ym, 0L);
        }
        for (Object[] row : orderRepo.findPaidAmountsSince(from)) {
            YearMonth ym = YearMonth.from(((Instant) row[0]).atZone(zone));
            revenue.computeIfPresent(ym, (k, v) -> v.add((BigDecimal) row[1]));
        }
        for (Instant created : customerRepo.findCreatedSince(from)) {
            customers.computeIfPresent(YearMonth.from(created.atZone(zone)), (k, v) -> v + 1);
        }
        return revenue.keySet().stream()
                .map(ym -> new MonthPoint(ym.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH), revenue.get(ym), customers.get(ym)))
                .toList();
    }

    private List<SegmentShare> segments() {
        Map<Segment, Long> counts = new EnumMap<>(Segment.class);
        for (Segment s : Segment.values()) counts.put(s, 0L);
        customerRepo.countBySegment().forEach(r -> counts.put((Segment) r[0], (Long) r[1]));
        long total = counts.values().stream().mapToLong(Long::longValue).sum();
        return counts.entrySet().stream()
                .map(e -> new SegmentShare(Formats.label(e.getKey()), e.getValue(), total == 0 ? 0 : round1(e.getValue() * 100.0 / total)))
                .toList();
    }

    private List<AtRiskCustomer> atRisk() {
        return customerRepo.findAtRisk(PageRequest.of(0, 5)).stream()
                .filter(c -> c.getChurnScore() >= ChurnRiskService.MEDIUM)
                .map(c -> new AtRiskCustomer(c.getId(), c.getFullName(), c.getCompanyName(), c.getChurnScore(),
                        c.getLifetimeValue(), c.getOwner() != null ? c.getOwner().getFullName() : null))
                .toList();
    }

    /** Today's pending follow-ups for the signed-in user (admins see the whole team). Not cached. */
    @Transactional(readOnly = true)
    public List<InteractionDto> followUpsToday() {
        AppUserPrincipal me = CurrentUser.require();
        ZoneId zone = props.timezone();
        LocalDate today = LocalDate.now(clock.withZone(zone));
        return interactionRepo.findFollowUpsBetween(today.atStartOfDay(zone).toInstant(),
                        today.plusDays(1).atStartOfDay(zone).toInstant(), me.role() == Role.ADMIN ? null : me.id())
                .stream().map(InteractionDto::from).toList();
    }

    private static String month(LocalDate d) {
        return d.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
    }

    static Double round1(double v) {
        return Math.round(v * 10) / 10.0;
    }
}
