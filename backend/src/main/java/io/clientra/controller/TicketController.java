package io.clientra.controller;

import io.clientra.dto.TicketDtos.*;
import io.clientra.entity.enums.TicketPriority;
import io.clientra.service.TicketService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "Support Tickets")
@RestController
@RequestMapping("/api/tickets")
@RequiredArgsConstructor
public class TicketController {

    private final TicketService service;

    @Operation(summary = "Ticket board grouped by status")
    @GetMapping
    public Board board(@RequestParam(required = false) String q,
                       @RequestParam(required = false) TicketPriority priority,
                       @RequestParam(defaultValue = "false") boolean mine) {
        return service.board(q, priority, mine);
    }

    @GetMapping("/open-count")
    public Map<String, Long> openCount() {
        return Map.of("count", service.openCount());
    }

    @GetMapping("/{id}")
    public TicketDetail get(@PathVariable Long id) {
        return service.get(id);
    }

    @Operation(summary = "Raise a ticket (SLA from priority, auto-assigned to the least-loaded agent)")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TicketDetail create(@Valid @RequestBody CreateTicketRequest req) {
        return service.create(req);
    }

    @Operation(summary = "Assign agent (Admin)")
    @PatchMapping("/{id}/assign")
    @PreAuthorize("hasRole('ADMIN')")
    public TicketDetail assign(@PathVariable Long id, @Valid @RequestBody AssignRequest req) {
        return service.assign(id, req.assigneeId());
    }

    @PatchMapping("/{id}/status")
    public TicketDetail status(@PathVariable Long id, @Valid @RequestBody StatusRequest req) {
        return service.changeStatus(id, req.status());
    }

    @Operation(summary = "Reply to customer, add internal note, or log a customer message")
    @PostMapping("/{id}/comments")
    public TicketDetail comment(@PathVariable Long id, @Valid @RequestBody CommentRequest req) {
        return service.addComment(id, req);
    }

    @PostMapping("/{id}/escalate")
    public TicketDetail escalate(@PathVariable Long id) {
        return service.escalate(id);
    }
}
