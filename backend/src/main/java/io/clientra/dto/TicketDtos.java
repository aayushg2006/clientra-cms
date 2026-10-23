package io.clientra.dto;

import io.clientra.entity.ActivityLog;
import io.clientra.entity.SupportTicket;
import io.clientra.entity.TicketComment;
import io.clientra.entity.enums.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class TicketDtos {

    private TicketDtos() {
    }

    public record TicketSummary(Long id, String ticketNumber, String subject, Long customerId, String customerName,
                                TicketChannel channel, TicketCategory category, TicketPriority priority,
                                TicketStatus status, Instant slaDueAt, boolean slaBreached, boolean escalated,
                                Instant resolvedAt, Instant createdAt, UserDto.Ref assignee) {

        public static TicketSummary from(SupportTicket t) {
            return new TicketSummary(t.getId(), t.getTicketNumber(), t.getSubject(), t.getCustomer().getId(),
                    t.getCustomer().getFullName(), t.getChannel(), t.getCategory(), t.getPriority(), t.getStatus(),
                    t.getSlaDueAt(), t.isSlaBreached(), t.isEscalated(), t.getResolvedAt(), t.getCreatedAt(),
                    UserDto.Ref.of(t.getAssignee()));
        }
    }

    public record CommentDto(Long id, String body, boolean internal, boolean fromCustomer, UserDto.Ref author,
                             String authorName, Instant createdAt) {

        public static CommentDto from(TicketComment c) {
            String name = c.isFromCustomer() ? c.getTicket().getCustomer().getFullName()
                    : c.getAuthor() != null ? c.getAuthor().getFullName() : "System";
            return new CommentDto(c.getId(), c.getBody(), c.isInternal(), c.isFromCustomer(), UserDto.Ref.of(c.getAuthor()),
                    name, c.getCreatedAt());
        }
    }

    public record ActivityDto(Long id, String action, String summary, UserDto.Ref user, Instant createdAt) {

        public static ActivityDto from(ActivityLog a) {
            return new ActivityDto(a.getId(), a.getAction(), a.getSummary(), UserDto.Ref.of(a.getUser()), a.getCreatedAt());
        }
    }

    public record TicketDetail(Long id, String ticketNumber, String subject, Long customerId, String customerName,
                               String customerEmail, TicketChannel channel, TicketCategory category,
                               TicketPriority priority, TicketStatus status, Instant slaDueAt, boolean slaBreached,
                               boolean escalated, Instant resolvedAt, Instant createdAt, UserDto.Ref assignee,
                               String description, Long orderId, String orderNumber, Instant firstResponseAt,
                               List<CommentDto> comments, List<ActivityDto> activity) {
    }

    public record CreateTicketRequest(
            @NotNull(message = "Customer is required") Long customerId,
            Long orderId,
            @NotBlank(message = "Subject is required") @Size(max = 200) String subject,
            @Size(max = 4000) String description,
            @NotNull TicketChannel channel,
            @NotNull TicketCategory category,
            @NotNull TicketPriority priority,
            Long assigneeId) {
    }

    public record AssignRequest(@NotNull Long assigneeId) {
    }

    public record StatusRequest(@NotNull TicketStatus status) {
    }

    public record CommentRequest(@NotBlank(message = "Message is required") @Size(max = 4000) String body, boolean internal,
                                 boolean fromCustomer) {
    }

    public record BoardColumn(TicketStatus status, long count, List<TicketSummary> tickets) {
    }

    public record Board(List<BoardColumn> columns, long openCount, Long avgFirstResponseMinutes, Double slaCompliance) {
    }
}
