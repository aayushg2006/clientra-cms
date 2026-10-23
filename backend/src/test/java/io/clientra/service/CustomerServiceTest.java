package io.clientra.service;

import io.clientra.TestFixtures;
import io.clientra.dto.CustomerDtos.CustomerDto;
import io.clientra.dto.CustomerDtos.CustomerRequest;
import io.clientra.entity.Customer;
import io.clientra.entity.enums.CustomerStatus;
import io.clientra.entity.enums.CustomerType;
import io.clientra.entity.enums.Role;
import io.clientra.entity.enums.Segment;
import io.clientra.exception.BusinessRuleException;
import io.clientra.exception.DuplicateCustomerException;
import io.clientra.mapper.CustomerMapper;
import io.clientra.mapper.CustomerMapperImpl;
import io.clientra.repository.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

    @Mock CustomerRepository customerRepo;
    @Mock UserRepository userRepo;
    @Mock TagRepository tagRepo;
    @Mock InteractionRepository interactionRepo;
    @Mock OrderRepository orderRepo;
    @Mock SupportTicketRepository ticketRepo;
    @Mock ActivityLogRepository logRepo;
    @Spy CustomerMapper mapper = new CustomerMapperImpl();
    @Mock AuditService audit;
    @Mock MailService mail;
    @Mock ChurnRiskService churn;
    @Spy Clock clock = TestFixtures.CLOCK;
    @InjectMocks CustomerService service;

    @BeforeEach
    void signIn() {
        TestFixtures.signIn(3, Role.SALES_EXEC);
    }

    @AfterEach
    void signOut() {
        TestFixtures.signOut();
    }

    private CustomerRequest request(String email, String phone) {
        return new CustomerRequest("Rahul", "Verma", email, phone, "Verma Foods Pvt Ltd", null, CustomerType.BUSINESS,
                Segment.SMB, null, null, List.of(), List.of(), null, false, null);
    }

    @Test
    void createCustomer_generatesCodeAndStartsAsLead() {
        when(customerRepo.findByEmailIgnoreCase(any())).thenReturn(Optional.empty());
        when(customerRepo.findFirstByPhoneAndDeletedAtIsNull(any())).thenReturn(Optional.empty());
        when(userRepo.getReferenceById(3L)).thenReturn(TestFixtures.user(3, "Karan Mehta", Role.SALES_EXEC));
        when(customerRepo.save(any())).thenAnswer(inv -> {
            Customer c = inv.getArgument(0);
            c.setId(2847L);
            return c;
        });

        CustomerDto dto = service.create(request("Rahul@VermaFoods.in", "+91 99300 12000"));

        assertThat(dto.customerCode()).isEqualTo("CUST-02847");
        assertThat(dto.status()).isEqualTo(CustomerStatus.LEAD);
        assertThat(dto.email()).isEqualTo("rahul@vermafoods.in");
        assertThat(dto.phone()).isEqualTo("9930012000");
        assertThat(dto.owner().name()).isEqualTo("Karan Mehta");
        verify(audit).log(eq("CUSTOMER"), eq(2847L), eq(2847L), eq("CREATED"), anyString(), any());
    }

    @Test
    void createCustomer_duplicateEmail_throws409WithExistingRecord() {
        Customer existing = TestFixtures.customer(1422, CustomerStatus.ACTIVE);
        existing.setCustomerCode("CUST-01422");
        when(customerRepo.findByEmailIgnoreCase("rahul@vermafoods.in")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.create(request("rahul@vermafoods.in", null)))
                .isInstanceOf(DuplicateCustomerException.class)
                .satisfies(e -> {
                    DuplicateCustomerException d = (DuplicateCustomerException) e;
                    assertThat(d.getExistingId()).isEqualTo(1422L);
                    assertThat(d.getExistingCode()).isEqualTo("CUST-01422");
                    assertThat(d.getField()).isEqualTo("email");
                });
        verify(customerRepo, never()).save(any());
    }

    @Test
    void createCustomer_duplicatePhone_isDetectedAfterNormalising() {
        Customer existing = TestFixtures.customer(9, CustomerStatus.ACTIVE);
        when(customerRepo.findByEmailIgnoreCase(any())).thenReturn(Optional.empty());
        when(customerRepo.findFirstByPhoneAndDeletedAtIsNull("9930012000")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.create(request("new@vermafoods.in", "+91-99300-12000")))
                .isInstanceOf(DuplicateCustomerException.class)
                .hasMessageContaining("phone");
    }

    @Test
    void createBusinessCustomer_withoutCompany_isRejected() {
        when(customerRepo.findByEmailIgnoreCase(any())).thenReturn(Optional.empty());
        CustomerRequest req = new CustomerRequest("Asha", "Rao", "asha@x.in", null, " ", null, CustomerType.BUSINESS,
                null, null, null, null, null, null, false, null);
        assertThatThrownBy(() -> service.create(req)).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void changeStatus_leadToActive_isAudited() {
        Customer c = TestFixtures.customer(7, CustomerStatus.LEAD);
        when(customerRepo.findById(7L)).thenReturn(Optional.of(c));

        CustomerDto dto = service.changeStatus(7L, CustomerStatus.ACTIVE);

        assertThat(dto.status()).isEqualTo(CustomerStatus.ACTIVE);
        verify(audit).log(eq("CUSTOMER"), eq(7L), eq(7L), eq("STATUS_CHANGED"), contains("Lead to Active"), any());
    }

    @Test
    void softDelete_hidesCustomerAndFreesEmail() {
        Customer c = TestFixtures.customer(5, CustomerStatus.ACTIVE);
        when(customerRepo.findById(5L)).thenReturn(Optional.of(c));

        service.softDelete(5L);

        assertThat(c.getDeletedAt()).isEqualTo(TestFixtures.NOW);
        assertThat(c.getEmail()).startsWith("deleted+5+");
    }
}
