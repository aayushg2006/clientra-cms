package io.clientra.service;

import io.clientra.TestFixtures;
import io.clientra.dto.TicketDtos.CreateTicketRequest;
import io.clientra.dto.TicketDtos.TicketDetail;
import io.clientra.entity.Customer;
import io.clientra.entity.SupportTicket;
import io.clientra.entity.User;
import io.clientra.entity.enums.*;
import io.clientra.exception.BusinessRuleException;
import io.clientra.repository.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TicketServiceTest {

    @Mock SupportTicketRepository ticketRepo;
    @Mock TicketCommentRepository commentRepo;
    @Mock ActivityLogRepository logRepo;
    @Mock OrderRepository orderRepo;
    @Mock UserRepository userRepo;
    @Mock CustomerService customerService;
    @Mock NotificationService notifications;
    @Mock MailService mail;
    @Mock AuditService audit;
    TicketService service;

    final User ritu = TestFixtures.user(5, "Ritu Desai", Role.SUPPORT_AGENT);
    final User sana = TestFixtures.user(6, "Sana Qureshi", Role.SUPPORT_AGENT);

    @BeforeEach
    void setUp() {
        service = new TicketService(ticketRepo, commentRepo, logRepo, orderRepo, userRepo, customerService, notifications, mail,
                audit, TestFixtures.props(), TestFixtures.CLOCK);
        TestFixtures.signIn(5, Role.SUPPORT_AGENT);
    }

    @AfterEach
    void tearDown() {
        TestFixtures.signOut();
    }

    private void stubCreate(Customer c) {
        when(customerService.find(1L)).thenReturn(c);
        when(userRepo.getReferenceById(5L)).thenReturn(ritu);
        when(userRepo.findByRoleAndActiveTrueOrderByFullName(Role.SUPPORT_AGENT)).thenReturn(List.of(ritu, sana));
        when(ticketRepo.save(any())).thenAnswer(inv -> {
            SupportTicket t = inv.getArgument(0);
            t.setId(1193L);
            t.setCreatedAt(TestFixtures.NOW);
            return t;
        });
    }

    @Test
    void createTicket_setsSlaByPriorityAndAutoAssignsLeastLoadedAgent() {
        stubCreate(TestFixtures.customer(1, CustomerStatus.ACTIVE));
        // Ritu has 4 open tickets, Sana has 1 -> Sana gets the new one
        when(ticketRepo.openLoadByAgent()).thenReturn(List.of(new Object[]{5L, 4L}, new Object[]{6L, 1L}));

        TicketDetail t = service.create(new CreateTicketRequest(1L, null, "Wrong shade delivered", "3 rolls", TicketChannel.PHONE,
                TicketCategory.PRODUCT_QUALITY, TicketPriority.HIGH, null));

        assertThat(t.ticketNumber()).isEqualTo("TKT-1193");
        assertThat(t.slaDueAt()).isEqualTo(TestFixtures.NOW.plus(Duration.ofHours(8)));
        assertThat(t.assignee().name()).isEqualTo("Sana Qureshi");
        assertThat(t.comments()).hasSize(1);
        verify(notifications).notify(eq(sana), eq(NotificationType.TICKET_ASSIGNED), anyString(), anyString(), eq("/tickets/1193"), eq(true));
    }

    @Test
    void createTicket_lowPriority_gets48hSla() {
        stubCreate(TestFixtures.customer(1, CustomerStatus.ACTIVE));
        when(ticketRepo.openLoadByAgent()).thenReturn(List.of());

        TicketDetail t = service.create(new CreateTicketRequest(1L, null, "Invoice copy", null, TicketChannel.EMAIL,
                TicketCategory.BILLING, TicketPriority.LOW, null));

        assertThat(t.slaDueAt()).isEqualTo(TestFixtures.NOW.plus(Duration.ofHours(48)));
    }

    private SupportTicket openTicket(TicketStatus status) {
        SupportTicket t = new SupportTicket();
        t.setId(1187L);
        t.setTicketNumber("TKT-1187");
        t.setSubject("Wrong shade delivered");
        t.setCustomer(TestFixtures.customer(1, CustomerStatus.ACTIVE));
        t.setAssignee(ritu);
        t.setPriority(TicketPriority.MEDIUM);
        t.setStatus(status);
        t.setCreatedAt(TestFixtures.NOW.minus(Duration.ofHours(30)));
        t.setSlaDueAt(TestFixtures.NOW.minus(Duration.ofHours(6)));
        return t;
    }

    @Test
    void slaMonitor_flagsBreachedTicketsOnceAndAlertsAdmins() {
        SupportTicket t = openTicket(TicketStatus.IN_PROGRESS);
        when(ticketRepo.findByStatusInAndSlaDueAtBefore(any(), eq(TestFixtures.NOW))).thenReturn(List.of(t));

        assertThat(service.flagBreaches()).isEqualTo(1);
        assertThat(t.isSlaBreached()).isTrue();
        assertThat(service.flagBreaches()).isZero();
        verify(notifications, times(1)).notifyAdmins(eq(NotificationType.SLA_BREACH), contains("TKT-1187"), anyString(), anyString(), eq(true));
    }

    @Test
    void firstAgentReply_recordsFirstResponseAndMovesToInProgress() {
        SupportTicket t = openTicket(TicketStatus.OPEN);
        when(ticketRepo.findDetailById(1187L)).thenReturn(Optional.of(t));
        when(userRepo.getReferenceById(5L)).thenReturn(ritu);

        TicketDetail d = service.addComment(1187L, new io.clientra.dto.TicketDtos.CommentRequest("Replacement dispatched", false, false));

        assertThat(d.status()).isEqualTo(TicketStatus.IN_PROGRESS);
        assertThat(d.firstResponseAt()).isEqualTo(TestFixtures.NOW);
        verify(mail).send(eq(t.getCustomer().getEmail()), contains("TKT-1187"), anyString(), contains("Replacement dispatched"));
    }

    @Test
    void resolve_setsResolvedAtAndEmailsCustomer() {
        SupportTicket t = openTicket(TicketStatus.IN_PROGRESS);
        when(ticketRepo.findDetailById(1187L)).thenReturn(Optional.of(t));

        TicketDetail d = service.changeStatus(1187L, TicketStatus.RESOLVED);

        assertThat(d.resolvedAt()).isEqualTo(TestFixtures.NOW);
        verify(mail).send(anyString(), contains("Resolved"), anyString(), anyString());
    }

    @Test
    void escalate_raisesPriorityAndTightensSla() {
        SupportTicket t = openTicket(TicketStatus.OPEN);
        t.setSlaDueAt(TestFixtures.NOW.plus(Duration.ofHours(20)));
        when(ticketRepo.findDetailById(1187L)).thenReturn(Optional.of(t));

        TicketDetail d = service.escalate(1187L);

        assertThat(d.priority()).isEqualTo(TicketPriority.HIGH);
        assertThat(d.escalated()).isTrue();
        assertThat(d.slaDueAt()).isEqualTo(TestFixtures.NOW.plus(Duration.ofHours(8)));
        assertThatThrownBy(() -> service.escalate(1187L)).isInstanceOf(BusinessRuleException.class);
    }
}
