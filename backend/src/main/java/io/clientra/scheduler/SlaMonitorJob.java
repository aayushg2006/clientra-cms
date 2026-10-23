package io.clientra.scheduler;

import io.clientra.service.TicketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/** Every 5 minutes: flag tickets whose SLA deadline has passed and alert admins and the assignee. */
@Slf4j
@Component
@RequiredArgsConstructor
public class SlaMonitorJob {

    private final TicketService ticketService;

    @Scheduled(fixedRate = 5, initialDelay = 1, timeUnit = TimeUnit.MINUTES)
    public void flagBreaches() {
        int breached = ticketService.flagBreaches();
        if (breached > 0) {
            log.info("SLA check complete: {} newly breached tickets", breached);
        }
    }
}
