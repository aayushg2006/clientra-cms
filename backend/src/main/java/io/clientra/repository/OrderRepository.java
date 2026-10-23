package io.clientra.repository;

import io.clientra.entity.Order;
import io.clientra.entity.enums.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long>, JpaSpecificationExecutor<Order> {

    @Override
    @EntityGraph(attributePaths = {"customer", "createdBy"})
    Page<Order> findAll(Specification<Order> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"customer", "createdBy", "items", "items.product"})
    @Query("select o from Order o where o.id = :id")
    Optional<Order> findDetailById(Long id);

    @EntityGraph(attributePaths = {"createdBy", "items"})
    List<Order> findTop20ByCustomerIdOrderByOrderDateDesc(Long customerId);

    List<Order> findByCustomerIdOrderByOrderDateDesc(Long customerId);

    long countByCustomerId(Long customerId);

    @Query("select coalesce(sum(o.totalAmount), 0) from Order o where o.paymentStatus = :status and o.orderDate >= :from and o.orderDate < :to")
    BigDecimal sumByPaymentStatusBetween(PaymentStatus status, Instant from, Instant to);

    @Query("select count(o) from Order o where o.orderDate >= :from and o.orderDate < :to")
    long countBetween(Instant from, Instant to);

    @Query("select coalesce(sum(o.totalAmount), 0) from Order o where o.paymentStatus = 'PAID' and o.paidAt >= :from and o.paidAt < :to")
    BigDecimal revenueBetween(Instant from, Instant to);

    @Query("select coalesce(avg(o.totalAmount), 0) from Order o where o.paymentStatus <> 'REFUNDED' and o.orderDate >= :from and o.orderDate < :to")
    BigDecimal averageOrderValueBetween(Instant from, Instant to);

    @Query("select o.paidAt from Order o where o.paymentStatus = 'PAID' and o.paidAt >= :from")
    List<Instant> findPaidDatesSince(Instant from);

    @Query("select o.paidAt, o.totalAmount from Order o where o.paymentStatus = 'PAID' and o.paidAt >= :from")
    List<Object[]> findPaidAmountsSince(Instant from);

    /** customerId, paid order count, last paid date, paid total. Used by churn scoring. */
    @Query("""
            select o.customer.id, count(o), max(o.orderDate), coalesce(sum(o.totalAmount), 0)
            from Order o where o.paymentStatus = 'PAID' group by o.customer.id
            """)
    List<Object[]> paidStatsByCustomer();

    @Query("select count(o), max(o.orderDate) from Order o where o.customer.id = :customerId and o.paymentStatus = 'PAID'")
    List<Object[]> paidStatsForCustomer(Long customerId);

    @Query("select count(distinct o.customer.id) from Order o where o.paymentStatus = 'PAID'")
    long countCustomersWithPaidOrder();

    @Query("""
            select count(c) from Customer c where c.deletedAt is null and
            (select count(o) from Order o where o.customer = c and o.paymentStatus = 'PAID') >= 2
            """)
    long countRepeatCustomers();

    /** createdBy userId, closed (paid) orders, paid revenue — for executive performance. */
    @Query("""
            select o.createdBy.id, count(o), coalesce(sum(o.totalAmount), 0) from Order o
            where o.paymentStatus = 'PAID' and o.paidAt >= :from group by o.createdBy.id
            """)
    List<Object[]> paidStatsByUserSince(Instant from);

    @Query("select distinct o.customer.id from Order o where o.paymentStatus = 'PAID' and o.paidAt >= :from and o.paidAt < :to")
    List<Long> customersWithPaidOrderBetween(Instant from, Instant to);

    @Query("select distinct o.customer.id from Order o")
    List<Long> customersWithAnyOrder();

    @Query("select max(o.id) from Order o")
    Long findMaxId();
}
