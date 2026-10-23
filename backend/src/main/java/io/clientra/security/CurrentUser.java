package io.clientra.security;

import io.clientra.entity.enums.Role;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/** Static access to the authenticated staff member for services (audit, ownership rules). */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static Optional<AppUserPrincipal> get() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AppUserPrincipal p) {
            return Optional.of(p);
        }
        return Optional.empty();
    }

    public static Long id() {
        return get().map(AppUserPrincipal::id).orElse(null);
    }

    public static AppUserPrincipal require() {
        return get().orElseThrow(() -> new org.springframework.security.access.AccessDeniedException("Not authenticated"));
    }

    public static boolean hasRole(Role role) {
        return get().map(p -> p.role() == role).orElse(false);
    }
}
