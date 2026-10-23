package io.clientra.repository.spec;

import io.clientra.entity.Customer;
import io.clientra.entity.Tag;
import io.clientra.entity.enums.CustomerStatus;
import io.clientra.entity.enums.Segment;
import jakarta.persistence.criteria.Join;
import org.springframework.data.jpa.domain.Specification;

/** Dynamic filters for the customer list (Spring Data Specifications). */
public final class CustomerSpecs {

    private CustomerSpecs() {
    }

    public static Specification<Customer> notDeleted() {
        return (root, q, cb) -> cb.isNull(root.get("deletedAt"));
    }

    public static Specification<Customer> status(CustomerStatus status) {
        return status == null ? null : (root, q, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<Customer> segment(Segment segment) {
        return segment == null ? null : (root, q, cb) -> cb.equal(root.get("segment"), segment);
    }

    public static Specification<Customer> owner(Long ownerId) {
        return ownerId == null ? null : (root, q, cb) -> cb.equal(root.get("owner").get("id"), ownerId);
    }

    public static Specification<Customer> tag(Long tagId) {
        return tagId == null ? null : (root, q, cb) -> {
            q.distinct(true);
            Join<Customer, Tag> tags = root.join("tags");
            return cb.equal(tags.get("id"), tagId);
        };
    }

    /** Free-text match on name, email, phone, company and customer code. */
    public static Specification<Customer> text(String text) {
        if (text == null || text.isBlank()) return null;
        String like = "%" + text.trim().toLowerCase() + "%";
        String digits = text.replaceAll("[^0-9]", "");
        return (root, q, cb) -> cb.or(
                cb.like(cb.lower(cb.concat(cb.concat(root.get("firstName"), " "), cb.coalesce(root.get("lastName"), ""))), like),
                cb.like(cb.lower(root.get("email")), like),
                cb.like(cb.lower(cb.coalesce(root.get("companyName"), "")), like),
                cb.like(cb.lower(cb.coalesce(root.get("customerCode"), "")), like),
                digits.length() >= 4 ? cb.like(cb.coalesce(root.get("phone"), ""), "%" + digits + "%") : cb.disjunction());
    }
}
