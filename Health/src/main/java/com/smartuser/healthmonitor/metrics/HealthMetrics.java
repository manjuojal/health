package com.smartuser.healthmonitor.metrics;

import java.util.Optional;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import com.smartuser.healthmonitor.health.CompositeHealthIndicatorRegistrar;
import com.smartuser.healthmonitor.health.DatabaseHealthIndicator;
import com.smartuser.healthmonitor.health.ExternalApiHealthIndicator;
import com.smartuser.healthmonitor.health.LogHealthIndicator;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

/**
 * Exposes health metrics to Prometheus
 * Follows Spring Boot 3.5 auto-configuration patterns
 */
@Slf4j
@AutoConfiguration(after = CompositeHealthIndicatorRegistrar.class)
@ConditionalOnClass(MeterRegistry.class)
@ConditionalOnProperty(prefix = "health.monitor", name = "enabled", havingValue = "true", matchIfMissing = true)
public class HealthMetrics {

    private final MeterRegistry meterRegistry;
    private final Optional<DatabaseHealthIndicator> databaseIndicator;
    private final Optional<ExternalApiHealthIndicator> externalApiIndicator;
    private final Optional<LogHealthIndicator> logHealthIndicator;

    public HealthMetrics(
            MeterRegistry meterRegistry,
            Optional<DatabaseHealthIndicator> databaseIndicator,
            Optional<ExternalApiHealthIndicator> externalApiIndicator,
            Optional<LogHealthIndicator> logHealthIndicator) {
        this.meterRegistry = meterRegistry;
        this.databaseIndicator = databaseIndicator;
        this.externalApiIndicator = externalApiIndicator;
        this.logHealthIndicator = logHealthIndicator;
    }

    @PostConstruct
    public void registerMetrics() {
        // Database metric
        databaseIndicator.ifPresent(indicator -> 
            Gauge.builder("health.monitor.database.status", indicator, ind -> {
                Health health = ind.health();
                return health.getStatus() == Status.UP ? 1.0 : 0.0;
            })
            .description("Database health status (1=UP, 0=DOWN)")
            .register(meterRegistry)
        );

        // External API metric
        externalApiIndicator.ifPresent(indicator ->
            Gauge.builder("health.monitor.external.status", indicator, ind -> {
                Health health = ind.health();
                return health.getStatus() == Status.UP ? 1.0 : 0.0;
            })
            .description("External API health status (1=UP, 0=DOWN)")
            .register(meterRegistry)
        );

        // Log errors metric
        logHealthIndicator.ifPresent(indicator ->
            Gauge.builder("health.monitor.logs.errors", indicator, ind -> {
                Health health = ind.health();
                Object recentErrorsCount = health.getDetails().get("recentErrorsCount");
                return recentErrorsCount != null ? ((Number) recentErrorsCount).doubleValue() : 0.0;
            })
            .description("Number of recent error logs")
            .register(meterRegistry)
        );

        log.info("Health metrics registered with Prometheus");
    }
}

