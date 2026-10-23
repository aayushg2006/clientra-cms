package io.clientra.dto;

import io.clientra.entity.enums.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Admin user management (Settings > Users & Roles) and profile edits. */
public final class UserAdminDtos {

    private UserAdminDtos() {
    }

    public record CreateUserRequest(
            @NotBlank(message = "Name is required") @Size(max = 100) String fullName,
            @NotBlank @Email(message = "Enter a valid email") @Size(max = 150) String email,
            @NotNull(message = "Role is required") Role role,
            @Size(max = 80) String title,
            @Pattern(regexp = "^$|^[0-9+ ]{7,15}$", message = "Enter a valid phone number") String phone,
            @NotBlank @Size(min = 8, max = 72, message = "Password must be 8-72 characters") String password) {
    }

    public record UpdateUserRequest(
            @NotBlank @Size(max = 100) String fullName,
            @NotNull Role role,
            @Size(max = 80) String title,
            @Pattern(regexp = "^$|^[0-9+ ]{7,15}$", message = "Enter a valid phone number") String phone,
            boolean active) {
    }

    public record UpdateProfileRequest(
            @NotBlank @Size(max = 100) String fullName,
            @Size(max = 80) String title,
            @Pattern(regexp = "^$|^[0-9+ ]{7,15}$", message = "Enter a valid phone number") String phone) {
    }
}
