package io.clientra.scheduler;

import io.clientra.config.AppProperties;
import io.clientra.entity.Interaction;
import io.clientra.entity.User;
import io.clientra.entity.enums.NotificationType;
import io.clientra.repository.InteractionRepository;
import io.clientra.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Each morning (and on start-up) sends every owner one reminder for the follow-ups due today. */
@Slf4j
@Component
@RequiredArgsConstructor
public class FollowUpReminderJob {

    private final InteractionRepository interactionRepo;
    private final NotificationService notifications;
    private final AppProperties props;
    private final Clock clock;

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        remind();
    }

    @Scheduled(cron = "0 0 8 * * *", zone = "${app.timezone}")
    @Transactional
    public void remind() {
        ZoneId zone = props.timezone();
        LocalDate today = LocalDate.now(clock.withZone(zone));
        List<Interaction> due = interactionRepo.findFollowUpsBetween(today.atStartOfDay(zone).toInstant(),
                today.plusDays(1).atStartOfDay(zone).toInstant(), null);
        DateTimeFormatter hm = DateTimeFormatter.ofPattern("HH:mm").withZone(zone);
        Map<User, List<Interaction>> byOwner = due.stream().collect(Collectors.groupingBy(Interaction::getUser));
        byOwner.forEach((user, items) -> {
            String list = items.stream().map(i -> hm.format(i.getFollowUpAt()) + " " + i.getCustomer().getFullName())
                    .collect(Collectors.joining(", "));
            notifications.notify(user, NotificationType.FOLLOW_UP,
                    items.size() + " follow-up" + (items.size() == 1 ? "" : "s") + " due today", list, "/interactions", true);
        });
        if (!byOwner.isEmpty()) log.info("Follow-up reminders sent to {} users", byOwner.size());
    }
}
