package io.clientra.dto;

import io.clientra.entity.Order;
import io.clientra.entity.OrderItem;
import io.clientra.entity.enums.OrderStatus;
import io.clientra.entity.enums.PaymentStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class OrderDtos {

    private OrderDtos() {
    }

    public record OrderSummary(Long id, String orderNumber, Long customerId, String customerName, String customerCode,
                               Instant orderDate, OrderStatus status, PaymentStatus paymentStatus,
                               BigDecimal totalAmount, BigDecimal taxAmount, int itemCount, UserDto.Ref createdBy) {

        public static OrderSummary from(Order o) {
            return new OrderSummary(o.getId(), o.getOrderNumber(), o.getCustomer().getId(), o.getCustomer().getFullName(),
                    o.getCustomer().getCustomerCode(), o.getOrderDate(), o.getStatus(), o.getPaymentStatus(),
                    o.getTotalAmount(), o.getTaxAmount(), o.getItems().size(), UserDto.Ref.of(o.getCreatedBy()));
        }
    }

    public record OrderItemDto(Long id, Long productId, String productName, String sku, int quantity,
                               BigDecimal unitPrice, BigDecimal lineTotal) {

        public static OrderItemDto from(OrderItem i) {
            return new OrderItemDto(i.getId(), i.getProduct().getId(), i.getProduct().getName(), i.getProduct().getSku(),
                    i.getQuantity(), i.getUnitPrice(), i.getLineTotal());
        }
    }

    public record OrderDetail(Long id, String orderNumber, Long customerId, String customerName, String customerCode,
                              Instant orderDate, OrderStatus status, PaymentStatus paymentStatus, BigDecimal totalAmount,
                              BigDecimal taxAmount, int itemCount, UserDto.Ref createdBy, Instant paidAt,
                              List<OrderItemDto> items) {

        public static OrderDetail from(Order o) {
            return new OrderDetail(o.getId(), o.getOrderNumber(), o.getCustomer().getId(), o.getCustomer().getFullName(),
                    o.getCustomer().getCustomerCode(), o.getOrderDate(), o.getStatus(), o.getPaymentStatus(),
                    o.getTotalAmount(), o.getTaxAmount(), o.getItems().size(), UserDto.Ref.of(o.getCreatedBy()),
                    o.getPaidAt(), o.getItems().stream().map(OrderItemDto::from).toList());
        }
    }

    public record OrderLineRequest(
            @NotNull(message = "Product is required") Long productId,
            @Min(value = 1, message = "Quantity must be at least 1") @Max(10000) int quantity) {
    }

    public record CreateOrderRequest(
            @NotNull(message = "Customer is required") Long customerId,
            @NotEmpty(message = "Add at least one product") @Valid List<OrderLineRequest> items,
            PaymentStatus paymentStatus) {
    }

    public record PaymentRequest(@NotNull PaymentStatus paymentStatus) {
    }

    public record StatusRequest(@NotNull OrderStatus status) {
    }

    public record OrderKpis(BigDecimal paid, BigDecimal pending, BigDecimal averageOrderValue,
                            long ordersThisQuarter, BigDecimal revenueThisQuarter) {
    }
}
