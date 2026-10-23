package io.clientra.security;

import io.clientra.entity.User;
import io.clientra.entity.enums.Role;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/** Authenticated staff member as seen by Spring Security. */
public record AppUserPrincipal(Long id, String email, String fullName, Role role, String passwordHash, boolean active)
        implements UserDetails {

    public static AppUserPrincipal from(User u) {
        return new AppUserPrincipal(u.getId(), u.getEmail(), u.getFullName(), u.getRole(), u.getPasswordHash(), u.isActive());
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }
}
