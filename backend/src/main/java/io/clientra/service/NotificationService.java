package io.clientra.service;

import io.clientra.config.AppProperties;
import io.clientra.dto.NotificationDto;
import io.clientra.entity.Notification;
import io.clientra.entity.User;
import io.clientra.entity.enums.NotificationType;
import io.clientra.entity.enums.Role;
import io.clientra.exception.ResourceNotFoundException;
import io.clientra.repository.NotificationRepository;
import io.clientra.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/** In-app notifications (bell icon) with an optional email copy. */
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository repo;
    private final UserRepository userRepo;
    private final MailService mail;
    private final AppProperties props;
    private final Clock clock;

    @Transactional
    public void notify(User user, NotificationType type, String title, String message, String link, boolean email) {
        if (user == null || !user.isActive()) return;
        // avoid repeating the same alert for the same record within a day (scheduled jobs run repeatedly)
        if (link != null && repo.existsByUserIdAndLinkAndTitleAndCreatedAtAfter(user.getId(), link, title,
                Instant.now(clock).minus(Duration.ofHours(20)))) {
            return;
        }
        Notification n = new Notification();
        n.setUser(user);
        n.setType(type);
        n.setTitle(title);
        n.setMessage(message);
        n.setLink(link);
        repo.save(n);
        if (email) {
            String href = link != null ? props.frontendUrl() + link : props.frontendUrl();
            mail.send(user.getEmail(), "[Clientra] " + title, title,
                    "<p>" + (message != null ? message : "") + "</p>"
                            + "<p><a href=\"" + href + "\" style=\"display:inline-block;background:#14776B;color:#fff;"
                            + "padding:10px 18px;border-radius:10px;text-decoration:none;font-weight:600\">Open in Clientra</a></p>");
        }
    }

    @Transactional
    public void notifyAdmins(NotificationType type, String title, String message, String link, boolean email) {
        userRepo.findByRoleAndActiveTrueOrderByFullName(Role.ADMIN)
                .forEach(admin -> notify(admin, type, title, message, link, email));
    }

    @Transactional(readOnly = true)
    public Page<NotificationDto> list(Long userId, int page, int size) {
        return repo.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(page, size)).map(NotificationDto::from);
    }

    @Transactional(readOnly = true)
    public long unreadCount(Long userId) {
        return repo.countByUserIdAndReadFalse(userId);
    }

    @Transactional
    public void markRead(Long userId, Long id) {
        Notification n = repo.findById(id)
                .filter(x -> x.getUser().getId().equals(userId))
                .orElseThrow(() -> new ResourceNotFoundException("Notification", id));
        n.setRead(true);
    }

    @Transactional
    public int markAllRead(Long userId) {
        return repo.markAllRead(userId);
    }
}
