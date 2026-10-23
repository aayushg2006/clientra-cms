package io.clientra.repository;

import io.clientra.entity.Customer;
import io.clientra.entity.enums.CustomerStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long>, JpaSpecificationExecutor<Customer> {

    Optional<Customer> findByEmailIgnoreCase(String email);

    Optional<Customer> findFirstByPhoneAndDeletedAtIsNull(String phone);

    boolean existsByPhoneAndDeletedAtIsNull(String phone);

    /** Customer 360: profile with owner, addresses and tags in one round trip. */
    @EntityGraph(attributePaths = {"owner", "addresses", "tags"})
    @Query("select c from Customer c where c.id = :id and c.deletedAt is null")
    Optional<Customer> findProfileById(Long id);

    @Query("select c from Customer c where c.deletedAt is null")
    List<Customer> findAllActive();

    long countByDeletedAtIsNull();

    long countByStatusAndDeletedAtIsNull(CustomerStatus status);

    long countByDeletedAtIsNullAndCreatedAtBetween(Instant from, Instant to);

    long countByDeletedAtIsNullAndCreatedAtBefore(Instant before);

    @Query("select c.segment, count(c) from Customer c where c.deletedAt is null group by c.segment")
    List<Object[]> countBySegment();

    @Query("select c.owner.id, count(c) from Customer c where c.deletedAt is null and c.owner is not null group by c.owner.id")
    List<Object[]> countByOwner();
}
