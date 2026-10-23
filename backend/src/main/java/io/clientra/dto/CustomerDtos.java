package io.clientra.dto;

import io.clientra.entity.enums.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Customer module DTOs (list, Customer 360 profile, create/update requests, CSV import report). */
public final class CustomerDtos {

    private CustomerDtos() {
    }

    public record TagDto(Long id, String name, String color) {
    }

    public record AddressDto(
            Long id,
            @NotNull AddressType addressType,
            @NotBlank(message = "Address line is required") @Size(max = 200) String line1,
            @Size(max = 200) String line2,
            @Size(max = 100) String city,
            @Size(max = 100) String state,
            @Pattern(regexp = "^$|^[0-9A-Za-z -]{3,10}$", message = "Enter a valid postal code") String postalCode,
            @Size(max = 60) String country,
            boolean primary) {
    }

    public record CustomerDto(
            Long id, String customerCode, String firstName, String lastName, String fullName, String email,
            String phone, String companyName, CustomerType customerType, Segment segment, CustomerStatus status,
            Source source, UserDto.Ref owner, BigDecimal lifetimeValue, Integer churnScore, List<TagDto> tags,
            Instant lastContactAt, Instant createdAt) {
    }

    public record CustomerRequest(
            @NotBlank(message = "First name is required") @Size(max = 60) String firstName,
            @Size(max = 60) String lastName,
            @NotBlank(message = "Email is required") @Email(message = "Enter a valid email") @Size(max = 150) String email,
            @Pattern(regexp = "^$|^\\+?[0-9 ]{10,16}$", message = "Enter a valid 10-digit phone number") String phone,
            @Size(max = 150) String companyName,
            @Size(max = 100) String jobTitle,
            @NotNull(message = "Choose individual or business") CustomerType customerType,
            Segment segment,
            Source source,
            Long assignedTo,
            @Valid List<AddressDto> addresses,
            List<@Size(max = 50) String> tags,
            @Size(max = 4000) String notes,
            boolean sendWelcomeEmail,
            /** Optimistic-lock version, required on update. */
            Long version) {
    }

    public record StatusRequest(@NotNull CustomerStatus status) {
    }

    public record NotesRequest(@Size(max = 4000) String notes) {
    }

    public record DuplicateCheck(boolean duplicate, String field, Long existingId, String existingCode, String existingName) {
    }

    public record CustomerStats(long total, long leads, long active, long inactive, long churned) {
    }

    public record TimelineEvent(String kind, String icon, String title, String actor, Instant at, String body,
                                List<Badge> badges, Instant followUpAt, Instant slaDueAt, String link) {
    }

    public record Badge(String label, String tone) {
    }

    public record ChurnRiskDto(int score, String level, Long recencyDays, long frequency, BigDecimal monetary,
                               long openTickets, List<String> reasons) {
    }

    public record Customer360(
            Long id, String customerCode, String firstName, String lastName, String fullName, String email,
            String phone, String companyName, String jobTitle, CustomerType customerType, Segment segment,
            CustomerStatus status, Source source, UserDto.Ref owner, BigDecimal lifetimeValue, Integer churnScore,
            List<TagDto> tags, String notes, Long version, Instant createdAt, Instant lastContactAt,
            List<AddressDto> addresses, long orderCount, long openTickets, long ticketCount, ChurnRiskDto churnRisk,
            List<OrderDtos.OrderSummary> recentOrders, List<TicketDtos.TicketSummary> recentTickets,
            List<InteractionDtos.InteractionDto> interactions, List<TimelineEvent> timeline) {
    }

    public record ImportError(int row, String email, String message) {
    }

    public record ImportResult(int totalRows, int imported, int skipped, List<ImportError> errors) {
    }

    public record CustomerLookup(Long id, String name, String customerCode, String companyName, String email, CustomerStatus status) {
    }
}
