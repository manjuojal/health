package com.smartuser.healthmonitor;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableAsync;

import javax.sql.DataSource;

import com.smartuser.healthmonitor.advice.GlobalExceptionHandler;
import com.smartuser.healthmonitor.controller.DatabaseStatusController;
import com.smartuser.healthmonitor.notifier.HealthStatusNotifier;

/**
 * Auto-configuration for Health Monitor Starter
 * Follows Spring Boot 3.5 auto-configuration patterns
 */
@AutoConfiguration
@EnableConfigurationProperties(HealthMonitorProperties.class)
@ConditionalOnProperty(prefix = "health.monitor", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableAsync
@ComponentScan(basePackages = "com.smartuser.healthmonitor")
public class HealthMonitorAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public GlobalExceptionHandler globalExceptionHandler(HealthStatusNotifier notifier) {
        return new GlobalExceptionHandler(notifier);
    }
    
    @Bean
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnProperty(prefix = "health.monitor.database", name = "statusEndpointEnabled", havingValue = "true", matchIfMissing = false)
    @ConditionalOnMissingBean
    public DatabaseStatusController databaseStatusController(DataSource dataSource, HealthMonitorProperties properties) {
        org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(HealthMonitorAutoConfiguration.class);
        log.info("Registering DatabaseStatusController bean - endpoint will be available at /api/health/db-status");
        return new DatabaseStatusController(dataSource, properties);
    }
}

