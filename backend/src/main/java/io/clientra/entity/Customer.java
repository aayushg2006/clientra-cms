package io.clientra.entity;

import io.clientra.entity.enums.CustomerStatus;
import io.clientra.entity.enums.CustomerType;
import io.clientra.entity.enums.Segment;
import io.clientra.entity.enums.Source;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "customers")
@Getter
@Setter
@NoArgsConstructor
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String customerCode;

    @Column(nullable = false)
    private String firstName;

    private String lastName;

    @Column(unique = true, nullable = false)
    private String email;

    private String phone;
    private String companyName;
    private String jobTitle;

    @Enumerated(EnumType.STRING)
    private CustomerType customerType = CustomerType.INDIVIDUAL;

    @Enumerated(EnumType.STRING)
    private Segment segment = Segment.RETAIL;

    @Enumerated(EnumType.STRING)
    private CustomerStatus status = CustomerStatus.LEAD;

    @Enumerated(EnumType.STRING)
    private Source source;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_to")
    private User owner;

    private BigDecimal lifetimeValue = BigDecimal.ZERO;

    private Integer churnScore;

    private String notes;

    @Version
    private Long version;

    private Instant deletedAt;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

    @OneToMany(mappedBy = "customer", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Address> addresses = new ArrayList<>();

    @ManyToMany
    @JoinTable(name = "customer_tags",
            joinColumns = @JoinColumn(name = "customer_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id"))
    private Set<Tag> tags = new HashSet<>();

    public String getFullName() {
        return lastName == null || lastName.isBlank() ? firstName : firstName + " " + lastName;
    }

    public void addAddress(Address address) {
        address.setCustomer(this);
        addresses.add(address);
    }
}
