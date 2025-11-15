package com.smartuser.healthmonitor.health;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import com.smartuser.healthmonitor.HealthMonitorProperties;

import lombok.extern.slf4j.Slf4j;

/**
 * Health indicator that monitors recent ERROR-level logs
 * Errors are recorded explicitly via recordError() method
 */
@Slf4j
@Component
public class LogHealthIndicator implements HealthIndicator {

    private final HealthMonitorProperties properties;
    private final ConcurrentLinkedQueue<LogEntry> recentErrors = new ConcurrentLinkedQueue<>();
    private static final int MAX_ERRORS_TO_TRACK = 100;

    public LogHealthIndicator(HealthMonitorProperties properties) {
        this.properties = properties;
    }

    /**
     * Record an error log entry
     */
    public void recordError(String message, Throwable throwable) {
        if (!properties.getLogs().isEnabled()) {
            return;
        }

        LogEntry entry = new LogEntry(Instant.now(), message, throwable);
        recentErrors.offer(entry);
        
        // Keep only recent errors
        while (recentErrors.size() > MAX_ERRORS_TO_TRACK) {
            recentErrors.poll();
        }
    }

    @Override
    public Health health() {
        if (!properties.getLogs().isEnabled()) {
            return Health.unknown()
                    .withDetail("status", "DISABLED")
                    .build();
        }

        int threshold = properties.getLogs().getRecentErrorsThreshold();
        List<LogEntry> recentErrorsList = getRecentErrors(threshold * 2); // Get more for details
        
        Health.Builder builder = Health.up()
                .withDetail("recentErrorsCount", recentErrorsList.size())
                .withDetail("threshold", threshold);

        if (recentErrorsList.size() >= threshold) {
            List<String> errorMessages = recentErrorsList.stream()
                    .limit(threshold)
                    .map(LogEntry::getMessage)
                    .collect(Collectors.toList());
            
            builder.down()
                    .withDetail("status", "ERROR_THRESHOLD_EXCEEDED")
                    .withDetail("recentErrors", errorMessages);
        }

        return builder.build();
    }

    private List<LogEntry> getRecentErrors(int maxCount) {
        List<LogEntry> errors = new ArrayList<>(recentErrors);
        Collections.reverse(errors); // Most recent first
        return errors.stream()
                .limit(maxCount)
                .collect(Collectors.toList());
    }

    private static class LogEntry {
        private final Instant timestamp;
        private final String message;
        private final Throwable throwable;

        public LogEntry(Instant timestamp, String message, Throwable throwable) {
            this.timestamp = timestamp;
            this.message = message;
            this.throwable = throwable;
        }

        public Instant getTimestamp() {
            return timestamp;
        }

        public String getMessage() {
            return message;
        }

        public Throwable getThrowable() {
            return throwable;
        }
    }
}

