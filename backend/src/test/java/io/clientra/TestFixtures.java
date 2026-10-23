package io.clientra;

import io.clientra.config.AppProperties;
import io.clientra.entity.Customer;
import io.clientra.entity.Product;
import io.clientra.entity.User;
import io.clientra.entity.enums.CustomerStatus;
import io.clientra.entity.enums.Role;
import io.clientra.security.AppUserPrincipal;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.*;
import java.util.List;

/** Shared builders for unit tests. */
public final class TestFixtures {

    public static final Instant NOW = Instant.parse("2026-10-12T05:30:00Z");
    public static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private TestFixtures() {
    }

    public static AppProperties props() {
        return new AppProperties(
                new AppProperties.Jwt("test-secret-test-secret-test-secret-test-secret-0123456789", Duration.ofMinutes(15), Duration.ofDays(7)),
                new AppProperties.Cors(List.of("http://localhost:5173")), "http://localhost:5173", ZoneId.of("Asia/Kolkata"),
                new AppProperties.Mail(false, "test@clientra.local"), new AppProperties.Seed(false),
                new AppProperties.Sla(8, 24, 48));
    }

    public static User user(long id, String name, Role role) {
        User u = new User();
        u.setId(id);
        u.setFullName(name);
        u.setEmail(name.toLowerCase().replace(' ', '.') + "@clientra.in");
        u.setRole(role);
        u.setPasswordHash("x");
        return u;
    }

    public static Customer customer(long id, CustomerStatus status) {
        Customer c = new Customer();
        c.setId(id);
        c.setFirstName("Rahul");
        c.setLastName("Verma");
        c.setEmail("rahul" + id + "@vermafoods.in");
        c.setStatus(status);
        c.setLifetimeValue(BigDecimal.ZERO);
        c.setCreatedAt(NOW.minus(Duration.ofDays(100)));
        return c;
    }

    public static Product product(long id, String name, String price, boolean active) {
        Product p = new Product();
        p.setId(id);
        p.setSku("SKU-" + id);
        p.setName(name);
        p.setUnitPrice(new BigDecimal(price));
        p.setGstRate(new BigDecimal("5"));
        p.setActive(active);
        return p;
    }

    public static void signIn(long id, Role role) {
        AppUserPrincipal p = new AppUserPrincipal(id, "user" + id + "@clientra.in", "User " + id, role, null, true);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(p, null, p.getAuthorities()));
    }

    public static void signOut() {
        SecurityContextHolder.clearContext();
    }
}
