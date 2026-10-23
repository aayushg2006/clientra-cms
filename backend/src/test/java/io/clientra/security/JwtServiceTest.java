package io.clientra.security;

import io.clientra.TestFixtures;
import io.clientra.entity.enums.Role;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private final AppUserPrincipal neha = new AppUserPrincipal(2L, "neha.sharma@clientra.in", "Neha Sharma", Role.ADMIN, null, true);

    @Test
    void accessToken_roundTripsUserAndRole() {
        JwtService jwt = new JwtService(TestFixtures.props(), TestFixtures.CLOCK);
        JwtService.TokenClaims claims = jwt.parse(jwt.createAccessToken(neha), JwtService.TYPE_ACCESS);
        assertThat(claims.userId()).isEqualTo(2L);
        assertThat(claims.role()).isEqualTo(Role.ADMIN);
        assertThat(claims.name()).isEqualTo("Neha Sharma");
        assertThat(jwt.accessTtlSeconds()).isEqualTo(900);
    }

    @Test
    void refreshToken_cannotBeUsedAsAccessToken() {
        JwtService jwt = new JwtService(TestFixtures.props(), TestFixtures.CLOCK);
        String refresh = jwt.createRefreshToken(neha);
        assertThatThrownBy(() -> jwt.parse(refresh, JwtService.TYPE_ACCESS)).isInstanceOf(JwtException.class);
        assertThat(jwt.parse(refresh, JwtService.TYPE_REFRESH).email()).isEqualTo("neha.sharma@clientra.in");
    }

    @Test
    void accessToken_expiresAfter15Minutes() {
        String token = new JwtService(TestFixtures.props(), TestFixtures.CLOCK).createAccessToken(neha);
        Clock later = Clock.fixed(TestFixtures.NOW.plus(Duration.ofMinutes(16)), ZoneOffset.UTC);
        assertThatThrownBy(() -> new JwtService(TestFixtures.props(), later).parse(token, JwtService.TYPE_ACCESS))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void tamperedToken_isRejected() {
        JwtService jwt = new JwtService(TestFixtures.props(), TestFixtures.CLOCK);
        String token = jwt.createAccessToken(neha);
        String tampered = token.substring(0, token.length() - 4) + "abcd";
        assertThatThrownBy(() -> jwt.parse(tampered, JwtService.TYPE_ACCESS)).isInstanceOf(JwtException.class);
    }
}
