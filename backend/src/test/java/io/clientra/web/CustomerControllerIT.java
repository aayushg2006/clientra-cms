package io.clientra.web;

import io.clientra.entity.Customer;
import io.clientra.entity.User;
import io.clientra.entity.enums.Role;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CustomerControllerIT extends AbstractIT {

    private static final String NEW_CUSTOMER = """
            {"firstName":"Rahul","lastName":"Verma","email":"%s","phone":"9930012000","companyName":"Verma Foods Pvt Ltd",
             "customerType":"BUSINESS","segment":"SMB","source":"WEBSITE",
             "addresses":[{"addressType":"BILLING","line1":"Shop 7, Sector 17","city":"Navi Mumbai","postalCode":"400703","primary":true}],
             "tags":["Wholesale","Hot Lead"]}
            """;

    @Test
    void getCustomers_paginatedAndFilteredByStatus() throws Exception {
        User sales = user(Role.SALES_EXEC);
        customer(sales, "a1@rao.in");
        customer(sales, "a2@rao.in");
        mvc.perform(get("/api/customers").param("status", "ACTIVE").param("size", "1").header("Authorization", bearer(sales)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.totalElements", greaterThanOrEqualTo(2)));
    }

    @Test
    void postCustomer_salesExec_201WithCodeAndLeadStatus() throws Exception {
        User sales = user(Role.SALES_EXEC);
        mvc.perform(post("/api/customers").header("Authorization", bearer(sales)).contentType(MediaType.APPLICATION_JSON)
                        .content(NEW_CUSTOMER.formatted("rahul@vermafoods.in")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.customerCode", matchesPattern("CUST-\\d{5}")))
                .andExpect(jsonPath("$.status").value("LEAD"))
                .andExpect(jsonPath("$.tags[*].name", containsInAnyOrder("Hot Lead", "Wholesale")));
    }

    @Test
    void postCustomer_duplicateEmail_409WithExistingId() throws Exception {
        User sales = user(Role.SALES_EXEC);
        Customer existing = customer(sales, "dupe@vermafoods.in");
        mvc.perform(post("/api/customers").header("Authorization", bearer(sales)).contentType(MediaType.APPLICATION_JSON)
                        .content(NEW_CUSTOMER.formatted("DUPE@vermafoods.in")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("DUPLICATE_CUSTOMER"))
                .andExpect(jsonPath("$.existingId").value(existing.getId()));
    }

    @Test
    void postCustomer_invalidBody_400WithFieldErrors() throws Exception {
        User sales = user(Role.SALES_EXEC);
        mvc.perform(post("/api/customers").header("Authorization", bearer(sales)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"\",\"email\":\"bad\",\"customerType\":\"INDIVIDUAL\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.firstName").exists())
                .andExpect(jsonPath("$.fieldErrors.email").exists());
    }

    @Test
    void postCustomer_supportAgent_403() throws Exception {
        User agent = user(Role.SUPPORT_AGENT);
        mvc.perform(post("/api/customers").header("Authorization", bearer(agent)).contentType(MediaType.APPLICATION_JSON)
                        .content(NEW_CUSTOMER.formatted("x@vermafoods.in")))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteCustomer_salesExec_403() throws Exception {
        User sales = user(Role.SALES_EXEC);
        Customer c = customer(sales, "keep@rao.in");
        mvc.perform(delete("/api/customers/{id}", c.getId()).header("Authorization", bearer(sales)))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteCustomer_admin_softDeletesAndHidesFromSearch() throws Exception {
        User admin = user(Role.ADMIN);
        Customer c = customer(admin, "gone@rao.in");
        String token = bearer(admin);
        mvc.perform(delete("/api/customers/{id}", c.getId()).header("Authorization", token)).andExpect(status().isNoContent());
        mvc.perform(get("/api/customers/{id}", c.getId()).header("Authorization", token)).andExpect(status().isNotFound());
        mvc.perform(get("/api/customers").param("q", "gone@rao.in").header("Authorization", token))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void getProfile_returns360View() throws Exception {
        User sales = user(Role.SALES_EXEC);
        Customer c = customer(sales, "profile@rao.in");
        mvc.perform(get("/api/customers/{id}", c.getId()).header("Authorization", bearer(sales)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerCode").value(c.getCustomerCode()))
                .andExpect(jsonPath("$.owner.name").value(sales.getFullName()))
                .andExpect(jsonPath("$.churnRisk.level").exists())
                .andExpect(jsonPath("$.timeline").isArray());
    }

    @Test
    void checkDuplicate_findsExistingEmail() throws Exception {
        User sales = user(Role.SALES_EXEC);
        Customer c = customer(sales, "check@rao.in");
        mvc.perform(get("/api/customers/check-duplicate").param("email", "check@rao.in").header("Authorization", bearer(sales)))
                .andExpect(jsonPath("$.duplicate").value(true))
                .andExpect(jsonPath("$.existingCode").value(c.getCustomerCode()));
    }
}
