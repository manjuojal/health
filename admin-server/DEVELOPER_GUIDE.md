# Health Monitoring Platform – Developer Guide

This document covers the three core modules in `D:\Source\Finance` and explains how to integrate the health-monitor starter, secure applications, and register services with Spring Boot Admin.

---

## 1. `Health/` – Health Monitor Starter

### Purpose
Reusable Spring Boot 3.5 starter that registers rich health indicators (database, external API, log error tracking), an exception notifier, Prometheus metrics, and an optional `/api/health/db-status` API.

### Key Classes
- `HealthMonitorAutoConfiguration` – enables components when `health.monitor.enabled=true`.
- `CompositeHealthIndicatorRegistrar` – replaces default `db` indicator, registers `externalApi` and `log`.
- `HealthStatusNotifier` – routes runtime/startup errors to email/webhook and log indicator.
- `DatabaseStatusController` – exposes `/api/health/db-status` when enabled.
- `HealthMetrics` – publishes `health.monitor.*` gauges via Micrometer.

### Configuration Template
```yaml
health:
  monitor:
    enabled: true
    database:
      enabled: true
      statusEndpointEnabled: true
      overrideDefaultIndicator: true
      exposeConnectionInfo: false   # keep false in prod
    external:
      enabled: true
      url: https://api.example.com/health
      timeout: 3000
      nonCritical: false
    logs:
      enabled: true
      recentErrorsThreshold: 5
      email:
        enabled: false
      webhook:
        enabled: false
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      show-details: when_authorized
      roles: ACTUATOR_ADMIN
```

### Developer Steps
1. `cd Health && mvn clean install`.
2. Add dependency:
   ```xml
   <dependency>
     <groupId>com.smartuser</groupId>
     <artifactId>health-monitor-starter</artifactId>
     <version>1.0.0</version>
   </dependency>
   ```
3. Protect `/api/health/**` behind auth or trusted networks.
4. Enable alerts (email/webhook) only after wiring secure transports.

---

## 2. `scheduler-demo-app/` – Reference Consumer

### Purpose
Demonstrates how to embed the starter, monitor scheduled jobs for hangs or failures, secure actuator endpoints, and register with Spring Boot Admin.

### Highlights
- `ScheduledTaskService` tracks execution counts, failures, and supports hang simulation.
- `SchedulerHealthIndicator` reports `scheduler` component DOWN when jobs hang, stay idle too long, or fail.
- `JobStatusController` exposes `/api/jobs/status`, `/health`, `/hang/*`, `/test-error`.
- `SecurityConfig` enforces HTTP Basic, role checks (`ACTUATOR_ADMIN`, `JOB_OPERATOR`), CSP, HSTS, and CSRF rules.

### Application Configuration (excerpt)
```yaml
spring:
  boot:
    admin:
      client:
        url: http://localhost:8085
        username: ${ADMIN_SERVER_USERNAME}
        password: ${ADMIN_SERVER_PASSWORD}
        instance:
          metadata:
            user.name: ${SCHEDULER_ACTUATOR_USERNAME:actuator}
            user.password: ${SCHEDULER_ACTUATOR_PASSWORD:change-this-password}
  security:
    user:
      name: ${SCHEDULER_ACTUATOR_USERNAME:actuator}
      password: ${SCHEDULER_ACTUATOR_PASSWORD:change-this-password}
      roles: ACTUATOR_ADMIN,JOB_OPERATOR
  h2:
    console:
      enabled: false

scheduler:
  monitor:
    enabled: true
    max-task-duration-ms: 8000
    max-idle-duration-ms: 60000

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      show-details: when_authorized
      roles: ACTUATOR_ADMIN
```

### Running Locally
```bash
cd D:\Source\Finance\scheduler-demo-app
mvn spring-boot:run
```
Test endpoints:
- `GET /api/jobs/status`
- `POST /api/jobs/hang/enable` (simulate hang → `scheduler` health DOWN)
- `GET /actuator/health`

---

## 3. `admin-server/` – Spring Boot Admin Host

### Purpose
Provides the Admin UI for observing registered services, viewing component health, and relaying notifications.

### Security Highlights
- `SecurityConfig`:
  - Only static assets/login/logout are anonymous; everything else requires `ROLE_ADMIN`.
  - CSRF enabled; `/instances/**` and `/actuator/**` excluded for SBA client callbacks.
  - CSP allows SBA inline assets (`script-src/style-src 'unsafe-inline'`, `img/font data:`), with HSTS, frame denial, referrer policy, and default XSS protection.
- Credentials pulled from environment (`ADMIN_SERVER_USERNAME`, `ADMIN_SERVER_PASSWORD`).

### Configuration Snippet
```yaml
spring:
  security:
    user:
      name: ${ADMIN_SERVER_USERNAME:admin}
      password: ${ADMIN_SERVER_PASSWORD:change-this-password}
spring.boot.admin:
  monitor:
    period: 10s
  notify:
    mail:
      enabled: true
      to: ${MAIL_TO:admin@example.com}
      from: ${MAIL_FROM:health-monitor@example.com}
```

### Running
```bash
cd D:\Source\Finance\admin-server
mvn spring-boot:run
```
Open `http://localhost:8085`, log in with admin credentials, and ensure services register successfully.

---

## Adoption Checklist for New Projects
1. Install the starter (`Health/`) and configure `health.monitor.*`.
2. Limit actuator exposure (`health,info,metrics,prometheus`) and secure with HTTP Basic roles.
3. Register the service with the Admin Server, passing actuator credentials via `spring.boot.admin.client.instance.metadata`.
4. Configure alerts (email/webhook) and thresholds per environment.
5. Validate scenarios: DB outage, external API failure, recent error spikes, job hang, manual `/api/jobs/test-error`.
6. Document service-specific run commands, ports, and credentials for ops teams.

By following this guide, teams can consistently integrate health monitoring, satisfy corporate security standards, and leverage Spring Boot Admin for observability across services.

