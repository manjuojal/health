package com.smartuser.healthmonitor.health;

import javax.sql.DataSource;

import org.springframework.boot.actuate.autoconfigure.health.HealthContributorAutoConfiguration;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import com.smartuser.healthmonitor.HealthMonitorProperties;

import lombok.extern.slf4j.Slf4j;

/**
 * Auto-configures health indicators based on available dependencies
 * Follows Spring Boot 3.5 auto-configuration patterns
 */
@Slf4j
@AutoConfiguration(after = DataSourceAutoConfiguration.class)
@AutoConfigureBefore(HealthContributorAutoConfiguration.class)
@EnableConfigurationProperties(HealthMonitorProperties.class)
@ConditionalOnProperty(prefix = "health.monitor", name = "enabled", havingValue = "true", matchIfMissing = true)
public class CompositeHealthIndicatorRegistrar {

    private final HealthMonitorProperties properties;

    public CompositeHealthIndicatorRegistrar(HealthMonitorProperties properties) {
        this.properties = properties;
    }

    /**
     * Register custom database health indicator with detailed information
     * Spring Boot Actuator automatically discovers HealthIndicator beans and registers them
     * Bean name "dbHealthIndicator" maps to component "db" in /actuator/health
     * Using @Primary ensures our bean takes precedence over Spring Boot's default one
     * Spring Boot's default DataSourceHealthContributorAutoConfiguration can still run,
     * but because our bean is primary and registered earlier, actuator will use this indicator
     */
    @Bean(name = "dbHealthIndicator")
    @Primary
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnClass(DataSource.class)
    @ConditionalOnProperty(prefix = "health.monitor.database", name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnProperty(prefix = "health.monitor.database", name = "overrideDefaultIndicator", havingValue = "true", matchIfMissing = true)
    public HealthIndicator dbHealthIndicator(DataSource dataSource) {
        log.info("=== HEALTH MONITOR: Registering custom database health indicator with detailed information ===");
        log.info("Bean name: dbHealthIndicator -> component: db");
        log.info("This replaces Spring Boot's default DataSourceHealthIndicator");
        DatabaseHealthIndicator indicator = new DatabaseHealthIndicator(dataSource, properties);
        log.debug("Custom DatabaseHealthIndicator instance created: {}", indicator.getClass().getName());
        return indicator;
    }



    @Bean
    @ConditionalOnMissingBean(name = "externalApiHealthIndicator")
    @ConditionalOnProperty(prefix = "health.monitor.external", name = "enabled", havingValue = "true", matchIfMissing = true)
    public ExternalApiHealthIndicator externalApiHealthIndicator() {
        log.info("Registering external API health indicator");
        return new ExternalApiHealthIndicator(properties);
    }

    @Bean
    @ConditionalOnMissingBean(name = "logHealthIndicator")
    @ConditionalOnProperty(prefix = "health.monitor.logs", name = "enabled", havingValue = "true", matchIfMissing = true)
    public LogHealthIndicator logHealthIndicator() {
        log.info("Registering log health indicator");
        return new LogHealthIndicator(properties);
    }
}

