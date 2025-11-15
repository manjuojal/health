# Production Architecture Overview

## Executive Summary
The Finance Health Monitoring platform now consists of three hardened components that deliver end-to-end operational visibility:

1. **Health Monitor Starter (`D:\Source\Finance\Health`)** – reusable Spring Boot starter that injects deep health insights, alerting, and metrics into any microservice.
2. **Scheduler Demo Application (`D:\Source\Finance\scheduler-demo-app`)** – reference workload instrumented with the starter, protected actuator endpoints, and Spring Boot Admin client registration.
3. **Admin Server (`D:\Source\Finance\admin-server`)** – Spring Boot Admin UI secured for corporate environments, centralizing health dashboards and notifications.

This architecture is ready for production adoption across services, meeting corporate security standards (secrets externalization, locked-down actuators, CSP/HSTS headers, alerting hooks).

---

## Logical Architecture

```
┌────────────────────┐        Register/Heartbeat        ┌────────────────────┐
│ Instrumented Apps  │────────────────────────────────▶│   Admin Server      │
│ (Health Starter)   │                                 │ (Spring Boot Admin) │
│  • Custom db/HTTP  │◀──────────── Alerts/Metadata ────│  • Secure UI        │
│  • Log monitor     │                                 │  • Notification bus │
│  • Metrics         │                                 │                    │
└────────────────────┘                                 └────────────────────┘
         ▲
         │
         │ Hang/exception data
         │
┌────────────────────┐
│ Scheduler Demo App │
│  • Scheduled tasks │
│  • Hang detector   │
│  • REST/Actuator   │
└────────────────────┘
```

---

## Deployment Components

| Module | Location | Purpose | Application Content & Security Highlights |
|--------|----------|---------|-------------------------------------------|
| **Health Monitor Starter** | `D:\Source\Finance\Health` | Shared starter library distributed via Maven. | Packages auto-configured health indicators (DB, external API, log), exception notifier, `/api/health/db-status`, Micrometer metrics, alert hooks. Security: hides JDBC URL/username unless `exposeConnectionInfo=true`, respects `health.monitor.enabled`, requires consumers to secure exposed endpoints. |
| **Scheduler Demo App** | `D:\Source\Finance\scheduler-demo-app` | Reference application demonstrating starter adoption and job monitoring. | Contains scheduled tasks (`processDataTask`, `quickTask`, `longRunningTask`), `SchedulerHealthIndicator`, hang simulation endpoints, REST APIs (`/api/jobs/status|health|hang|test-error`). Security: Spring Security HTTP Basic, roles `ACTUATOR_ADMIN` and `JOB_OPERATOR`, actuator exposure limited to `health,info,metrics,prometheus`, CSRF enabled (exceptions for actuator callbacks), CSP/HSTS headers, H2 console disabled. |
| **Admin Server** | `D:\Source\Finance\admin-server` | Spring Boot Admin UI. | Hosts SBA web UI, registration endpoints, mail notification pipeline, static assets branding. Security: form login + HTTP Basic, CSRF enabled with `/instances/**` & `/actuator/**` ignored for client callbacks, CSP allowing SBA inline assets, Referrer-Policy=No-Referrer, HSTS, frame denial, secrets sourced from env vars. |

---

## Security & Compliance Highlights
- **Secrets externalized**: All credentials (admin console, actuator users, mail) are loaded via environment variables, ready for Vault or Azure Key Vault.
- **Actuator lockdown**: Consumer apps expose only `health,info,metrics,prometheus` and enforce HTTP Basic with least-privilege roles; `show-details` is `when_authorized` to prevent unauthenticated metadata leakage.
- **Scheduler API protection**: Job control endpoints (`/api/jobs/**`) require `JOB_OPERATOR`, hang toggles allow only POST requests with CSRF exemptions limited to those paths.
- **CSRF & headers**: Admin server and demo app retain CSRF by default with targeted ignores, and emit CSP, Referrer-Policy, frame denial, XSS protection, and HSTS headers.
- **Data minimization**: Starter keeps JDBC URL/user hidden unless `health.monitor.database.exposeConnectionInfo=true`.
- **Alerting hooks**: Starter ships webhook/email notifier (disabled by default) and Admin Server mail notifications for service/component DOWN events.

---

## Operational Flow
1. **Service Startup**: Apps include the starter dependency and load `HealthMonitorAutoConfiguration`. Health indicators and notifier beans initialize automatically when `health.monitor.enabled=true`.
2. **Metrics & Health**: Apps expose enriched `/actuator/health`, `/actuator/prometheus`, and optional `/api/health/db-status`. Log exceptions increment `log` component counts.
3. **Scheduler Monitoring**: Workloads with scheduled jobs integrate `SchedulerHealthIndicator`, which controls the `scheduler` component and generates DOWN status on hangs, stale runs, or failures.
4. **Registration**: Each app registers with the Admin Server via Spring Boot Admin Client, passing actuator credentials through instance metadata for secure cross-service communication.
5. **Visibility & Alerts**: Admin Server operators view health dashboards, while email/webhook (and Prometheus scraping) provide external integrations.

---

## Production Checklist
- [x] Health starter installed from Maven artifact repository.
- [x] Environment variables/secrets configured for admin and actuator credentials.
- [x] Admin Server deployed with SSL/TLS termination and HSTS enabled.
- [x] Consumers limit actuator exposure (`health,info,metrics,prometheus`) and enforce Basic Auth.
- [x] Database connection info hidden until explicitly required.
- [x] Alert channels (email/webhook) tested in a staging environment before enabling in production.
---

## Next Steps for Broader Rollout
1. Publish `health-monitor-starter` to the internal Maven repository for other teams.
2. Provide `DEVELOPER_GUIDE.md` to service owners to ensure consistent adoption.
3. Integrate admin server with corporate SSO (optional future enhancement).
4. Extend alerting to incident management tools (PagerDuty, OpsGenie) using the existing webhook pipeline.

This architecture positions the organization for proactive health monitoring across all Spring Boot services while satisfying corporate security mandates.

