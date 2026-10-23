package io.clientra.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.time.ZoneId;
import java.util.List;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        Jwt jwt,
        Cors cors,
        String frontendUrl,
        ZoneId timezone,
        Mail mail,
        Seed seed,
        Sla sla) {

    public record Jwt(String secret, Duration accessTokenTtl, Duration refreshTokenTtl) {
    }

    public record Cors(List<String> allowedOrigins) {
    }

    public record Mail(boolean enabled, String from) {
    }

    public record Seed(boolean enabled) {
    }

    public record Sla(int highHours, int mediumHours, int lowHours) {
    }
}
