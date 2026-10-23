package io.clientra.entity;

import io.clientra.entity.enums.AddressType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "addresses")
@Getter
@Setter
@NoArgsConstructor
public class Address {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @Enumerated(EnumType.STRING)
    private AddressType addressType = AddressType.BILLING;

    @Column(nullable = false)
    private String line1;
    private String line2;
    private String city;
    private String state;
    private String postalCode;
    private String country = "India";

    @Column(name = "is_primary")
    private boolean primary;

    public String format() {
        StringBuilder sb = new StringBuilder(line1);
        if (line2 != null && !line2.isBlank()) sb.append(", ").append(line2);
        if (city != null && !city.isBlank()) sb.append(", ").append(city);
        if (state != null && !state.isBlank()) sb.append(", ").append(state);
        if (postalCode != null && !postalCode.isBlank()) sb.append(" ").append(postalCode);
        return sb.toString();
    }
}
