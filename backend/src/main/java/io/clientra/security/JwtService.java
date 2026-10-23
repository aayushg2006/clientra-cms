package io.clientra.security;

import io.clientra.config.AppProperties;
import io.clientra.entity.enums.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

/** Issues and validates the stateless access (15 min) and refresh (7 days) tokens. */
@Service
public class JwtService {

    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_REFRESH = "refresh";

    private final SecretKey key;
    private final Duration accessTtl;
    private final Duration refreshTtl;
    private final Clock clock;

    public JwtService(AppProperties props, Clock clock) {
        String secret = props.jwt().secret();
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException("app.jwt.secret must be set (at least 32 characters) in application-local.yml");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTtl = props.jwt().accessTokenTtl();
        this.refreshTtl = props.jwt().refreshTokenTtl();
        this.clock = clock;
    }

    public String createAccessToken(AppUserPrincipal user) {
        return build(user, TYPE_ACCESS, accessTtl);
    }

    public String createRefreshToken(AppUserPrincipal user) {
        return build(user, TYPE_REFRESH, refreshTtl);
    }

    public long accessTtlSeconds() {
        return accessTtl.toSeconds();
    }

    private String build(AppUserPrincipal user, String type, Duration ttl) {
        Instant now = Instant.now(clock);
        return Jwts.builder()
                .subject(user.email())
                .claim("uid", user.id())
                .claim("role", user.role().name())
                .claim("name", user.fullName())
                .claim("typ", type)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(key)
                .compact();
    }

    /** Parses a token of the expected type; throws {@link JwtException} when invalid or expired. */
    public TokenClaims parse(String token, String expectedType) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .clock(() -> Date.from(Instant.now(clock)))
                .build()
                .parseSignedClaims(token)
                .getPayload();
        if (!expectedType.equals(claims.get("typ", String.class))) {
            throw new JwtException("Wrong token type");
        }
        return new TokenClaims(claims.get("uid", Long.class), claims.getSubject(),
                Role.valueOf(claims.get("role", String.class)), claims.get("name", String.class));
    }

    public record TokenClaims(Long userId, String email, Role role, String name) {
    }
}
