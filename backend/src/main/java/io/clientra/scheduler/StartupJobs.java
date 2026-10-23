package io.clientra.scheduler;

import io.clientra.service.ChurnRiskService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Computes churn scores once at start-up so a fresh database has scores before the nightly run. */
@Component
@Profile("!test")
@RequiredArgsConstructor
public class StartupJobs {

    private final ChurnRiskService churn;

    @Order(0)
    @EventListener(ApplicationReadyEvent.class)
    public void scoreCustomers() {
        churn.recalculateAll();
    }
}
