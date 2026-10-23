package io.clientra.web;

import com.fasterxml.jackson.databind.JsonNode;
import io.clientra.entity.Customer;
import io.clientra.entity.Product;
import io.clientra.entity.User;
import io.clientra.entity.enums.CustomerStatus;
import io.clientra.entity.enums.Role;
import io.clientra.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TicketAndOrderIT extends AbstractIT {

    @Autowired ProductRepository productRepo;

    private JsonNode createTicket(String token, Customer c, String priority) throws Exception {
        String body = mvc.perform(post("/api/tickets").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId":%d,"subject":"Wrong shade delivered (3 rolls)","channel":"PHONE",
                                 "category":"PRODUCT_QUALITY","priority":"%s"}""".formatted(c.getId(), priority)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body);
    }

    @Test
    void getBoard_groupedByStatus() throws Exception {
        User agent = user(Role.SUPPORT_AGENT);
        Customer c = customer(agent, "board@rao.in");
        String token = bearer(agent);
        createTicket(token, c, "HIGH");
        mvc.perform(get("/api/tickets").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.columns", hasSize(4)))
                .andExpect(jsonPath("$.columns[0].status").value("OPEN"));
    }

    @Test
    void createTicket_autoAssignsAgentAndSetsSla() throws Exception {
        User agent = user(Role.SUPPORT_AGENT);
        Customer c = customer(agent, "sla@rao.in");
        JsonNode t = createTicket(bearer(agent), c, "HIGH");
        org.assertj.core.api.Assertions.assertThat(t.get("ticketNumber").asText()).startsWith("TKT-");
        org.assertj.core.api.Assertions.assertThat(t.get("assignee").isNull()).isFalse();
        java.time.Duration window = java.time.Duration.between(java.time.Instant.parse(t.get("createdAt").asText()),
                java.time.Instant.parse(t.get("slaDueAt").asText()));
        org.assertj.core.api.Assertions.assertThat(window.toMinutes()).isBetween(479L, 481L);
    }

    @Test
    void patchAssign_admin_200_butSupportAgent_403() throws Exception {
        User admin = user(Role.ADMIN);
        User agent = user(Role.SUPPORT_AGENT);
        Customer c = customer(admin, "assign@rao.in");
        long id = createTicket(bearer(admin), c, "LOW").get("id").asLong();
        String assign = "{\"assigneeId\":" + agent.getId() + "}";
        mvc.perform(patch("/api/tickets/{id}/assign", id).header("Authorization", bearer(agent)).contentType(MediaType.APPLICATION_JSON).content(assign))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/tickets/{id}/assign", id).header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON).content(assign))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignee.id").value(agent.getId()));
    }

    @Test
    void salesExec_cannotOpenTickets_andSupport_cannotOpenOrders() throws Exception {
        mvc.perform(get("/api/tickets").header("Authorization", bearer(user(Role.SALES_EXEC)))).andExpect(status().isForbidden());
        mvc.perform(get("/api/orders").header("Authorization", bearer(user(Role.SUPPORT_AGENT)))).andExpect(status().isForbidden());
    }

    @Test
    void orderLifecycle_markPaidUpdatesLtvAndConvertsLead() throws Exception {
        User sales = user(Role.SALES_EXEC);
        Customer c = customer(sales, "lead@vermafoods.in");
        c.setStatus(CustomerStatus.LEAD);
        c.setLifetimeValue(BigDecimal.ZERO);
        Product p = new Product();
        p.setSku("IT-BAS-25");
        p.setName("Basmati Rice 25kg");
        p.setUnitPrice(new BigDecimal("2400"));
        p = productRepo.save(p);
        String token = bearer(sales);

        String created = mvc.perform(post("/api/orders").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerId\":%d,\"items\":[{\"productId\":%d,\"quantity\":4}]}".formatted(c.getId(), p.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalAmount").value(9600))
                .andExpect(jsonPath("$.paymentStatus").value("PENDING"))
                .andReturn().getResponse().getContentAsString();
        long orderId = json.readTree(created).get("id").asLong();

        for (int i = 0; i < 2; i++) { // paying twice must not double count
            mvc.perform(patch("/api/orders/{id}/payment", orderId).header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"paymentStatus\":\"PAID\"}"))
                    .andExpect(status().isOk());
        }
        mvc.perform(get("/api/customers/{id}", c.getId()).header("Authorization", token))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.lifetimeValue").value(9600));
    }

    @Test
    void dashboardKpis_availableToAllRoles() throws Exception {
        mvc.perform(get("/api/dashboard/kpis").header("Authorization", bearer(user(Role.SUPPORT_AGENT))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.monthly", hasSize(6)));
    }

    @Test
    void reports_adminOnly() throws Exception {
        mvc.perform(get("/api/reports/summary").header("Authorization", bearer(user(Role.SALES_EXEC)))).andExpect(status().isForbidden());
        mvc.perform(get("/api/reports/summary").header("Authorization", bearer(user(Role.ADMIN))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.funnel", hasSize(5)));
    }
}
