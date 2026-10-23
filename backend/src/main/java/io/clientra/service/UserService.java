package io.clientra.service;

import io.clientra.dto.UserAdminDtos.*;
import io.clientra.dto.UserDto;
import io.clientra.entity.User;
import io.clientra.entity.enums.Role;
import io.clientra.exception.BusinessRuleException;
import io.clientra.exception.ResourceNotFoundException;
import io.clientra.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepo;
    private final PasswordEncoder encoder;
    private final AuditService audit;
    private final MailService mail;

    @Transactional(readOnly = true)
    public List<UserDto> list(Role role, boolean includeInactive) {
        List<User> users = role != null ? userRepo.findByRoleAndActiveTrueOrderByFullName(role)
                : includeInactive ? userRepo.findAll(org.springframework.data.domain.Sort.by("fullName"))
                : userRepo.findByActiveTrueOrderByFullName();
        return users.stream().map(UserDto::from).toList();
    }

    @Transactional(readOnly = true)
    public UserDto get(Long id) {
        return UserDto.from(find(id));
    }

    @Transactional
    public UserDto create(CreateUserRequest req) {
        if (userRepo.existsByEmailIgnoreCase(req.email().trim())) {
            throw new BusinessRuleException("EMAIL_TAKEN", "A user with this email already exists");
        }
        User u = new User();
        u.setFullName(req.fullName().trim());
        u.setEmail(req.email().trim().toLowerCase());
        u.setRole(req.role());
        u.setTitle(req.title());
        u.setPhone(blankToNull(req.phone()));
        u.setPasswordHash(encoder.encode(req.password()));
        u = userRepo.save(u);
        audit.log("USER", u.getId(), null, "CREATED", "User " + u.getFullName() + " created as " + u.getRole(),
                Map.of("email", u.getEmail(), "role", u.getRole()));
        mail.send(u.getEmail(), "Welcome to Clientra CMS", "Your account is ready",
                "<p>Hi " + u.getFullName() + ",</p><p>An administrator created a Clientra account for you with the role <b>"
                        + u.getRole() + "</b>. Sign in with this email address and the temporary password shared with you, "
                        + "then change it from Settings.</p>");
        return UserDto.from(u);
    }

    @Transactional
    public UserDto update(Long id, UpdateUserRequest req, Long actingUserId) {
        User u = find(id);
        if (id.equals(actingUserId) && (!req.active() || req.role() != u.getRole())) {
            throw new BusinessRuleException("You cannot deactivate yourself or change your own role");
        }
        u.setFullName(req.fullName().trim());
        u.setRole(req.role());
        u.setTitle(req.title());
        u.setPhone(blankToNull(req.phone()));
        u.setActive(req.active());
        audit.log("USER", u.getId(), null, "UPDATED", "User " + u.getFullName() + " updated",
                Map.of("role", u.getRole(), "active", u.isActive()));
        return UserDto.from(u);
    }

    @Transactional
    public UserDto updateProfile(Long id, UpdateProfileRequest req) {
        User u = find(id);
        u.setFullName(req.fullName().trim());
        u.setTitle(req.title());
        u.setPhone(blankToNull(req.phone()));
        audit.log("USER", u.getId(), null, "PROFILE_UPDATED", u.getFullName() + " updated their profile");
        return UserDto.from(u);
    }

    private User find(Long id) {
        return userRepo.findById(id).orElseThrow(() -> new ResourceNotFoundException("User", id));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
