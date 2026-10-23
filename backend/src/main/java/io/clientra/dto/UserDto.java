package io.clientra.dto;

import io.clientra.entity.User;
import io.clientra.entity.enums.Role;

import java.time.Instant;

public record UserDto(Long id, String fullName, String email, Role role, String title, String phone,
                      String initials, boolean active, Instant lastLoginAt, Instant createdAt) {

    public static UserDto from(User u) {
        return new UserDto(u.getId(), u.getFullName(), u.getEmail(), u.getRole(), u.getTitle(), u.getPhone(),
                u.getInitials(), u.isActive(), u.getLastLoginAt(), u.getCreatedAt());
    }

    /** Compact reference used inside other DTOs (owner, assignee, author). */
    public record Ref(Long id, String name, String initials) {

        public static Ref of(User u) {
            return u == null ? null : new Ref(u.getId(), u.getFullName(), u.getInitials());
        }
    }
}
