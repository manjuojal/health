package com.smartuser.healthmonitor.notifier;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationFailedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import com.smartuser.healthmonitor.HealthMonitorProperties;
import com.smartuser.healthmonitor.health.LogHealthIndicator;

import lombok.extern.slf4j.Slf4j;
import reactor.util.retry.Retry;

/**
 * Notifies about health status changes and exceptions
 */
@Slf4j
@Component
public class HealthStatusNotifier {

    private final HealthMonitorProperties properties;
    private final ObjectProvider<LogHealthIndicator> logHealthIndicatorProvider;
    private final WebClient webClient;

    public HealthStatusNotifier(HealthMonitorProperties properties, 
                               ObjectProvider<LogHealthIndicator> logHealthIndicatorProvider) {
        this.properties = properties;
        this.logHealthIndicatorProvider = logHealthIndicatorProvider;
        this.webClient = WebClient.builder().build();
    }

    /**
     * Listen for application startup failures
     */
    @EventListener(ApplicationFailedEvent.class)
    public void onApplicationFailed(ApplicationFailedEvent event) {
        Throwable exception = event.getException();
        log.error("Application failed to start", exception);
        
        String message = "Application startup failed: " + exception.getMessage();
        recordLogError(message, exception);
        
        sendAlert("STARTUP_FAILURE", message, exception);
    }

    /**
     * Notify about health status change
     */
    public void notifyHealthStatusChange(String component, String status, String reason) {
        String message = String.format("Health status change: %s is now %s. Reason: %s", 
                component, status, reason);
        log.warn(message);
        
        sendAlert("HEALTH_STATUS_CHANGE", message, null);
    }

    /**
     * Notify about error
     */
    public void notifyError(String message, Throwable throwable) {
        log.error(message, throwable);
        recordLogError(message, throwable);
        sendAlert("ERROR", message, throwable);
    }

    private void recordLogError(String message, Throwable throwable) {
        logHealthIndicatorProvider.ifAvailable(indicator -> indicator.recordError(message, throwable));
    }

    private void sendAlert(String alertType, String message, Throwable throwable) {
        if (!properties.isEnabled()) {
            return;
        }

        Map<String, Object> alertPayload = new HashMap<>();
        alertPayload.put("alertType", alertType);
        alertPayload.put("message", message);
        alertPayload.put("timestamp", System.currentTimeMillis());
        alertPayload.put("application", "health-monitor");
        
        if (throwable != null) {
            alertPayload.put("exception", throwable.getClass().getName());
            alertPayload.put("exceptionMessage", throwable.getMessage());
        }

        // Send webhook if enabled
        if (properties.getLogs().getWebhook().isEnabled()) {
            sendWebhookAlert(alertPayload);
        }

        // Send email if enabled (in a real implementation, use JavaMailSender)
        if (properties.getLogs().getEmail().isEnabled()) {
            sendEmailAlert(alertPayload);
        }
    }

    private void sendWebhookAlert(@NonNull Map<String, Object> payload) {
        try {
            final String webhookUrl = Objects.requireNonNull(
                    properties.getLogs().getWebhook().getUrl(),
                    "health.monitor.logs.webhook.url must not be null");
            long timeout = properties.getLogs().getWebhook().getTimeout();
            final Map<String, Object> requestPayload = new HashMap<>(payload);

            webClient.post()
                    .uri(webhookUrl)
                    .body(BodyInserters.fromValue(requestPayload))
                    .retrieve()
                    .toBodilessEntity()
                    .timeout(Duration.ofMillis(timeout))
                    .retryWhen(Retry.fixedDelay(1, Duration.ofSeconds(1)))
                    .block();

            log.debug("Webhook alert sent successfully");
        } catch (Exception e) {
            log.warn("Failed to send webhook alert: {}", e.getMessage());
        }
    }

    private void sendEmailAlert(Map<String, Object> payload) {
        // In a real implementation, use JavaMailSender or similar
        // For now, just log it
        log.info("Email alert would be sent to {}: {}", 
                properties.getLogs().getEmail().getTo(), 
                payload.get("message"));
    }
}

