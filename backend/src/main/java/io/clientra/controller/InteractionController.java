package io.clientra.controller;

import io.clientra.dto.InteractionDtos.InteractionDto;
import io.clientra.dto.InteractionDtos.InteractionRequest;
import io.clientra.entity.enums.InteractionType;
import io.clientra.service.InteractionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Interactions")
@RestController
@RequestMapping("/api/interactions")
@RequiredArgsConstructor
public class InteractionController {

    private final InteractionService service;

    @GetMapping
    public Page<InteractionDto> list(@RequestParam(required = false) Long customerId,
                                     @RequestParam(required = false) InteractionType type,
                                     @RequestParam(defaultValue = "false") boolean mine,
                                     @RequestParam(required = false) String q,
                                     @PageableDefault(size = 20, sort = "interactionAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return service.list(customerId, type, mine, q, pageable);
    }

    @Operation(summary = "Pending follow-ups: overdue, today and next 7 days")
    @GetMapping("/follow-ups")
    public InteractionService.FollowUps followUps() {
        return service.followUps();
    }

    @Operation(summary = "Log a call / email / meeting / note")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InteractionDto create(@Valid @RequestBody InteractionRequest req) {
        return service.create(req);
    }

    @PatchMapping("/{id}/follow-up-done")
    public InteractionDto completeFollowUp(@PathVariable Long id) {
        return service.completeFollowUp(id);
    }
}
