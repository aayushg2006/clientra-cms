package io.clientra.service;

import io.clientra.config.CacheConfig;
import io.clientra.dto.CustomerDtos.*;
import io.clientra.dto.InteractionDtos.InteractionDto;
import io.clientra.dto.OrderDtos.OrderSummary;
import io.clientra.dto.TicketDtos.TicketSummary;
import io.clientra.dto.UserDto;
import io.clientra.entity.*;
import io.clientra.entity.enums.*;
import io.clientra.exception.BusinessRuleException;
import io.clientra.exception.DuplicateCustomerException;
import io.clientra.exception.ResourceNotFoundException;
import io.clientra.mapper.CustomerMapper;
import io.clientra.repository.*;
import io.clientra.repository.spec.CustomerSpecs;
import io.clientra.util.Formats;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class CustomerService {

    private static final List<TicketStatus> OPEN = List.of(TicketStatus.OPEN, TicketStatus.IN_PROGRESS, TicketStatus.AWAITING_CUSTOMER);

    private final CustomerRepository customerRepo;
    private final UserRepository userRepo;
    private final TagRepository tagRepo;
    private final InteractionRepository interactionRepo;
    private final OrderRepository orderRepo;
    private final SupportTicketRepository ticketRepo;
    private final ActivityLogRepository logRepo;
    private final CustomerMapper mapper;
    private final AuditService audit;
    private final MailService mail;
    private final ChurnRiskService churn;
    private final Clock clock;

    // ------------------------------------------------------------------ queries

    @Transactional(readOnly = true)
    public Page<CustomerDto> search(String q, CustomerStatus status, Segment segment, Long ownerId, Long tagId, Pageable pageable) {
        Specification<Customer> spec = Specification.where(CustomerSpecs.notDeleted())
                .and(CustomerSpecs.text(q))
                .and(CustomerSpecs.status(status))
                .and(CustomerSpecs.segment(segment))
                .and(CustomerSpecs.owner(ownerId))
                .and(CustomerSpecs.tag(tagId));
        Page<Customer> page = customerRepo.findAll(spec, pageable);
        Map<Long, Instant> lastContact = new HashMap<>();
        if (page.hasContent()) {
            interactionRepo.findLastInteractionAt(page.map(Customer::getId).getContent())
                    .forEach(r -> lastContact.put((Long) r[0], (Instant) r[1]));
        }
        return page.map(c -> mapper.toDto(c, lastContact.get(c.getId())));
    }

    @Transactional(readOnly = true)
    public CustomerStats stats() {
        return new CustomerStats(customerRepo.countByDeletedAtIsNull(),
                customerRepo.countByStatusAndDeletedAtIsNull(CustomerStatus.LEAD),
                customerRepo.countByStatusAndDeletedAtIsNull(CustomerStatus.ACTIVE),
                customerRepo.countByStatusAndDeletedAtIsNull(CustomerStatus.INACTIVE),
                customerRepo.countByStatusAndDeletedAtIsNull(CustomerStatus.CHURNED));
    }

    @Transactional(readOnly = true)
    public List<CustomerLookup> lookup(String q) {
        Specification<Customer> spec = Specification.where(CustomerSpecs.notDeleted()).and(CustomerSpecs.text(q));
        return customerRepo.findAll(spec, org.springframework.data.domain.PageRequest.of(0, 10,
                        org.springframework.data.domain.Sort.by("firstName")))
                .map(c -> new CustomerLookup(c.getId(), c.getFullName(), c.getCustomerCode(), c.getCompanyName(), c.getEmail(), c.getStatus()))
                .getContent();
    }

    /** Customer 360: profile, account summary, latest orders / tickets / interactions and a merged timeline. */
    @Transactional(readOnly = true)
    public Customer360 getProfile(Long id) {
        Customer c = customerRepo.findProfileById(id).orElseThrow(() -> new ResourceNotFoundException("Customer", id));
        List<Interaction> interactions = interactionRepo.findTop20ByCustomerIdOrderByInteractionAtDesc(id);
        List<Order> orders = orderRepo.findTop20ByCustomerIdOrderByOrderDateDesc(id);
        List<SupportTicket> tickets = ticketRepo.findTop20ByCustomerIdOrderByCreatedAtDesc(id);
        List<ActivityLog> activity = logRepo.findTop20ByCustomerIdOrderByCreatedAtDesc(id);

        Instant lastContact = interactions.isEmpty() ? null : interactions.get(0).getInteractionAt();
        long openTickets = tickets.stream().filter(t -> OPEN.contains(t.getStatus())).count();
        long orderCount = orderRepo.countByCustomerId(id);

        return new Customer360(c.getId(), c.getCustomerCode(), c.getFirstName(), c.getLastName(), c.getFullName(),
                c.getEmail(), c.getPhone(), c.getCompanyName(), c.getJobTitle(), c.getCustomerType(), c.getSegment(),
                c.getStatus(), c.getSource(), UserDto.Ref.of(c.getOwner()), c.getLifetimeValue(), c.getChurnScore(),
                mapper.tags(c.getTags()), c.getNotes(), c.getVersion(), c.getCreatedAt(), lastContact,
                c.getAddresses().stream().map(mapper::toDto).toList(), orderCount, openTickets, tickets.size(),
                churn.scoreFor(c),
                orders.stream().map(o -> {
                    o.setCustomer(c);
                    return OrderSummary.from(o);
                }).toList(),
                tickets.stream().map(t -> {
                    t.setCustomer(c);
                    return TicketSummary.from(t);
                }).toList(),
                interactions.stream().map(i -> {
                    i.setCustomer(c);
                    return InteractionDto.from(i);
                }).toList(),
                timeline(interactions, orders, tickets, activity));
    }

    private List<TimelineEvent> timeline(List<Interaction> interactions, List<Order> orders, List<SupportTicket> tickets,
                                         List<ActivityLog> activity) {
        Stream<TimelineEvent> fromInteractions = interactions.stream().map(i -> new TimelineEvent("INTERACTION",
                icon(i.getType()), Formats.label(i.getType()) + " — " + i.getSubject(), i.getUser().getFullName(),
                i.getInteractionAt(), i.getNotes(), List.of(), i.isFollowUpDone() ? null : i.getFollowUpAt(), null, null));

        Stream<TimelineEvent> fromOrders = orders.stream().map(o -> new TimelineEvent("ORDER", "🧾",
                "Order " + o.getOrderNumber() + " placed — " + Formats.inr(o.getTotalAmount()),
                o.getCreatedBy().getFullName(), o.getOrderDate(), null,
                List.of(new Badge(Formats.label(o.getPaymentStatus()), paymentTone(o.getPaymentStatus())),
                        new Badge(Formats.label(o.getStatus()), orderTone(o.getStatus()))),
                null, null, "/orders?open=" + o.getId()));

        Stream<TimelineEvent> fromTickets = tickets.stream().map(t -> new TimelineEvent("TICKET", "🎫",
                "Ticket " + t.getTicketNumber() + " — " + t.getSubject(),
                t.getAssignee() != null ? t.getAssignee().getFullName() : "Unassigned", t.getCreatedAt(), null,
                List.of(new Badge(Formats.label(t.getPriority()), t.getPriority() == TicketPriority.HIGH ? "red"
                                : t.getPriority() == TicketPriority.MEDIUM ? "amber" : "teal"),
                        new Badge(Formats.label(t.getStatus()), t.getStatus() == TicketStatus.RESOLVED ? "green" : "amber")),
                null, t.getStatus() == TicketStatus.RESOLVED ? null : t.getSlaDueAt(), "/tickets/" + t.getId()));

        // Only profile-level events; ticket and order changes already appear above.
        Set<String> profileActions = Set.of("TAG_ADDED", "TAG_REMOVED", "STATUS_CHANGED", "SEGMENT_CHANGED", "OWNER_CHANGED",
                "IMPORTED", "UPDATED");
        Stream<TimelineEvent> fromActivity = activity.stream()
                .filter(a -> a.getEntityType().equals("CUSTOMER") && profileActions.contains(a.getAction()))
                .map(a -> new TimelineEvent("ACTIVITY", "🏷️", a.getSummary(),
                        a.getUser() != null ? a.getUser().getFullName() : "System", a.getCreatedAt(), null, List.of(),
                        null, null, null));

        return Stream.of(fromInteractions, fromOrders, fromTickets, fromActivity).flatMap(s -> s)
                .sorted(Comparator.comparing(TimelineEvent::at).reversed())
                .limit(25)
                .toList();
    }

    @Transactional(readOnly = true)
    public DuplicateCheck checkDuplicate(String email, String phone, Long excludeId) {
        if (email != null && !email.isBlank()) {
            Optional<Customer> byEmail = customerRepo.findByEmailIgnoreCase(email.trim())
                    .filter(c -> !c.getId().equals(excludeId));
            if (byEmail.isPresent()) {
                Customer c = byEmail.get();
                return new DuplicateCheck(true, "email", c.getId(), c.getCustomerCode(), c.getFullName());
            }
        }
        String normalized = Formats.phone(phone);
        if (normalized != null) {
            Optional<Customer> byPhone = customerRepo.findFirstByPhoneAndDeletedAtIsNull(normalized)
                    .filter(c -> !c.getId().equals(excludeId));
            if (byPhone.isPresent()) {
                Customer c = byPhone.get();
                return new DuplicateCheck(true, "phone", c.getId(), c.getCustomerCode(), c.getFullName());
            }
        }
        return new DuplicateCheck(false, null, null, null, null);
    }

    // ------------------------------------------------------------------ commands

    @Transactional
    @CacheEvict(cacheNames = {CacheConfig.DASHBOARD, CacheConfig.REPORTS}, allEntries = true)
    public CustomerDto create(CustomerRequest req) {
        return mapper.toDto(createEntity(req, "CREATED"));
    }

    /** Shared by the API and CSV import. Duplicate email (unique) or phone (service-level) raise 409. */
    @Transactional
    public Customer createEntity(CustomerRequest req, String auditAction) {
        rejectDuplicates(req.email(), req.phone(), null);
        Customer c = new Customer();
        apply(c, req);
        c.setStatus(CustomerStatus.LEAD);
        if (c.getOwner() == null && io.clientra.security.CurrentUser.id() != null) {
            c.setOwner(userRepo.getReferenceById(io.clientra.security.CurrentUser.id()));
        }
        c = customerRepo.save(c);
        c.setCustomerCode(String.format("CUST-%05d", c.getId()));
        audit.log("CUSTOMER", c.getId(), c.getId(), auditAction, "Customer " + c.getFullName() + " created as lead",
                Map.of("email", c.getEmail(), "segment", c.getSegment(), "source", String.valueOf(c.getSource())));
        if (req.sendWelcomeEmail()) {
            mail.send(c.getEmail(), "Welcome to Clientra", "Welcome, " + c.getFirstName() + "!",
                    "<p>Thank you for your interest. Your customer ID is <b>" + c.getCustomerCode()
                            + "</b>. Your account manager will be in touch shortly.</p>");
        }
        return c;
    }

    @Transactional
    @CacheEvict(cacheNames = {CacheConfig.DASHBOARD, CacheConfig.REPORTS}, allEntries = true)
    public Customer360 update(Long id, CustomerRequest req) {
        Customer c = find(id);
        if (req.version() != null && !req.version().equals(c.getVersion())) {
            throw new ObjectOptimisticLockingFailureException(Customer.class, id);
        }
        rejectDuplicates(req.email(), req.phone(), id);
        Segment oldSegment = c.getSegment();
        Long oldOwner = c.getOwner() != null ? c.getOwner().getId() : null;
        Set<String> oldTags = c.getTags().stream().map(Tag::getName).collect(Collectors.toSet());

        apply(c, req);

        Set<String> newTags = c.getTags().stream().map(Tag::getName).collect(Collectors.toSet());
        List<String> changes = new ArrayList<>();
        newTags.stream().filter(t -> !oldTags.contains(t)).forEach(t -> changes.add("Tag “" + t + "” added"));
        oldTags.stream().filter(t -> !newTags.contains(t)).forEach(t -> changes.add("Tag “" + t + "” removed"));
        if (oldSegment != c.getSegment()) changes.add("segment changed to " + Formats.label(c.getSegment()));
        Long newOwner = c.getOwner() != null ? c.getOwner().getId() : null;
        if (!Objects.equals(oldOwner, newOwner) && c.getOwner() != null) changes.add("owner changed to " + c.getOwner().getFullName());

        String action = changes.stream().anyMatch(s -> s.startsWith("Tag")) ? "TAG_ADDED"
                : oldSegment != c.getSegment() ? "SEGMENT_CHANGED" : !Objects.equals(oldOwner, newOwner) ? "OWNER_CHANGED" : "UPDATED";
        String summary = changes.isEmpty() ? "Profile details updated" : capitalize(String.join(", ", changes));
        audit.log("CUSTOMER", c.getId(), c.getId(), action, summary, Map.of("changes", changes));
        customerRepo.flush();
        return getProfile(id);
    }

    @Transactional
    @CacheEvict(cacheNames = {CacheConfig.DASHBOARD, CacheConfig.REPORTS}, allEntries = true)
    public CustomerDto changeStatus(Long id, CustomerStatus status) {
        Customer c = find(id);
        CustomerStatus old = c.getStatus();
        if (old == status) return mapper.toDto(c);
        c.setStatus(status);
        audit.log("CUSTOMER", id, id, "STATUS_CHANGED", "Status changed from " + Formats.label(old) + " to " + Formats.label(status),
                Map.of("from", old, "to", status));
        return mapper.toDto(c);
    }

    @Transactional
    public void updateNotes(Long id, String notes) {
        Customer c = find(id);
        c.setNotes(Formats.blankToNull(notes));
        audit.log("CUSTOMER", id, id, "NOTES_UPDATED", "Account notes updated");
    }

    /** Soft delete: the record stays for history and order/ticket integrity but disappears from lists and search. */
    @Transactional
    @CacheEvict(cacheNames = {CacheConfig.DASHBOARD, CacheConfig.REPORTS}, allEntries = true)
    public void softDelete(Long id) {
        Customer c = find(id);
        c.setDeletedAt(Instant.now(clock));
        // free the unique email so the person can be re-added later
        c.setEmail("deleted+" + c.getId() + "+" + c.getEmail());
        audit.log("CUSTOMER", id, id, "DELETED", "Customer " + c.getFullName() + " deleted");
    }

    // ------------------------------------------------------------------ helpers

    public Customer find(Long id) {
        return customerRepo.findById(id).filter(c -> c.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", id));
    }

    private void rejectDuplicates(String email, String phone, Long excludeId) {
        DuplicateCheck dup = checkDuplicate(email, phone, excludeId);
        if (dup.duplicate()) {
            throw new DuplicateCustomerException(dup.field(), dup.field().equals("email") ? email.trim() : phone,
                    dup.existingId(), dup.existingCode());
        }
    }

    private void apply(Customer c, CustomerRequest req) {
        c.setFirstName(req.firstName().trim());
        c.setLastName(Formats.blankToNull(req.lastName()));
        c.setEmail(req.email().trim().toLowerCase());
        c.setPhone(Formats.phone(req.phone()));
        c.setCompanyName(Formats.blankToNull(req.companyName()));
        c.setJobTitle(Formats.blankToNull(req.jobTitle()));
        c.setCustomerType(req.customerType());
        if (req.customerType() == CustomerType.BUSINESS && c.getCompanyName() == null) {
            throw new BusinessRuleException("Company name is required for business customers");
        }
        c.setSegment(req.segment() != null ? req.segment() : Segment.RETAIL);
        c.setSource(req.source());
        if (req.notes() != null) c.setNotes(Formats.blankToNull(req.notes()));
        if (req.assignedTo() != null) {
            User owner = userRepo.findById(req.assignedTo()).filter(User::isActive)
                    .orElseThrow(() -> new BusinessRuleException("Selected owner is not an active user"));
            c.setOwner(owner);
        }
        if (req.addresses() != null) {
            c.getAddresses().clear();
            boolean primarySet = false;
            for (AddressDto a : req.addresses()) {
                if (a == null || a.line1() == null || a.line1().isBlank()) continue;
                Address address = mapper.toEntity(a);
                address.setPrimary(!primarySet);
                primarySet = true;
                if (address.getCountry() == null) address.setCountry("India");
                c.addAddress(address);
            }
        }
        if (req.tags() != null) {
            c.getTags().clear();
            for (String name : req.tags()) {
                if (name == null || name.isBlank()) continue;
                Tag tag = tagRepo.findByNameIgnoreCase(name.trim()).orElseGet(() -> tagRepo.save(new Tag(name.trim(), "#14776B")));
                c.getTags().add(tag);
            }
        }
    }

    private static String icon(InteractionType t) {
        return switch (t) {
            case CALL -> "📞";
            case EMAIL, QUOTE -> "✉️";
            case MEETING, DEMO -> "🤝";
            case NOTE -> "📝";
        };
    }

    private static String paymentTone(PaymentStatus s) {
        return switch (s) {
            case PAID -> "green";
            case PENDING -> "amber";
            case REFUNDED -> "red";
        };
    }

    private static String orderTone(OrderStatus s) {
        return switch (s) {
            case CONFIRMED -> "amber";
            case SHIPPED -> "blue";
            case DELIVERED -> "green";
            case CANCELLED -> "red";
        };
    }

    private static String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
