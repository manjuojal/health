package com.smartuser.healthmonitor;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

/**
 * Configuration properties for Health Monitor Starter
 */
@Data
@ConfigurationProperties(prefix = "health.monitor")
public class HealthMonitorProperties {

    /**
     * Enable/disable health monitoring
     */
    private boolean enabled = true;

    /**
     * Database health check configuration
     */
    private DatabaseConfig database = new DatabaseConfig();

    /**
     * External API health check configuration
     */
    private ExternalConfig external = new ExternalConfig();

    /**
     * Log monitoring configuration
     */
    private LogsConfig logs = new LogsConfig();

    @Data
    public static class DatabaseConfig {
        private boolean enabled = true;
        /**
         * Enable database status endpoint (/actuator/db-status)
         */
        private boolean statusEndpointEnabled = false;
        /**
         * Replace Spring Boot's default "database" health component with the starter's
         * custom indicator (shows detailed metadata). Set to false to keep the default.
         */
        private boolean overrideDefaultIndicator = true;
        /**
         * Expose JDBC URL and username in health/diagnostic responses. Keep disabled
         * for production to avoid leaking connection details.
         */
        private boolean exposeConnectionInfo = false;
    }

    @Data
    public static class ExternalConfig {
        private boolean enabled = true;
        private String url = "";
        private long timeout = 3000;
        /**
         * If true, external API failures won't affect overall health status
         * (returns UNKNOWN instead of DOWN when failed)
         */
        private boolean nonCritical = false;
    }

    @Data
    public static class LogsConfig {
        private boolean enabled = true;
        private int recentErrorsThreshold = 5;
        private EmailConfig email = new EmailConfig();
        private WebhookConfig webhook = new WebhookConfig();
    }

    @Data
    public static class EmailConfig {
        private boolean enabled = true;
        private String to = "ops@company.com";
        private String from = "health-monitor@company.com";
        private String subject = "Health Monitor Alert";
    }

    @Data
    public static class WebhookConfig {
        private boolean enabled = true;
        private String url = "";
        private long timeout = 5000;
    }
}

