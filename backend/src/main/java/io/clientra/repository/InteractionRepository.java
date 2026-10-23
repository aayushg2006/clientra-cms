package io.clientra.repository;

import io.clientra.entity.Interaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface InteractionRepository extends JpaRepository<Interaction, Long>, JpaSpecificationExecutor<Interaction> {

    @EntityGraph(attributePaths = "user")
    List<Interaction> findTop20ByCustomerIdOrderByInteractionAtDesc(Long customerId);

    @EntityGraph(attributePaths = "user")
    List<Interaction> findByCustomerIdAndTypeOrderByInteractionAtDesc(Long customerId, io.clientra.entity.enums.InteractionType type);

    @Override
    @EntityGraph(attributePaths = {"user", "customer"})
    Page<Interaction> findAll(Specification<Interaction> spec, Pageable pageable);

    /** Pending follow-ups in a time window, optionally limited to one staff member. */
    @EntityGraph(attributePaths = {"user", "customer"})
    @Query("""
            select i from Interaction i
            where i.followUpDone = false and i.followUpAt >= :from and i.followUpAt < :to
              and (:userId is null or i.user.id = :userId)
              and i.customer.deletedAt is null
            order by i.followUpAt asc
            """)
    List<Interaction> findFollowUpsBetween(Instant from, Instant to, Long userId);

    @EntityGraph(attributePaths = {"user", "customer"})
    @Query("""
            select i from Interaction i
            where i.followUpDone = false and i.followUpAt < :before
              and (:userId is null or i.user.id = :userId)
              and i.customer.deletedAt is null
            order by i.followUpAt asc
            """)
    List<Interaction> findOverdueFollowUps(Instant before, Long userId);

    @Query("select max(i.interactionAt) from Interaction i where i.customer.id = :customerId")
    Instant findLastInteractionAt(Long customerId);

    @Query("select i.customer.id, max(i.interactionAt) from Interaction i where i.customer.id in :ids group by i.customer.id")
    List<Object[]> findLastInteractionAt(Collection<Long> ids);

    @Query("select i.user.id, count(i) from Interaction i where i.interactionAt >= :from group by i.user.id")
    List<Object[]> countByUserSince(Instant from);

    long countByCustomerIdAndType(Long customerId, io.clientra.entity.enums.InteractionType type);
}
