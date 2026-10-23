package io.clientra.repository;

import io.clientra.entity.SupportTicket;
import io.clientra.entity.enums.TicketStatus;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SupportTicketRepository extends JpaRepository<SupportTicket, Long>, JpaSpecificationExecutor<SupportTicket> {

    @Override
    @EntityGraph(attributePaths = {"customer", "assignee"})
    List<SupportTicket> findAll(Specification<SupportTicket> spec);

    @EntityGraph(attributePaths = {"customer", "assignee", "order", "createdBy"})
    @Query("select t from SupportTicket t where t.id = :id")
    Optional<SupportTicket> findDetailById(Long id);

    @EntityGraph(attributePaths = {"assignee"})
    List<SupportTicket> findTop20ByCustomerIdOrderByCreatedAtDesc(Long customerId);

    List<SupportTicket> findByStatusInAndSlaDueAtBefore(Collection<TicketStatus> statuses, Instant before);

    long countByStatusIn(Collection<TicketStatus> statuses);

    long countByCustomerIdAndStatusIn(Long customerId, Collection<TicketStatus> statuses);

    @Query("""
            select count(t) from SupportTicket t
            where t.status in :statuses and t.slaBreached = false and t.slaDueAt >= :now and t.slaDueAt < :soon
            """)
    long countNearBreach(Collection<TicketStatus> statuses, Instant now, Instant soon);

    /** Open-ticket load per agent, used for least-loaded auto-assignment. */
    @Query("""
            select t.assignee.id, count(t) from SupportTicket t
            where t.assignee is not null and t.status <> 'RESOLVED' group by t.assignee.id
            """)
    List<Object[]> openLoadByAgent();

    @Query("select t.customer.id, count(t) from SupportTicket t where t.status <> 'RESOLVED' group by t.customer.id")
    List<Object[]> openCountByCustomer();

    @Query("select t.category, count(t) from SupportTicket t where t.createdAt >= :from group by t.category")
    List<Object[]> countByCategorySince(Instant from);

    @Query("select t from SupportTicket t where t.status = 'RESOLVED' and t.resolvedAt >= :from")
    List<SupportTicket> findResolvedSince(Instant from);

    @Query("select t from SupportTicket t where t.firstResponseAt is not null and t.createdAt >= :from")
    List<SupportTicket> findRespondedSince(Instant from);

    @Query("select max(t.id) from SupportTicket t")
    Long findMaxId();
}
