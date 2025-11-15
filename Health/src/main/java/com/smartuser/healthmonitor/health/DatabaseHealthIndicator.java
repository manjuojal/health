package com.smartuser.healthmonitor.health;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;

import javax.sql.DataSource;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import com.smartuser.healthmonitor.HealthMonitorProperties;

import lombok.extern.slf4j.Slf4j;

/**
 * Health indicator for database connectivity
 */
@Slf4j
@Component
public class DatabaseHealthIndicator implements HealthIndicator {

    private final DataSource dataSource;
    private final HealthMonitorProperties properties;
    private static final int TIMEOUT_SECONDS = 2;

    public DatabaseHealthIndicator(DataSource dataSource, HealthMonitorProperties properties) {
        this.dataSource = dataSource;
        this.properties = properties;
    }

    @Override
    public Health health() {
        Instant start = Instant.now();
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            
            // Use virtual thread executor for JDK 21 (Project Loom) - keep executor open during connection
            var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor();
            try {
            	  log.info("Database health check ");
                connection.setNetworkTimeout(executor, TIMEOUT_SECONDS * 1000);
                
                // Test heartbeat with connection validation
                long heartbeatStart = System.currentTimeMillis();
                connection.isValid(2); // Validate connection and measure heartbeat
                long heartbeatTime = System.currentTimeMillis() - heartbeatStart;
                
                try (Statement statement = connection.createStatement()) {
                    statement.execute("SELECT 1");
                }
                
                Duration duration = Duration.between(start, Instant.now());
                
                Health.Builder healthBuilder = Health.up()
                        .withDetail("database", metaData.getDatabaseProductName())
                        .withDetail("databaseVersion", metaData.getDatabaseProductVersion())
                        .withDetail("driverName", metaData.getDriverName())
                        .withDetail("driverVersion", metaData.getDriverVersion())
                        .withDetail("heartbeat", heartbeatTime + "ms")
                        .withDetail("responseTime", duration.toMillis() + "ms")
                        .withDetail("readOnly", connection.isReadOnly())
                        .withDetail("autoCommit", connection.getAutoCommit());
                
                if (properties.getDatabase().isExposeConnectionInfo()) {
                    healthBuilder.withDetail("url", metaData.getURL())
                            .withDetail("username", metaData.getUserName());
                }
                
                // Add database-specific information
                try {
                    healthBuilder.withDetail("catalog", connection.getCatalog())
                            .withDetail("schema", connection.getSchema())
                            .withDetail("maxConnections", metaData.getMaxConnections())
                            .withDetail("defaultTransactionIsolation", metaData.getDefaultTransactionIsolation());
                } catch (Exception e) {
                    // Some databases may not support all metadata
                    log.debug("Could not retrieve some database metadata: {}", e.getMessage());
                }
                
                return healthBuilder.build();
            } finally {
                executor.shutdown();
            }
        } catch (Exception e) {
            Duration duration = Duration.between(start, Instant.now());
            log.warn("Database health check failed: {}", e.getMessage());
            return Health.down()
                    .withDetail("error", e.getMessage())
                    .withDetail("errorType", e.getClass().getSimpleName())
                    .withDetail("responseTime", duration.toMillis() + "ms")
                    .build();
        }
    }
}

