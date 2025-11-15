package com.smartuser.admin.config;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.mail.MailSender;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.util.Assert;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.codecentric.boot.admin.server.domain.entities.Instance;
import de.codecentric.boot.admin.server.domain.entities.InstanceRepository;
import de.codecentric.boot.admin.server.domain.events.InstanceEvent;
import de.codecentric.boot.admin.server.domain.events.InstanceStatusChangedEvent;
import de.codecentric.boot.admin.server.notify.AbstractStatusChangeNotifier;
import reactor.core.publisher.Mono;

/**
 * Configuration for Spring Boot Admin email notifications
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "spring.boot.admin.notify.mail", name = "enabled", havingValue = "true", matchIfMissing = false)
public class NotificationConfig {

    private static final Logger log = LoggerFactory.getLogger(NotificationConfig.class);
    
    // Track previous component statuses to detect changes
    private final Map<String, Map<String, String>> previousComponentStatuses = new ConcurrentHashMap<>();
    private final WebClient webClient = WebClient.builder().build();
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    // Injected dependencies for scheduled method
    private InstanceRepository instanceRepository;
    private MailSender mailSender;
    private String mailTo;
    private String mailFrom;
    private String componentSubject;
    
    // Constructor injection for dependencies
    public NotificationConfig(
            InstanceRepository instanceRepository,
            MailSender mailSender,
            @Value("${spring.boot.admin.notify.mail.to}") String mailTo,
            @Value("${spring.boot.admin.notify.mail.from}") String mailFrom,
            @Value("${spring.boot.admin.notify.mail.component-subject:Health Monitor Alert: Database is DOWN}") String componentSubject) {
        this.instanceRepository = instanceRepository;
        this.mailSender = mailSender;
        this.mailTo = requireProperty(mailTo, "spring.boot.admin.notify.mail.to");
        this.mailFrom = requireProperty(mailFrom, "spring.boot.admin.notify.mail.from");
        this.componentSubject = componentSubject;
    }

    /**
     * Custom notifier that only sends emails when services go DOWN
     */
    @Bean
    @ConditionalOnProperty(prefix = "spring.boot.admin.notify.mail", name = "only-on-down", havingValue = "true", matchIfMissing = true)
    public DownOnlyMailNotifier downOnlyMailNotifier(
            MailSender mailSender, 
            InstanceRepository repository,
            @Value("${spring.boot.admin.notify.mail.to}") String to,
            @Value("${spring.boot.admin.notify.mail.from}") String from,
            @Value("${spring.boot.admin.notify.mail.subject:Health Monitor Alert: Service is DOWN}") String subject) {
        DownOnlyMailNotifier notifier = new DownOnlyMailNotifier(mailSender, repository);
        notifier.setTo(requireProperty(to, "spring.boot.admin.notify.mail.to"));
        notifier.setFrom(requireProperty(from, "spring.boot.admin.notify.mail.from"));
        notifier.setSubject(subject);
        return notifier;
    }
    
    /**
     * Periodically check component health and send emails when components go DOWN
     * Note: @Scheduled methods must have no parameters
     */
    @Scheduled(fixedDelayString = "${spring.boot.admin.notify.mail.component-check-interval:30000}") // Default 30 seconds
    @ConditionalOnProperty(prefix = "spring.boot.admin.notify.mail", name = "monitor-components", havingValue = "true", matchIfMissing = true)
    public void checkComponentHealth() {
        if (instanceRepository == null || mailSender == null) {
            return; // Dependencies not yet injected
        }
        
        instanceRepository.findAll()
            .collectList()
            .doOnNext(instances -> {
                instances.forEach(instance -> {
                    try {
                        String healthUrl = instance.getRegistration().getManagementUrl() + "/actuator/health";
                        String healthJson = webClient.get()
                            .uri(healthUrl)
                            .retrieve()
                            .bodyToMono(String.class)
                            .block();
                        
                        if (healthJson != null) {
                            checkComponentStatus(instance, healthJson, mailSender, mailTo, mailFrom, componentSubject);
                        }
                    } catch (Exception e) {
                        log.debug("Failed to check health for instance {}: {}", instance.getId(), e.getMessage());
                    }
                });
            })
            .subscribe(
                instances -> log.trace("Checked health for {} instances", instances.size()),
                error -> log.error("Error checking component health", error)
            );
    }

    private static String requireProperty(String value, String propertyName) {
        Assert.hasText(value, propertyName + " must be provided");
        return value;
    }
    
    private void checkComponentStatus(Instance instance, String healthJson, MailSender mailSender, 
            String to, String from, String subject) {
        try {
            JsonNode root = objectMapper.readTree(healthJson);
            JsonNode components = root.get("components");
            
            if (components != null) {
                String instanceId = instance.getId().toString();
                Map<String, String> previousStatuses = previousComponentStatuses.computeIfAbsent(
                    instanceId, k -> new HashMap<>());
                
                // Check database component
                JsonNode dbComponent = components.get("db");
                if (dbComponent != null) {
                    String currentStatus = dbComponent.get("status").asText();
                    String previousStatus = previousStatuses.get("db");
                    
                    // If database goes from UP to DOWN, send email
                    if ("DOWN".equals(currentStatus) && !"DOWN".equals(previousStatus)) {
                        sendComponentEmail(instance, "db", currentStatus, dbComponent, mailSender, to, from, subject);
                    }
                    
                    previousStatuses.put("db", currentStatus);
                }
            }
        } catch (Exception e) {
            log.error("Failed to parse health JSON for instance {}", instance.getId(), e);
        }
    }
    
    private void sendComponentEmail(Instance instance, String componentName, String status, 
            JsonNode componentDetails, MailSender mailSender, String to, String from, String subject) {
        try {
            org.springframework.mail.SimpleMailMessage message = new org.springframework.mail.SimpleMailMessage();
            message.setTo(to);
            message.setFrom(from);
            message.setSubject(subject);
            message.setText(buildComponentEmailBody(instance, componentName, status, componentDetails));
            
            mailSender.send(message);
            log.info("Email notification sent for component {} in service {} ({}) with status {}", 
                componentName, instance.getRegistration().getName(), instance.getId(), status);
        } catch (Exception e) {
            log.error("Failed to send email notification for component {} in service {} ({})", 
                componentName, instance.getRegistration().getName(), instance.getId(), e);
        }
    }
    
    private String buildComponentEmailBody(Instance instance, String componentName, String status, JsonNode componentDetails) {
        StringBuilder body = new StringBuilder();
        body.append("Component Health Alert\n");
        body.append("=====================\n\n");
        body.append("Service Name: ").append(instance.getRegistration().getName()).append("\n");
        body.append("Service ID: ").append(instance.getId()).append("\n");
        body.append("Component: ").append(componentName.toUpperCase()).append("\n");
        body.append("Status: ").append(status).append("\n");
        body.append("Management URL: ").append(instance.getRegistration().getManagementUrl()).append("\n");
        body.append("Service URL: ").append(instance.getRegistration().getServiceUrl()).append("\n");
        
        if (componentDetails != null && componentDetails.has("details")) {
            body.append("\nComponent Details:\n");
            JsonNode details = componentDetails.get("details");
            details.properties().forEach(entry -> {
                body.append("  ").append(entry.getKey()).append(": ").append(entry.getValue().asText()).append("\n");
            });
        }
        
        body.append("\n");
        body.append("The ").append(componentName.toUpperCase()).append(" component has gone DOWN. Please check immediately.\n");
        body.append("\n");
        body.append("You can view the service details in the Health Monitor Dashboard.\n");
        return body.toString();
    }

    /**
     * Custom notifier that only sends emails when service status changes to DOWN
     */
    public static class DownOnlyMailNotifier extends AbstractStatusChangeNotifier {
        
        private static final Logger log = LoggerFactory.getLogger(DownOnlyMailNotifier.class);
        
        private final MailSender mailSender;
        private String to = "admin@example.com";
        private String from = "health-monitor@example.com";
        private String subject = "Health Monitor Alert: Service is DOWN";

        public DownOnlyMailNotifier(MailSender mailSender, InstanceRepository repository) {
            super(repository);
            this.mailSender = mailSender;
        }

        @Override
        @NonNull
        protected Mono<Void> doNotify(@NonNull InstanceEvent event, @NonNull Instance instance) {
            return Objects.requireNonNull(Mono.fromRunnable(() -> {
                if (event instanceof InstanceStatusChangedEvent) {
                    InstanceStatusChangedEvent statusEvent = (InstanceStatusChangedEvent) event;
                    String toStatus = statusEvent.getStatusInfo().getStatus();
                    
                    // Only send email when service goes DOWN or OFFLINE
                    if ("DOWN".equals(toStatus) || "OFFLINE".equals(toStatus)) {
                        sendEmail(instance, toStatus);
                    }
                }
            }));
        }

        private void sendEmail(Instance instance, String status) {
            try {
                org.springframework.mail.SimpleMailMessage message = new org.springframework.mail.SimpleMailMessage();
                message.setTo(to);
                message.setFrom(from);
                message.setSubject(subject);
                message.setText(buildEmailBody(instance, status));
                
                mailSender.send(message);
                log.info("Email notification sent for service {} ({}) with status {}", 
                    instance.getRegistration().getName(), instance.getId(), status);
            } catch (Exception e) {
                log.error("Failed to send email notification for service {} ({})", 
                    instance.getRegistration().getName(), instance.getId(), e);
            }
        }

        private String buildEmailBody(Instance instance, String status) {
            StringBuilder body = new StringBuilder();
            body.append("Service Status Alert\n");
            body.append("===================\n\n");
            body.append("Service Name: ").append(instance.getRegistration().getName()).append("\n");
            body.append("Service ID: ").append(instance.getId()).append("\n");
            body.append("Status: ").append(status).append("\n");
            body.append("Management URL: ").append(instance.getRegistration().getManagementUrl()).append("\n");
            body.append("Service URL: ").append(instance.getRegistration().getServiceUrl()).append("\n");
            body.append("\n");
            body.append("The service has gone DOWN. Please check the service immediately.\n");
            body.append("\n");
            body.append("You can view the service details in the Health Monitor Dashboard.\n");
            return body.toString();
        }

        public String getTo() {
            return to;
        }

        public void setTo(String to) {
            this.to = to;
        }

        public String getFrom() {
            return from;
        }

        public void setFrom(String from) {
            this.from = from;
        }

        public String getSubject() {
            return subject;
        }

        public void setSubject(String subject) {
            this.subject = subject;
        }
    }
}
