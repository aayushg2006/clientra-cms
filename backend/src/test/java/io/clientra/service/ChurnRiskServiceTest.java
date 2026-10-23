package io.clientra.service;

import io.clientra.TestFixtures;
import io.clientra.dto.CustomerDtos.ChurnRiskDto;
import io.clientra.entity.enums.CustomerStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class ChurnRiskServiceTest {

    private final ChurnRiskService service = new ChurnRiskService(null, null, null, null, TestFixtures.props(), TestFixtures.CLOCK);

    private ChurnRiskService.Features features(CustomerStatus status, Long daysSinceOrder, long orders, String ltv, long openTickets) {
        return new ChurnRiskService.Features(status,
                daysSinceOrder == null ? null : TestFixtures.NOW.minus(Duration.ofDays(daysSinceOrder)),
                orders, new BigDecimal(ltv), openTickets, TestFixtures.NOW.minus(Duration.ofDays(400)));
    }

    @Test
    void loyalRecentHighValueCustomer_isLowRisk() {
        ChurnRiskDto r = service.score(features(CustomerStatus.ACTIVE, 10L, 8, "480000", 0));
        assertThat(r.score()).isZero();
        assertThat(r.level()).isEqualTo("LOW");
    }

    @Test
    void lapsedLowValueCustomerWithTickets_isHighRisk() {
        ChurnRiskDto r = service.score(features(CustomerStatus.INACTIVE, 200L, 1, "8000", 2));
        assertThat(r.score()).isGreaterThanOrEqualTo(ChurnRiskService.HIGH);
        assertThat(r.level()).isEqualTo("HIGH");
        assertThat(r.reasons()).anyMatch(s -> s.contains("No purchase in 200 days"));
    }

    @Test
    void churnedCustomer_isAlways100() {
        assertThat(service.score(features(CustomerStatus.CHURNED, 5L, 9, "900000", 0)).score()).isEqualTo(100);
    }

    @Test
    void leadWithoutOrders_isNotScoredAsRisk() {
        ChurnRiskDto r = service.score(features(CustomerStatus.LEAD, null, 0, "0", 0));
        assertThat(r.score()).isZero();
    }

    @Test
    void moderateRecency_isMediumRisk() {
        ChurnRiskDto r = service.score(features(CustomerStatus.ACTIVE, 60L, 2, "40000", 1));
        assertThat(r.level()).isEqualTo("MEDIUM");
    }
}
