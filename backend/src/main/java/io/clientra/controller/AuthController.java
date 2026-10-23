package io.clientra.controller;

import io.clientra.dto.AuthDtos.*;
import io.clientra.dto.UserAdminDtos.UpdateProfileRequest;
import io.clientra.dto.UserDto;
import io.clientra.security.CurrentUser;
import io.clientra.service.AuthService;
import io.clientra.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Auth")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final UserService userService;

    @Operation(summary = "Login and receive access + refresh tokens")
    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest req) {
        return authService.login(req);
    }

    @Operation(summary = "Exchange a refresh token for a new access token")
    @PostMapping("/refresh")
    public AuthResponse refresh(@Valid @RequestBody RefreshRequest req) {
        return authService.refresh(req);
    }

    @Operation(summary = "Email a 6-digit password reset code")
    @PostMapping("/forgot-password")
    public MessageResponse forgotPassword(@Valid @RequestBody ForgotPasswordRequest req) {
        authService.forgotPassword(req);
        return new MessageResponse("If an account exists for this email, a reset code has been sent.");
    }

    @Operation(summary = "Reset password with the emailed code")
    @PostMapping("/reset-password")
    public MessageResponse resetPassword(@Valid @RequestBody ResetPasswordRequest req) {
        authService.resetPassword(req);
        return new MessageResponse("Password updated. You can now sign in.");
    }

    @Operation(summary = "Current user profile")
    @GetMapping("/me")
    public UserDto me() {
        return userService.get(CurrentUser.require().id());
    }

    @PutMapping("/me")
    public UserDto updateMe(@Valid @RequestBody UpdateProfileRequest req) {
        return userService.updateProfile(CurrentUser.require().id(), req);
    }

    @PostMapping("/change-password")
    public MessageResponse changePassword(@Valid @RequestBody ChangePasswordRequest req) {
        authService.changePassword(CurrentUser.require().id(), req);
        return new MessageResponse("Password changed");
    }
}
