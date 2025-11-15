# Email Notifications Configuration

Spring Boot Admin Server can send email notifications when monitored services go DOWN.

## Configuration

### 1. SMTP Server Configuration

Edit `application.yml` to configure your SMTP server:

```yaml
spring:
  mail:
    host: smtp.gmail.com  # Your SMTP server
    port: 587
    username: ${MAIL_USERNAME:your-email@gmail.com}
    password: ${MAIL_PASSWORD:your-password}
    properties:
      mail:
        smtp:
          auth: true
          starttls:
            enable: true
            required: true
```

### 2. Email Notification Settings

Configure email notification recipients and sender:

```yaml
spring.boot.admin:
  notify:
    mail:
      enabled: true
      only-on-down: true  # Only send emails when services go DOWN
      to: ${MAIL_TO:admin@example.com}  # Recipient email(s)
      from: ${MAIL_FROM:health-monitor@example.com}  # Sender email
      subject: "Health Monitor Alert: Service is DOWN"
```

## Environment Variables

For security, use environment variables for sensitive information:

**Windows:**
```cmd
set MAIL_USERNAME=your-email@gmail.com
set MAIL_PASSWORD=your-app-password
set MAIL_TO=admin@example.com
set MAIL_FROM=health-monitor@example.com
```

**Linux/Mac:**
```bash
export MAIL_USERNAME=your-email@gmail.com
export MAIL_PASSWORD=your-app-password
export MAIL_TO=admin@example.com
export MAIL_FROM=health-monitor@example.com
```

## Gmail Configuration

If using Gmail:

1. **Enable 2-Factor Authentication** on your Google account
2. **Generate an App Password**:
   - Go to Google Account → Security → 2-Step Verification
   - Scroll down to "App passwords"
   - Generate a password for "Mail"
   - Use this password in `MAIL_PASSWORD` (not your regular Gmail password)

3. **Configure in application.yml:**
```yaml
spring:
  mail:
    host: smtp.gmail.com
    port: 587
    username: your-email@gmail.com
    password: ${MAIL_PASSWORD}  # Use app password here
```

## Other SMTP Servers

### Outlook/Office 365
```yaml
spring:
  mail:
    host: smtp.office365.com
    port: 587
    username: your-email@outlook.com
    password: ${MAIL_PASSWORD}
```

### SendGrid
```yaml
spring:
  mail:
    host: smtp.sendgrid.net
    port: 587
    username: apikey
    password: ${SENDGRID_API_KEY}
```

### Custom SMTP Server
```yaml
spring:
  mail:
    host: smtp.yourcompany.com
    port: 587  # or 465 for SSL
    username: ${MAIL_USERNAME}
    password: ${MAIL_PASSWORD}
    properties:
      mail:
        smtp:
          auth: true
          starttls:
            enable: true
            required: true
```

## Email Content

### Service-Level Alerts

When a service goes DOWN, an email will be sent with:

- **Subject**: "Health Monitor Alert: Service is DOWN" (configurable)
- **Body** includes:
  - Service Name
  - Service ID
  - Status (DOWN/OFFLINE)
  - Management URL
  - Service URL
  - Alert message

### Component-Level Alerts

When a component (e.g., database) goes DOWN, an email will be sent with:

- **Subject**: "Health Monitor Alert: Database is DOWN" (configurable)
- **Body** includes:
  - Service Name
  - Service ID
  - Component Name (e.g., DB)
  - Component Status (DOWN)
  - Component Details (error messages, etc.)
  - Management URL
  - Service URL
  - Alert message

## Component Monitoring

The Admin Server can monitor individual component health (e.g., database) and send emails when components go DOWN, even if the overall service status is still UP.

### Configuration

Enable component monitoring in `application.yml`:

```yaml
spring.boot.admin.notify.mail:
  enabled: true
  monitor-components: true  # Enable component-level monitoring
  component-check-interval: 30000  # Check every 30 seconds (in milliseconds)
  component-subject: "Health Monitor Alert: Database is DOWN"
```

### How It Works

1. **Periodic Health Checks**: The Admin Server periodically checks the `/actuator/health` endpoint of each registered service
2. **Component Status Tracking**: It tracks the status of each component (e.g., `db`, `log`, `externalApi`)
3. **Status Change Detection**: When a component status changes from UP to DOWN, an email is sent
4. **No Duplicate Alerts**: Emails are only sent when the status changes, not on every check

### Supported Components

Currently monitors:
- **Database (`db`)**: Sends email when database connectivity fails

You can extend this to monitor other components like:
- `log` - Log monitoring status
- `externalApi` - External API status
- Any other custom health indicators

## Testing

1. **Start Admin Server** with email configuration
2. **Register a test application**
3. **Stop the test application** (or make it unhealthy)
4. **Check email** - You should receive an email notification

## Troubleshooting

### Email Not Sending

1. **Check SMTP Configuration**: Verify host, port, username, password
2. **Check Logs**: Look for email-related errors in Admin Server logs
3. **Test SMTP Connection**: Use a mail client to verify SMTP settings
4. **Check Firewall**: Ensure port 587 (or 465) is not blocked

### Gmail Issues

- Use **App Password**, not regular password
- Ensure 2-Factor Authentication is enabled
- Check if "Less secure app access" is enabled (if not using App Password)

### Multiple Recipients

To send to multiple recipients, use comma-separated emails:

```yaml
spring.boot.admin.notify.mail.to: admin1@example.com,admin2@example.com,ops@example.com
```

## Disabling Email Notifications

To disable email notifications:

```yaml
spring.boot.admin.notify.mail.enabled: false
```

Or set `only-on-down: false` to receive notifications for all status changes (not just DOWN).

