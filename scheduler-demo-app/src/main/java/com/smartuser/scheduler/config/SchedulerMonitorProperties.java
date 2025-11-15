package com.smartuser.scheduler.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

/**
 * Scheduler health monitoring thresholds.
 */
@Data
@ConfigurationProperties(prefix = "scheduler.monitor")
public class SchedulerMonitorProperties {

    /**
     * Enable/disable scheduler health indicator.
     */
    private boolean enabled = true;

    /**
     * Maximum duration (ms) a job may run before being considered hung.
     */
    private long maxTaskDurationMs = 15_000;

    /**
     * Maximum idle time (ms) since last successful completion.
     */
    private long maxIdleDurationMs = 180_000;

    /**
     * Minimum executions before enforcing idle detection (avoid false positives at startup).
     */
    private int minExecutionsBeforeIdleCheck = 1;
}

