package io.clientra.service;

import io.clientra.dto.AuthDtos.*;
import io.clientra.dto.UserDto;
import io.clientra.entity.PasswordResetToken;
import io.clientra.entity.User;
import io.clientra.exception.ApiException;
import io.clientra.exception.BusinessRuleException;
import io.clientra.repository.PasswordResetTokenRepository;
import io.clientra.repository.UserRepository;
import io.clientra.security.AppUserPrincipal;
import io.clientra.security.JwtService;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final Duration OTP_TTL = Duration.ofMinutes(10);
    private static final int OTP_MAX_ATTEMPTS = 5;

    private final AuthenticationManager authManager;
    private final JwtService jwtService;
    private final UserRepository userRepo;
    private final PasswordResetTokenRepository resetRepo;
    private final PasswordEncoder encoder;
    private final MailService mail;
    private final AuditService audit;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    @Transactional
    public AuthResponse login(LoginRequest req) {
        var auth = authManager.authenticate(new UsernamePasswordAuthenticationToken(req.email().trim(), req.password()));
        AppUserPrincipal principal = (AppUserPrincipal) auth.getPrincipal();
        User user = userRepo.getReferenceById(principal.id());
        user.setLastLoginAt(Instant.now(clock));
        return tokens(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse refresh(RefreshRequest req) {
        JwtService.TokenClaims claims;
        try {
            claims = jwtService.parse(req.refreshToken(), JwtService.TYPE_REFRESH);
        } catch (JwtException | IllegalArgumentException e) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN", "Session expired. Please sign in again.");
        }
        User user = userRepo.findById(claims.userId())
                .filter(User::isActive)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN", "Account is no longer active"));
        return tokens(user);
    }

    private AuthResponse tokens(User user) {
        AppUserPrincipal p = AppUserPrincipal.from(user);
        return new AuthResponse(jwtService.createAccessToken(p), jwtService.createRefreshToken(p),
                jwtService.accessTtlSeconds(), UserDto.from(user));
    }

    /** Always answers the same way so the endpoint cannot be used to discover which emails exist. */
    @Transactional
    public void forgotPassword(ForgotPasswordRequest req) {
        userRepo.findByEmailIgnoreCase(req.email().trim()).filter(User::isActive).ifPresent(user -> {
            resetRepo.invalidateAllForUser(user.getId());
            String otp = String.format("%06d", random.nextInt(1_000_000));
            PasswordResetToken token = new PasswordResetToken();
            token.setUser(user);
            token.setOtpHash(encoder.encode(otp));
            token.setExpiresAt(Instant.now(clock).plus(OTP_TTL));
            resetRepo.save(token);
            if (!mail.isEnabled()) {
                log.warn("Email is disabled - password reset code for {} is {}", user.getEmail(), otp);
            }
            mail.send(user.getEmail(), "Your Clientra password reset code", "Reset your password",
                    "<p>Hi " + user.getFullName() + ",</p><p>Use this code to reset your Clientra password. "
                            + "It expires in 10 minutes.</p><p style=\"font-size:28px;font-weight:700;letter-spacing:6px;"
                            + "color:#14776B\">" + otp + "</p><p>If you did not request this, you can ignore this email.</p>");
        });
    }

    @Transactional(noRollbackFor = BusinessRuleException.class)
    public void resetPassword(ResetPasswordRequest req) {
        User user = userRepo.findByEmailIgnoreCase(req.email().trim())
                .orElseThrow(() -> new BusinessRuleException("INVALID_OTP", "The code is invalid or has expired"));
        PasswordResetToken token = resetRepo.findTopByUserIdAndUsedFalseOrderByCreatedAtDesc(user.getId())
                .orElseThrow(() -> new BusinessRuleException("INVALID_OTP", "The code is invalid or has expired"));
        if (token.getExpiresAt().isBefore(Instant.now(clock)) || token.getAttempts() >= OTP_MAX_ATTEMPTS) {
            token.setUsed(true);
            throw new BusinessRuleException("INVALID_OTP", "The code is invalid or has expired");
        }
        if (!encoder.matches(req.otp(), token.getOtpHash())) {
            token.setAttempts(token.getAttempts() + 1);
            throw new BusinessRuleException("INVALID_OTP", "The code is incorrect");
        }
        token.setUsed(true);
        user.setPasswordHash(encoder.encode(req.newPassword()));
        audit.log("USER", user.getId(), null, "PASSWORD_RESET", user.getFullName() + " reset their password via email code");
    }

    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest req) {
        User user = userRepo.findById(userId).orElseThrow();
        if (!encoder.matches(req.currentPassword(), user.getPasswordHash())) {
            throw new BusinessRuleException("WRONG_PASSWORD", "Current password is incorrect");
        }
        user.setPasswordHash(encoder.encode(req.newPassword()));
        audit.log("USER", user.getId(), null, "PASSWORD_CHANGED", user.getFullName() + " changed their password");
    }
}
