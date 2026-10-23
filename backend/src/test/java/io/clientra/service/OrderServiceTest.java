package io.clientra.service;

import io.clientra.TestFixtures;
import io.clientra.dto.CustomerDtos.ChurnRiskDto;
import io.clientra.dto.OrderDtos.CreateOrderRequest;
import io.clientra.dto.OrderDtos.OrderDetail;
import io.clientra.dto.OrderDtos.OrderLineRequest;
import io.clientra.entity.Customer;
import io.clientra.entity.Order;
import io.clientra.entity.enums.*;
import io.clientra.exception.BusinessRuleException;
import io.clientra.repository.OrderRepository;
import io.clientra.repository.ProductRepository;
import io.clientra.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock OrderRepository orderRepo;
    @Mock ProductRepository productRepo;
    @Mock UserRepository userRepo;
    @Mock CustomerService customerService;
    @Mock ChurnRiskService churn;
    @Mock NotificationService notifications;
    @Mock AuditService audit;
    OrderService service;

    @BeforeEach
    void setUp() {
        service = new OrderService(orderRepo, productRepo, userRepo, customerService, churn, notifications, audit,
                TestFixtures.props(), TestFixtures.CLOCK);
        TestFixtures.signIn(3, Role.SALES_EXEC);
    }

    @AfterEach
    void tearDown() {
        TestFixtures.signOut();
    }

    private Order pendingOrder(Customer c, String total) {
        Order o = new Order();
        o.setId(931L);
        o.setOrderNumber("ORD-2026-0931");
        o.setCustomer(c);
        o.setCreatedBy(TestFixtures.user(3, "Karan Mehta", Role.SALES_EXEC));
        o.setOrderDate(TestFixtures.NOW);
        o.setTotalAmount(new BigDecimal(total));
        return o;
    }

    private void stubChurn() {
        when(churn.scoreFor(any())).thenReturn(new ChurnRiskDto(5, "LOW", 0L, 1, BigDecimal.ONE, 0, List.of()));
    }

    @Test
    void createOrder_calculatesLineTotalsMergesLinesAndIncludedGst() {
        Customer c = TestFixtures.customer(7, CustomerStatus.LEAD);
        when(customerService.find(7L)).thenReturn(c);
        when(productRepo.findById(1L)).thenReturn(Optional.of(TestFixtures.product(1, "Basmati Rice 25kg", "2400", true)));
        when(productRepo.findById(2L)).thenReturn(Optional.of(TestFixtures.product(2, "Sunflower Oil 15L", "2250", true)));
        when(userRepo.getReferenceById(3L)).thenReturn(TestFixtures.user(3, "Karan Mehta", Role.SALES_EXEC));
        when(orderRepo.save(any())).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setId(931L);
            return o;
        });

        OrderDetail o = service.create(new CreateOrderRequest(7L, List.of(
                new OrderLineRequest(1L, 3), new OrderLineRequest(2L, 5), new OrderLineRequest(1L, 1)), PaymentStatus.PENDING));

        assertThat(o.orderNumber()).isEqualTo("ORD-2026-0931");
        assertThat(o.items()).hasSize(2);
        assertThat(o.items().get(0).quantity()).isEqualTo(4);
        assertThat(o.totalAmount()).isEqualByComparingTo("20850"); // 4 x 2400 + 5 x 2250
        assertThat(o.taxAmount()).isEqualByComparingTo("992.85");  // 5% GST included in the price, rounded per line
        assertThat(c.getLifetimeValue()).isEqualByComparingTo("0"); // pending orders do not count
    }

    @Test
    void createOrder_inactiveProduct_isRejected() {
        when(customerService.find(7L)).thenReturn(TestFixtures.customer(7, CustomerStatus.ACTIVE));
        when(userRepo.getReferenceById(3L)).thenReturn(TestFixtures.user(3, "Karan Mehta", Role.SALES_EXEC));
        when(productRepo.findById(9L)).thenReturn(Optional.of(TestFixtures.product(9, "Groundnut Oil", "980", false)));

        assertThatThrownBy(() -> service.create(new CreateOrderRequest(7L, List.of(new OrderLineRequest(9L, 1)), null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("no longer available");
        verify(orderRepo, never()).save(any());
    }

    @Test
    void markPaid_updatesLifetimeValueAndActivatesLead() {
        Customer c = TestFixtures.customer(7, CustomerStatus.LEAD);
        c.setLifetimeValue(new BigDecimal("1000"));
        Order o = pendingOrder(c, "24600");
        when(orderRepo.findDetailById(931L)).thenReturn(Optional.of(o));
        stubChurn();

        OrderDetail paid = service.changePayment(931L, PaymentStatus.PAID);

        assertThat(paid.paymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(paid.paidAt()).isEqualTo(TestFixtures.NOW);
        assertThat(c.getLifetimeValue()).isEqualByComparingTo("25600");
        assertThat(c.getStatus()).isEqualTo(CustomerStatus.ACTIVE);
    }

    @Test
    void markPaid_isIdempotent() {
        Customer c = TestFixtures.customer(7, CustomerStatus.LEAD);
        Order o = pendingOrder(c, "24600");
        when(orderRepo.findDetailById(931L)).thenReturn(Optional.of(o));
        stubChurn();

        service.changePayment(931L, PaymentStatus.PAID);
        service.changePayment(931L, PaymentStatus.PAID);

        assertThat(c.getLifetimeValue()).isEqualByComparingTo("24600");
        verify(churn, times(1)).scoreFor(c);
    }

    @Test
    void refund_reversesLifetimeValueAndCancelsOrder() {
        Customer c = TestFixtures.customer(7, CustomerStatus.ACTIVE);
        c.setLifetimeValue(new BigDecimal("30000"));
        Order o = pendingOrder(c, "24600");
        o.setPaymentStatus(PaymentStatus.PAID);
        when(orderRepo.findDetailById(931L)).thenReturn(Optional.of(o));

        OrderDetail refunded = service.changePayment(931L, PaymentStatus.REFUNDED);

        assertThat(refunded.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(c.getLifetimeValue()).isEqualByComparingTo("5400");
    }

    @Test
    void refund_ofPendingOrder_isRejected() {
        Order o = pendingOrder(TestFixtures.customer(7, CustomerStatus.ACTIVE), "100");
        when(orderRepo.findDetailById(931L)).thenReturn(Optional.of(o));
        assertThatThrownBy(() -> service.changePayment(931L, PaymentStatus.REFUNDED)).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void cancellingPaidOrder_requiresRefundFirst() {
        Order o = pendingOrder(TestFixtures.customer(7, CustomerStatus.ACTIVE), "100");
        o.setPaymentStatus(PaymentStatus.PAID);
        when(orderRepo.findDetailById(931L)).thenReturn(Optional.of(o));
        assertThatThrownBy(() -> service.changeStatus(931L, OrderStatus.CANCELLED)).hasMessageContaining("Refund");
    }

    @Test
    void orderStatus_cannotGoBackwards() {
        Order o = pendingOrder(TestFixtures.customer(7, CustomerStatus.ACTIVE), "100");
        o.setStatus(OrderStatus.DELIVERED);
        when(orderRepo.findDetailById(931L)).thenReturn(Optional.of(o));
        assertThatThrownBy(() -> service.changeStatus(931L, OrderStatus.SHIPPED)).isInstanceOf(BusinessRuleException.class);
    }
}
