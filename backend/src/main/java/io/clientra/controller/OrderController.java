package io.clientra.controller;

import io.clientra.dto.OrderDtos.*;
import io.clientra.entity.enums.OrderStatus;
import io.clientra.entity.enums.PaymentStatus;
import io.clientra.service.OrderService;
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

@Tag(name = "Orders")
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService service;

    @GetMapping
    public Page<OrderSummary> list(@RequestParam(required = false) String q,
                                   @RequestParam(required = false) PaymentStatus paymentStatus,
                                   @RequestParam(required = false) OrderStatus status,
                                   @RequestParam(required = false) Long customerId,
                                   @PageableDefault(size = 20, sort = "orderDate", direction = Sort.Direction.DESC) Pageable pageable) {
        return service.list(q, paymentStatus, status, customerId, pageable);
    }

    @GetMapping("/kpis")
    public OrderKpis kpis() {
        return service.kpis();
    }

    @GetMapping("/{id}")
    public OrderDetail get(@PathVariable Long id) {
        return service.get(id);
    }

    @Operation(summary = "Create order with items")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderDetail create(@Valid @RequestBody CreateOrderRequest req) {
        return service.create(req);
    }

    @Operation(summary = "Mark paid (updates lifetime value) or refunded")
    @PatchMapping("/{id}/payment")
    public OrderDetail payment(@PathVariable Long id, @Valid @RequestBody PaymentRequest req) {
        return service.changePayment(id, req.paymentStatus());
    }

    @Operation(summary = "Move order through Confirmed -> Shipped -> Delivered, or cancel")
    @PatchMapping("/{id}/status")
    public OrderDetail status(@PathVariable Long id, @Valid @RequestBody StatusRequest req) {
        return service.changeStatus(id, req.status());
    }
}
