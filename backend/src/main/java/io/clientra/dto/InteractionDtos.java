package io.clientra.dto;

import io.clientra.entity.Interaction;
import io.clientra.entity.enums.InteractionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class InteractionDtos {

    private InteractionDtos() {
    }

    public record InteractionDto(Long id, Long customerId, String customerName, String companyName, InteractionType type,
                                 String subject, String notes, Instant interactionAt, Instant followUpAt,
                                 boolean followUpDone, UserDto.Ref user) {

        public static InteractionDto from(Interaction i) {
            return new InteractionDto(i.getId(), i.getCustomer().getId(), i.getCustomer().getFullName(),
                    i.getCustomer().getCompanyName(), i.getType(), i.getSubject(), i.getNotes(), i.getInteractionAt(),
                    i.getFollowUpAt(), i.isFollowUpDone(), UserDto.Ref.of(i.getUser()));
        }
    }

    public record InteractionRequest(
            @NotNull(message = "Customer is required") Long customerId,
            @NotNull(message = "Type is required") InteractionType type,
            @NotBlank(message = "Subject is required") @Size(max = 200) String subject,
            @Size(max = 4000) String notes,
            Instant interactionAt,
            Instant followUpAt) {
    }
}
