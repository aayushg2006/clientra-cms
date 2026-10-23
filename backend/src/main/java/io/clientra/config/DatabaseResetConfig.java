package io.clientra.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Opt-in "reset demo data": start once with --app.reset-db=true --spring.flyway.clean-disabled=false
 * to drop all tables, re-run the migrations and let the seeder load fresh demo data.
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "app.reset-db", havingValue = "true")
public class DatabaseResetConfig {

    @Bean
    public FlywayMigrationStrategy cleanThenMigrate() {
        return flyway -> {
            log.warn("app.reset-db=true: dropping and recreating the clientra schema");
            flyway.clean();
            flyway.migrate();
        };
    }
}
