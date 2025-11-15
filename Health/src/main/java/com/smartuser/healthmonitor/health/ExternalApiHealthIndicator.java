package com.smartuser.healthmonitor.health;

import java.time.Duration;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.smartuser.healthmonitor.HealthMonitorProperties;

import lombok.extern.slf4j.Slf4j;

/**
 * Health indicator for external API availability
 */
@Slf4j
@Component
public class ExternalApiHealthIndicator implements HealthIndicator {

    private final HealthMonitorProperties properties;
    private final WebClient webClient;

    public ExternalApiHealthIndicator(HealthMonitorProperties properties) {
        this.properties = properties;
        this.webClient = WebClient.builder()
                .build();
    }

    @Override
    public Health health() {
        if (!properties.getExternal().isEnabled()) {
            return Health.unknown()
                    .withDetail("status", "DISABLED")
                    .build();
        }

        String url = properties.getExternal().getUrl();
        long timeout = properties.getExternal().getTimeout();

        try {
            HttpStatusCode statusCode = webClient.get()
                    .uri(url)
                    .retrieve()
                    .toBodilessEntity()
                    .timeout(Duration.ofMillis(timeout))
                    .map(response -> response.getStatusCode())
                    .block();

            if (statusCode != null && statusCode.is2xxSuccessful()) {
                return Health.up()
                        .withDetail("url", url)
                        .withDetail("status", statusCode.value())
                        .withDetail("responseTime", "< " + timeout + "ms")
                        .build();
            } else {
                return buildHealthResponse(url, statusCode != null ? String.valueOf(statusCode.value()) : "UNKNOWN", 
                        "Non-2xx response", null);
            }
        } catch (WebClientResponseException e) {
            log.warn("External API health check failed for {}: {}", url, e.getMessage());
            return buildHealthResponse(url, String.valueOf(e.getStatusCode().value()), 
                    null, e.getMessage());
        } catch (org.springframework.web.reactive.function.client.WebClientRequestException e) {
            log.warn("External API connection error for {}: {}", url, e.getMessage());
            return buildHealthResponse(url, null, 
                    "Connection error - check network connectivity", e.getMessage());
        } catch (Exception e) {
            // Check if root cause is timeout
            Throwable rootCause = e.getCause();
            String errorMessage = e.getMessage();
            boolean isTimeout = rootCause instanceof java.util.concurrent.TimeoutException ||
                               (errorMessage != null && (errorMessage.contains("timeout") || 
                                                         errorMessage.contains("Timeout")));
            
            if (isTimeout) {
                log.warn("External API timeout for {}: {}", url, errorMessage);
                return buildHealthResponse(url, null, 
                        "Timeout after " + timeout + "ms", "Request timeout");
            }
            
            log.warn("External API health check error for {}: {}", url, errorMessage);
            return buildHealthResponse(url, null, 
                    "Unexpected error during health check", errorMessage);
        }
    }

    /**
     * Build health response - returns UNKNOWN instead of DOWN if non-critical
     */
    private Health buildHealthResponse(String url, String status, String reason, String error) {
        Health.Builder builder;
        
        if (properties.getExternal().isNonCritical()) {
            // Non-critical: return UNKNOWN so it doesn't affect overall health
            builder = Health.unknown()
                    .withDetail("url", url)
                    .withDetail("critical", false);
        } else {
            // Critical: return DOWN which affects overall health
            builder = Health.down()
                    .withDetail("url", url)
                    .withDetail("critical", true);
        }
        
        if (status != null) {
            builder.withDetail("status", status);
        }
        if (reason != null) {
            builder.withDetail("reason", reason);
        }
        if (error != null) {
            builder.withDetail("error", error);
        }
        
        return builder.build();
    }
}

