package io.clientra.dto;

import java.math.BigDecimal;
import java.util.List;

/** Dashboard and Reports & Analytics payloads. */
public final class InsightDtos {

    private InsightDtos() {
    }

    public record MonthPoint(String month, BigDecimal revenue, long newCustomers) {
    }

    public record SegmentShare(String segment, long count, double percent) {
    }

    public record AtRiskCustomer(Long id, String name, String companyName, int churnScore, BigDecimal lifetimeValue,
                                 String ownerName) {
    }

    /** Cached (Redis, 60s) part of the dashboard; identical for every user. */
    public record DashboardKpis(
            long totalCustomers, Double customerGrowthPercent,
            long newCustomersThisMonth, long leadsConverted, String monthLabel,
            BigDecimal revenueMtd, Double revenueChangePercent, String previousMonthLabel,
            long openTickets, long nearSlaBreach,
            List<MonthPoint> monthly, List<SegmentShare> segments, List<AtRiskCustomer> atRisk) {
    }

    public record FunnelStage(String stage, long count, double percent) {
    }

    public record CategoryShare(String category, long count, double percent) {
    }

    public record ExecutivePerformance(Long userId, String name, long customersOwned, long interactions, long ordersClosed,
                                       BigDecimal revenue, double conversionPercent) {
    }

    public record Kpi(Double value, Double delta, String note) {
    }

    public record ReportSummary(
            String period, String periodLabel, String rangeLabel,
            Kpi retention, Kpi conversion, Kpi avgResolutionHours, Kpi slaCompliance, long slaBreaches,
            List<FunnelStage> funnel, long ticketTotal, List<CategoryShare> ticketCategories,
            List<ExecutivePerformance> executives, BigDecimal revenue, long ordersPaid) {
    }
}
