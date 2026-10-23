package io.clientra.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.clientra.entity.Customer;
import io.clientra.entity.User;
import io.clientra.entity.enums.CustomerStatus;
import io.clientra.entity.enums.CustomerType;
import io.clientra.entity.enums.Role;
import io.clientra.entity.enums.Segment;
import io.clientra.repository.CustomerRepository;
import io.clientra.repository.UserRepository;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Integration tests: full Spring context + MockMvc against the local MySQL schema clientra_test.
 * The schema is rebuilt by Flyway once per run and every test rolls back its own data.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"local", "test"})
@Transactional
abstract class AbstractIT {

    static final String PASSWORD = "Test@12345";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository userRepo;
    @Autowired CustomerRepository customerRepo;
    @Autowired PasswordEncoder encoder;

    @TestConfiguration
    static class FreshSchema {
        @Bean
        FlywayMigrationStrategy cleanMigrate() {
            return (Flyway flyway) -> {
                flyway.clean();
                flyway.migrate();
            };
        }
    }

    User user(Role role) {
        User u = new User();
        String tag = UUID.randomUUID().toString().substring(0, 8);
        u.setFullName(role.name().charAt(0) + role.name().substring(1).toLowerCase() + " " + tag);
        u.setEmail(tag + "@it.clientra.in");
        u.setRole(role);
        u.setPasswordHash(encoder.encode(PASSWORD));
        return userRepo.save(u);
    }

    Customer customer(User owner, String email) {
        Customer c = new Customer();
        c.setFirstName("Arvind");
        c.setLastName("Rao");
        c.setEmail(email);
        c.setPhone(String.valueOf(9_000_000_000L + Math.abs(email.hashCode() % 999_999_999)));
        c.setCompanyName("Rao Textiles Pvt Ltd");
        c.setCustomerType(CustomerType.BUSINESS);
        c.setSegment(Segment.ENTERPRISE);
        c.setStatus(CustomerStatus.ACTIVE);
        c.setOwner(owner);
        c.setLifetimeValue(new BigDecimal("482000"));
        c = customerRepo.save(c);
        c.setCustomerCode(String.format("CUST-%05d", c.getId()));
        return c;
    }

    JsonNode login(String email, String password) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body);
    }

    String bearer(User u) throws Exception {
        return "Bearer " + login(u.getEmail(), PASSWORD).get("accessToken").asText();
    }
}
