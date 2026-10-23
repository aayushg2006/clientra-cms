package io.clientra.repository;

import io.clientra.entity.ActivityLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface ActivityLogRepository extends JpaRepository<ActivityLog, Long>, JpaSpecificationExecutor<ActivityLog> {

    @EntityGraph(attributePaths = "user")
    List<ActivityLog> findTop20ByCustomerIdOrderByCreatedAtDesc(Long customerId);

    @EntityGraph(attributePaths = "user")
    List<ActivityLog> findByEntityTypeAndEntityIdOrderByCreatedAtAsc(String entityType, Long entityId);

    /** Lead -> Active conversions recorded by the status-change audit entries. */
    @org.springframework.data.jpa.repository.Query("""
            select count(distinct a.entityId) from ActivityLog a where a.entityType = 'CUSTOMER' and a.action = 'STATUS_CHANGED'
            and a.summary like 'Status changed from Lead to Active%' and a.createdAt >= :from and a.createdAt < :to
            """)
    long countLeadConversions(java.time.Instant from, java.time.Instant to);

    @Override
    @EntityGraph(attributePaths = "user")
    Page<ActivityLog> findAll(org.springframework.data.jpa.domain.Specification<ActivityLog> spec, Pageable pageable);
}
