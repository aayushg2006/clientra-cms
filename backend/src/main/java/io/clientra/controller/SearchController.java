package io.clientra.controller;

import io.clientra.entity.SupportTicket;
import io.clientra.entity.enums.CustomerStatus;
import io.clientra.entity.enums.Role;
import io.clientra.entity.enums.TicketStatus;
import io.clientra.repository.CustomerRepository;
import io.clientra.repository.SupportTicketRepository;
import io.clientra.repository.spec.CustomerSpecs;
import io.clientra.security.CurrentUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Header search box: top customers and (for admins/support) tickets matching the text. */
@Tag(name = "Search")
@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class SearchController {

    private final CustomerRepository customerRepo;
    private final SupportTicketRepository ticketRepo;

    public record CustomerHit(Long id, String name, String code, String email, String companyName, CustomerStatus status) {
    }

    public record TicketHit(Long id, String number, String subject, String customerName, TicketStatus status) {
    }

    public record Result(List<CustomerHit> customers, List<TicketHit> tickets) {
    }

    @GetMapping
    @Transactional(readOnly = true)
    public Result search(@RequestParam String q) {
        String text = q.trim();
        if (text.length() < 2) return new Result(List.of(), List.of());
        List<CustomerHit> customers = customerRepo.findAll(
                        Specification.where(CustomerSpecs.notDeleted()).and(CustomerSpecs.text(text)),
                        PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "lifetimeValue")))
                .map(c -> new CustomerHit(c.getId(), c.getFullName(), c.getCustomerCode(), c.getEmail(), c.getCompanyName(), c.getStatus()))
                .getContent();

        List<TicketHit> tickets = List.of();
        if (CurrentUser.hasRole(Role.ADMIN) || CurrentUser.hasRole(Role.SUPPORT_AGENT)) {
            String like = "%" + text.toLowerCase() + "%";
            Specification<SupportTicket> spec = (root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("subject")), like),
                    cb.like(cb.lower(root.get("ticketNumber")), like),
                    cb.like(cb.lower(root.join("customer").get("firstName")), like));
            tickets = ticketRepo.findAll(spec, PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "createdAt")))
                    .map(t -> new TicketHit(t.getId(), t.getTicketNumber(), t.getSubject(), t.getCustomer().getFullName(), t.getStatus()))
                    .getContent();
        }
        return new Result(customers, tickets);
    }
}
