package io.clientra.controller;

import io.clientra.dto.UserAdminDtos.CreateUserRequest;
import io.clientra.dto.UserAdminDtos.UpdateUserRequest;
import io.clientra.dto.UserDto;
import io.clientra.entity.enums.Role;
import io.clientra.security.CurrentUser;
import io.clientra.service.UserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Users")
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /** Staff list for owner / assignee dropdowns. Admins may include deactivated accounts. */
    @GetMapping
    public List<UserDto> list(@RequestParam(required = false) Role role,
                              @RequestParam(defaultValue = "false") boolean includeInactive) {
        return userService.list(role, includeInactive && CurrentUser.hasRole(Role.ADMIN));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public UserDto create(@Valid @RequestBody CreateUserRequest req) {
        return userService.create(req);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public UserDto update(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest req) {
        return userService.update(id, req, CurrentUser.require().id());
    }
}
