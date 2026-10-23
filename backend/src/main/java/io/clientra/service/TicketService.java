package io.clientra.service;

import io.clientra.config.AppProperties;
import io.clientra.config.CacheConfig;
import io.clientra.dto.TicketDtos.*;
import io.clientra.dto.UserDto;
import io.clientra.entity.*;
import io.clientra.entity.enums.*;
import io.clientra.exception.BusinessRuleException;
import io.clientra.exception.ResourceNotFoundException;
import io.clientra.repository.*;
import io.clientra.security.AppUserPrincipal;
import io.clientra.security.CurrentUser;
import io.clientra.util.Formats;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class TicketService {

    static final List<TicketStatus> UNRESOLVED = List.of(TicketStatus.OPEN, TicketStatus.IN_PROGRESS, TicketStatus.AWAITING_CUSTOMER);

    private final SupportTicketRepository ticketRepo;
    private final TicketCommentRepository commentRepo;
    private final ActivityLogRepository logRepo;
    private final OrderRepository orderRepo;
    private final UserRepository userRepo;
    private final CustomerService customerService;
    private final NotificationService notifications;
    private final MailService mail;
    private final AuditService audit;
    private final AppProperties props;
    private final Clock clock;

    // ------------------------------------------------------------------ queries

    /** Kanban board. The Resolved column shows tickets resolved in the last 7 days. */
    @Transactional(readOnly = true)
    public Board board(String q, TicketPriority priority, boolean mine) {
        Instant now = Instant.now(clock);
        Specification<SupportTicket> spec = (root, query, cb) -> cb.or(
                cb.notEqual(root.get("status"), TicketStatus.RESOLVED),
                cb.greaterThanOrEqualTo(root.get("resolvedAt"), now.minus(Duration.ofDays(7))));
        if (priority != null) spec = spec.and((r, qq, cb) -> cb.equal(r.get("priority"), priority));
        if (mine) {
            Long me = CurrentUser.id();
            spec = spec.and((r, qq, cb) -> cb.equal(r.get("assignee").get("id"), me));
        }
        if (q != null && !q.isBlank()) {
            String like = "%" + q.trim().toLowerCase() + "%";
            spec = spec.and((r, qq, cb) -> cb.or(cb.like(cb.lower(r.get("subject")), like),
                    cb.like(cb.lower(r.get("ticketNumber")), like),
                    cb.like(cb.lower(r.get("customer").get("firstName")), like)));
        }
        List<SupportTicket> tickets = ticketRepo.findAll(spec);
        Comparator<SupportTicket> order = Comparator.comparing(SupportTicket::getId).reversed();
        List<BoardColumn> columns = new ArrayList<>();
        for (TicketStatus s : TicketStatus.values()) {
            List<TicketSummary> col = tickets.stream().filter(t -> t.getStatus() == s)
                    .sorted(s == TicketStatus.RESOLVED ? Comparator.comparing(SupportTicket::getResolvedAt).reversed() : order)
                    .map(TicketSummary::from).toList();
            columns.add(new BoardColumn(s, col.size(), col));
        }
        Instant monthAgo = now.minus(Duration.ofDays(30));
        List<SupportTicket> responded = ticketRepo.findRespondedSince(monthAgo);
        Long avgFirstResponse = responded.isEmpty() ? null : Math.round(responded.stream()
                .mapToLong(t -> Duration.between(t.getCreatedAt(), t.getFirstResponseAt()).toMinutes()).average().orElse(0));
        return new Board(columns, ticketRepo.countByStatusIn(UNRESOLVED), avgFirstResponse, slaCompliance(monthAgo));
    }

    /** Share of tickets resolved within SLA over the window (0-100). */
    @Transactional(readOnly = true)
    public Double slaCompliance(Instant since) {
        List<SupportTicket> resolved = ticketRepo.findResolvedSince(since);
        if (resolved.isEmpty()) return null;
        long ok = resolved.stream().filter(t -> !t.getResolvedAt().isAfter(t.getSlaDueAt())).count();
        return Math.round(ok * 1000.0 / resolved.size()) / 10.0;
    }

    @Transactional(readOnly = true)
    public long openCount() {
        return ticketRepo.countByStatusIn(UNRESOLVED);
    }

    @Transactional(readOnly = true)
    public TicketDetail get(Long id) {
        SupportTicket t = find(id);
        return detail(t);
    }

    private TicketDetail detail(SupportTicket t) {
        List<CommentDto> comments = t.getComments().stream().map(CommentDto::from).toList();
        List<ActivityDto> activity = logRepo.findByEntityTypeAndEntityIdOrderByCreatedAtAsc("TICKET", t.getId())
                .stream().map(ActivityDto::from).toList();
        return new TicketDetail(t.getId(), t.getTicketNumber(), t.getSubject(), t.getCustomer().getId(),
                t.getCustomer().getFullName(), t.getCustomer().getEmail(), t.getChannel(), t.getCategory(), t.getPriority(),
                t.getStatus(), t.getSlaDueAt(), t.isSlaBreached(), t.isEscalated(), t.getResolvedAt(), t.getCreatedAt(),
                UserDto.Ref.of(t.getAssignee()), t.getDescription(), t.getOrder() != null ? t.getOrder().getId() : null,
                t.getOrder() != null ? t.getOrder().getOrderNumber() : null, t.getFirstResponseAt(), comments, activity);
    }

    // ------------------------------------------------------------------ commands

    @Transactional
    @CacheEvict(cacheNames = {CacheConfig.DASHBOARD, CacheConfig.REPORTS}, allEntries = true)
    public TicketDetail create(CreateTicketRequest req) {
        Customer customer = customerService.find(req.customerId());
        SupportTicket t = new SupportTicket();
        t.setCustomer(customer);
        if (req.orderId() != null) {
            Order order = orderRepo.findById(req.orderId()).orElseThrow(() -> new ResourceNotFoundException("Order", req.orderId()));
            if (!order.getCustomer().getId().equals(customer.getId())) {
                throw new BusinessRuleException("The selected order belongs to a different customer");
            }
            t.setOrder(order);
        }
        AppUserPrincipal me = CurrentUser.require();
        t.setCreatedBy(userRepo.getReferenceById(me.id()));
        t.setSubject(req.subject().trim());
        t.setDescription(Formats.blankToNull(req.description()));
        t.setChannel(req.channel());
        t.setCategory(req.category());
        t.setPriority(req.priority());
        Instant now = Instant.now(clock);
        t.setSlaDueAt(now.plus(slaFor(req.priority())));

        boolean auto = req.assigneeId() == null || me.role() != Role.ADMIN;
        User assignee = auto ? leastLoadedAgent() : activeAgent(req.assigneeId());
        t.setAssignee(assignee);
        if (t.getDescription() != null) {
            TicketComment first = new TicketComment();
            first.setTicket(t);
            first.setFromCustomer(true);
            first.setBody(t.getDescription());
            t.getComments().add(first);
        }
        t = ticketRepo.save(t);
        t.setTicketNumber("TKT-" + t.getId());
        audit.log("TICKET", t.getId(), customer.getId(), "CREATED", "Ticket created",
                Map.of("priority", req.priority(), "channel", req.channel(), "slaDueAt", t.getSlaDueAt().toString()));
        if (assignee != null) {
            audit.log("TICKET", t.getId(), customer.getId(), "ASSIGNED", "Assigned to " + assignee.getFullName() + (auto ? " (auto)" : ""));
            notifyAssignee(t, assignee);
        }
        return detail(t);
    }

    @Transactional
    public TicketDetail assign(Long id, Long assigneeId) {
        SupportTicket t = find(id);
        User agent = activeAgent(assigneeId);
        if (t.getAssignee() != null && t.getAssignee().getId().equals(agent.getId())) return detail(t);
        t.setAssignee(agent);
        audit.log("TICKET", id, t.getCustomer().getId(), "ASSIGNED", "Assigned to " + agent.getFullName());
        notifyAssignee(t, agent);
        return detail(t);
    }

    @Transactional
    @CacheEvict(cacheNames = {CacheConfig.DASHBOARD, CacheConfig.REPORTS}, allEntries = true)
    public TicketDetail changeStatus(Long id, TicketStatus target) {
        SupportTicket t = find(id);
        TicketStatus current = t.getStatus();
        if (current == target) return detail(t);
        Instant now = Instant.now(clock);
        if (target == TicketStatus.RESOLVED) {
            t.setResolvedAt(now);
            if (t.getFirstResponseAt() == null) t.setFirstResponseAt(now);
            mail.send(t.getCustomer().getEmail(), "[" + t.getTicketNumber() + "] Resolved: " + t.getSubject(),
                    "Your ticket has been resolved",
                    "<p>Hi " + t.getCustomer().getFirstName() + ",</p><p>Your support request <b>" + t.getTicketNumber() + " — "
                            + t.getSubject() + "</b> has been marked as resolved. If anything is still not right, just reply to this email.</p>");
        } else if (current == TicketStatus.RESOLVED) {
            // reopening starts a fresh SLA window
            t.setResolvedAt(null);
            t.setSlaBreached(false);
            t.setSlaDueAt(now.plus(slaFor(t.getPriority())));
        }
        t.setStatus(target);
        audit.log("TICKET", id, t.getCustomer().getId(), "STATUS_CHANGED", "Status → " + Formats.label(target),
                Map.of("from", current, "to", target));
        return detail(t);
    }

    @Transactional
    @CacheEvict(cacheNames = CacheConfig.REPORTS, allEntries = true)
    public TicketDetail addComment(Long id, CommentRequest req) {
        SupportTicket t = find(id);
        if (t.getStatus() == TicketStatus.RESOLVED && !req.internal()) {
            throw new BusinessRuleException("Reopen the ticket before replying");
        }
        TicketComment c = new TicketComment();
        c.setTicket(t);
        c.setBody(req.body().trim());
        c.setInternal(req.internal() && !req.fromCustomer());
        c.setFromCustomer(req.fromCustomer());
        if (!req.fromCustomer()) c.setAuthor(userRepo.getReferenceById(CurrentUser.require().id()));
        t.getComments().add(c);
        commentRepo.save(c);

        if (req.fromCustomer()) {
            if (t.getStatus() == TicketStatus.AWAITING_CUSTOMER) t.setStatus(TicketStatus.IN_PROGRESS);
            audit.log("TICKET", id, t.getCustomer().getId(), "CUSTOMER_MESSAGE", "Customer message logged");
            if (t.getAssignee() != null && !t.getAssignee().getId().equals(CurrentUser.id())) {
                notifications.notify(t.getAssignee(), NotificationType.TICKET_UPDATE, t.getTicketNumber() + ": customer replied",
                        abbreviate(req.body()), "/tickets/" + id, false);
            }
        } else if (c.isInternal()) {
            audit.log("TICKET", id, t.getCustomer().getId(), "NOTE_ADDED", "Internal note added");
        } else {
            Instant now = Instant.now(clock);
            if (t.getFirstResponseAt() == null) t.setFirstResponseAt(now);
            if (t.getStatus() == TicketStatus.OPEN) {
                t.setStatus(TicketStatus.IN_PROGRESS);
                audit.log("TICKET", id, t.getCustomer().getId(), "STATUS_CHANGED", "Status → In Progress");
            }
            audit.log("TICKET", id, t.getCustomer().getId(), "REPLIED", "Reply sent to customer");
            mail.send(t.getCustomer().getEmail(), "Re: [" + t.getTicketNumber() + "] " + t.getSubject(),
                    "Update on your request " + t.getTicketNumber(),
                    "<p>" + escape(req.body()).replace("\n", "<br>") + "</p><p style=\"color:#6B7280\">— "
                            + CurrentUser.require().fullName() + ", Clientra Support</p>");
        }
        return detail(t);
    }

    /** Escalation raises priority to High, tightens the SLA and alerts admins. */
    @Transactional
    @CacheEvict(cacheNames = {CacheConfig.DASHBOARD, CacheConfig.REPORTS}, allEntries = true)
    public TicketDetail escalate(Long id) {
        SupportTicket t = find(id);
        if (t.getStatus() == TicketStatus.RESOLVED) throw new BusinessRuleException("Resolved tickets cannot be escalated");
        if (t.isEscalated()) throw new BusinessRuleException("Ticket is already escalated");
        TicketPriority old = t.getPriority();
        t.setEscalated(true);
        t.setPriority(TicketPriority.HIGH);
        Instant tighter = Instant.now(clock).plus(slaFor(TicketPriority.HIGH));
        if (tighter.isBefore(t.getSlaDueAt())) t.setSlaDueAt(tighter);
        audit.log("TICKET", id, t.getCustomer().getId(), "ESCALATED", "Escalated (priority " + Formats.label(old) + " → High)");
        notifications.notifyAdmins(NotificationType.TICKET_UPDATE, "Escalated: " + t.getTicketNumber(),
                t.getSubject() + " — " + t.getCustomer().getFullName(), "/tickets/" + id, true);
        return detail(t);
    }

    /** Flags tickets past their SLA deadline; tickets awaiting the customer are paused. Returns newly breached count. */
    @Transactional
    public int flagBreaches() {
        List<SupportTicket> overdue = ticketRepo.findByStatusInAndSlaDueAtBefore(
                List.of(TicketStatus.OPEN, TicketStatus.IN_PROGRESS), Instant.now(clock));
        int newly = 0;
        for (SupportTicket t : overdue) {
            if (t.isSlaBreached()) continue;
            newly++;
            t.setSlaBreached(true);
            audit.log("TICKET", t.getId(), t.getCustomer().getId(), "SLA_BREACHED", "SLA breached");
            String title = "SLA breached: " + t.getTicketNumber();
            String message = t.getSubject() + " (" + Formats.label(t.getPriority()) + ") — " + t.getCustomer().getFullName();
            notifications.notifyAdmins(NotificationType.SLA_BREACH, title, message, "/tickets/" + t.getId(), true);
            if (t.getAssignee() != null && t.getAssignee().getRole() != Role.ADMIN) {
                notifications.notify(t.getAssignee(), NotificationType.SLA_BREACH, title, message, "/tickets/" + t.getId(), false);
            }
        }
        return newly;
    }

    // ------------------------------------------------------------------ helpers

    private SupportTicket find(Long id) {
        return ticketRepo.findDetailById(id).orElseThrow(() -> new ResourceNotFoundException("Ticket", id));
    }

    Duration slaFor(TicketPriority p) {
        return Duration.ofHours(switch (p) {
            case HIGH -> props.sla().highHours();
            case MEDIUM -> props.sla().mediumHours();
            case LOW -> props.sla().lowHours();
        });
    }

    /** Least-loaded active support agent by number of unresolved tickets (ties: alphabetical). */
    private User leastLoadedAgent() {
        List<User> agents = userRepo.findByRoleAndActiveTrueOrderByFullName(Role.SUPPORT_AGENT);
        if (agents.isEmpty()) return null;
        Map<Long, Long> load = new HashMap<>();
        ticketRepo.openLoadByAgent().forEach(r -> load.put((Long) r[0], (Long) r[1]));
        return agents.stream().min(Comparator.comparingLong(a -> load.getOrDefault(a.getId(), 0L))).orElse(null);
    }

    private User activeAgent(Long id) {
        User u = userRepo.findById(id).orElseThrow(() -> new ResourceNotFoundException("User", id));
        if (!u.isActive() || u.getRole() == Role.SALES_EXEC) {
            throw new BusinessRuleException("Tickets can only be assigned to active support agents or admins");
        }
        return u;
    }

    private void notifyAssignee(SupportTicket t, User assignee) {
        if (assignee.getId().equals(CurrentUser.id())) return;
        notifications.notify(assignee, NotificationType.TICKET_ASSIGNED, "New ticket " + t.getTicketNumber(),
                t.getSubject() + " — " + t.getCustomer().getFullName() + " (" + Formats.label(t.getPriority()) + ")",
                "/tickets/" + t.getId(), true);
    }

    private static String abbreviate(String s) {
        return s.length() > 140 ? s.substring(0, 137) + "..." : s;
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
