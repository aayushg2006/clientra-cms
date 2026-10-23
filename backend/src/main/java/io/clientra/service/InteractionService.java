package io.clientra.service;

import io.clientra.config.AppProperties;
import io.clientra.config.CacheConfig;
import io.clientra.dto.InteractionDtos.InteractionDto;
import io.clientra.dto.InteractionDtos.InteractionRequest;
import io.clientra.entity.Customer;
import io.clientra.entity.Interaction;
import io.clientra.entity.enums.CustomerStatus;
import io.clientra.entity.enums.InteractionType;
import io.clientra.entity.enums.Role;
import io.clientra.exception.BusinessRuleException;
import io.clientra.exception.ResourceNotFoundException;
import io.clientra.repository.InteractionRepository;
import io.clientra.repository.UserRepository;
import io.clientra.security.AppUserPrincipal;
import io.clientra.security.CurrentUser;
import io.clientra.util.Formats;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class InteractionService {

    private final InteractionRepository repo;
    private final UserRepository userRepo;
    private final CustomerService customerService;
    private final AuditService audit;
    private final AppProperties props;
    private final Clock clock;

    public record FollowUps(List<InteractionDto> overdue, List<InteractionDto> today, List<InteractionDto> upcoming) {
    }

    @Transactional(readOnly = true)
    public Page<InteractionDto> list(Long customerId, InteractionType type, boolean mine, String q, Pageable pageable) {
        Specification<Interaction> spec = (root, query, cb) -> cb.isNull(root.get("customer").get("deletedAt"));
        if (customerId != null) spec = spec.and((r, qq, cb) -> cb.equal(r.get("customer").get("id"), customerId));
        if (type != null) spec = spec.and((r, qq, cb) -> cb.equal(r.get("type"), type));
        if (mine) {
            Long me = CurrentUser.id();
            spec = spec.and((r, qq, cb) -> cb.equal(r.get("user").get("id"), me));
        }
        if (q != null && !q.isBlank()) {
            String like = "%" + q.trim().toLowerCase() + "%";
            spec = spec.and((r, qq, cb) -> cb.or(cb.like(cb.lower(r.get("subject")), like),
                    cb.like(cb.lower(r.get("customer").get("firstName")), like),
                    cb.like(cb.lower(cb.coalesce(r.get("customer").get("companyName"), "")), like)));
        }
        return repo.findAll(spec, pageable).map(InteractionDto::from);
    }

    /** Pending follow-ups for the current user (admins see everyone's), split into overdue / today / next 7 days. */
    @Transactional(readOnly = true)
    public FollowUps followUps() {
        AppUserPrincipal me = CurrentUser.require();
        Long userId = me.role() == Role.ADMIN ? null : me.id();
        ZoneId zone = props.timezone();
        LocalDate today = LocalDate.now(clock.withZone(zone));
        Instant start = today.atStartOfDay(zone).toInstant();
        Instant end = today.plusDays(1).atStartOfDay(zone).toInstant();
        return new FollowUps(
                repo.findOverdueFollowUps(start, userId).stream().map(InteractionDto::from).toList(),
                repo.findFollowUpsBetween(start, end, userId).stream().map(InteractionDto::from).toList(),
                repo.findFollowUpsBetween(end, today.plusDays(8).atStartOfDay(zone).toInstant(), userId).stream()
                        .map(InteractionDto::from).toList());
    }

    @Transactional
    @CacheEvict(cacheNames = CacheConfig.DASHBOARD, allEntries = true)
    public InteractionDto create(InteractionRequest req) {
        Customer customer = customerService.find(req.customerId());
        Instant now = Instant.now(clock);
        Instant at = req.interactionAt() != null ? req.interactionAt() : now;
        if (at.isAfter(now.plus(Duration.ofMinutes(5)))) {
            throw new BusinessRuleException("An interaction cannot be logged in the future - set a follow-up instead");
        }
        if (req.followUpAt() != null && !req.followUpAt().isAfter(at)) {
            throw new BusinessRuleException("Follow-up must be after the interaction time");
        }
        Interaction i = new Interaction();
        i.setCustomer(customer);
        i.setUser(userRepo.getReferenceById(CurrentUser.require().id()));
        i.setType(req.type());
        i.setSubject(req.subject().trim());
        i.setNotes(Formats.blankToNull(req.notes()));
        i.setInteractionAt(at);
        i.setFollowUpAt(req.followUpAt());
        i = repo.save(i);
        // contacting an inactive customer is the first step of win-back; keep status, but record it
        audit.log("INTERACTION", i.getId(), customer.getId(), "LOGGED",
                Formats.label(req.type()) + " logged: " + i.getSubject(),
                req.followUpAt() != null ? Map.of("followUpAt", req.followUpAt().toString()) : null);
        if (customer.getStatus() == CustomerStatus.CHURNED) {
            audit.log("CUSTOMER", customer.getId(), customer.getId(), "WIN_BACK_ATTEMPT", "Win-back contact with churned customer");
        }
        return InteractionDto.from(i);
    }

    @Transactional
    @CacheEvict(cacheNames = CacheConfig.DASHBOARD, allEntries = true)
    public InteractionDto completeFollowUp(Long id) {
        Interaction i = repo.findById(id).orElseThrow(() -> new ResourceNotFoundException("Interaction", id));
        if (i.getFollowUpAt() == null) throw new BusinessRuleException("This interaction has no follow-up");
        i.setFollowUpDone(true);
        audit.log("INTERACTION", id, i.getCustomer().getId(), "FOLLOW_UP_DONE", "Follow-up completed: " + i.getSubject());
        return InteractionDto.from(i);
    }
}
