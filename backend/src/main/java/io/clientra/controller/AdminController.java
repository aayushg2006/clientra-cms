package io.clientra.controller;

import io.clientra.config.CacheConfig;
import io.clientra.entity.ActivityLog;
import io.clientra.repository.ActivityLogRepository;
import io.clientra.service.ChurnRiskService;
import io.clientra.service.MailService;
import io.clientra.service.TicketService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

/** Audit-log viewer and maintenance actions for administrators. */
@Tag(name = "Admin")
@RestController
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final ActivityLogRepository logRepo;
    private final ChurnRiskService churn;
    private final TicketService tickets;
    private final MailService mail;
    private final RedisConnectionFactory redis;

    @Operation(summary = "Audit trail with filters")
    @GetMapping("/api/audit-logs")
    @Transactional(readOnly = true)
    public Page<AuditRow> auditLogs(@RequestParam(required = false) String entityType,
                                    @RequestParam(required = false) Long userId,
                                    @RequestParam(required = false) String q,
                                    @PageableDefault(size = 25, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Specification<ActivityLog> spec = (root, query, cb) -> cb.conjunction();
        if (entityType != null && !entityType.isBlank()) spec = spec.and((r, qq, cb) -> cb.equal(r.get("entityType"), entityType));
        if (userId != null) spec = spec.and((r, qq, cb) -> cb.equal(r.get("user").get("id"), userId));
        if (q != null && !q.isBlank()) {
            String like = "%" + q.trim().toLowerCase() + "%";
            spec = spec.and((r, qq, cb) -> cb.or(cb.like(cb.lower(r.get("summary")), like), cb.like(cb.lower(r.get("action")), like)));
        }
        return logRepo.findAll(spec, pageable).map(a -> new AuditRow(a.getId(), a.getEntityType(), a.getEntityId(), a.getCustomerId(),
                a.getAction(), a.getSummary(), a.getDetails(), a.getUser() != null ? a.getUser().getFullName() : "System", a.getCreatedAt()));
    }

    public record AuditRow(Long id, String entityType, Long entityId, Long customerId, String action, String summary,
                           String details, String userName, Instant createdAt) {
    }

    @Operation(summary = "Recalculate churn-risk scores now (normally nightly)")
    @PostMapping("/api/admin/churn/recalculate")
    @CacheEvict(cacheNames = CacheConfig.DASHBOARD, allEntries = true)
    public Map<String, Integer> recalculateChurn() {
        return Map.of("newlyHighRisk", churn.recalculateAll());
    }

    @Operation(summary = "Run the SLA breach check now (normally every 5 minutes)")
    @PostMapping("/api/admin/sla/check")
    public Map<String, Integer> slaCheck() {
        return Map.of("newlyBreached", tickets.flagBreaches());
    }

    @GetMapping("/api/admin/system")
    public Map<String, Object> system() {
        boolean redisUp;
        try (var conn = redis.getConnection()) {
            redisUp = "PONG".equalsIgnoreCase(conn.ping());
        } catch (Exception e) {
            redisUp = false;
        }
        return Map.of("mailEnabled", mail.isEnabled(), "redisUp", redisUp);
    }
}
