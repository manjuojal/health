package com.smartuser.scheduler.health;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import com.smartuser.healthmonitor.notifier.HealthStatusNotifier;
import com.smartuser.scheduler.config.SchedulerMonitorProperties;
import com.smartuser.scheduler.task.ScheduledTaskService;

import lombok.extern.slf4j.Slf4j;

/**
 * Custom health indicator that marks the scheduler DOWN when a job is hung or stale.
 */
@Slf4j
@Component("scheduler")
public class SchedulerHealthIndicator implements HealthIndicator {

    private final ScheduledTaskService scheduledTaskService;
    private final SchedulerMonitorProperties properties;
    private final ObjectProvider<HealthStatusNotifier> notifierProvider;

    public SchedulerHealthIndicator(
            ScheduledTaskService scheduledTaskService,
            SchedulerMonitorProperties properties,
            ObjectProvider<HealthStatusNotifier> notifierProvider) {
        this.scheduledTaskService = scheduledTaskService;
        this.properties = properties;
        this.notifierProvider = notifierProvider;
    }

    @Override
    public Health health() {
        if (!properties.isEnabled()) {
            return Health.unknown()
                    .withDetail("status", "DISABLED")
                    .build();
        }

        ScheduledTaskService.JobStatus jobStatus = scheduledTaskService.getJobStatus();
        long now = System.currentTimeMillis();

        Health.Builder builder = Health.up();
        builder.withDetail("lastStatus", jobStatus.lastStatus())
               .withDetail("isRunning", jobStatus.isRunning())
               .withDetail("totalExecutions", jobStatus.totalExecutions())
               .withDetail("successCount", jobStatus.successCount())
               .withDetail("failureCount", jobStatus.failureCount())
               .withDetail("lastExecutionTime", jobStatus.lastExecutionTime())
               .withDetail("lastCompletionTime", jobStatus.lastCompletionTime())
               .withDetail("hangSimulationEnabled", jobStatus.hangSimulationEnabled());
        if (jobStatus.lastError() != null) {
            builder.withDetail("lastError", jobStatus.lastError());
        }

        boolean hung = jobStatus.isRunning()
                && jobStatus.lastExecutionTime() > 0
                && (now - jobStatus.lastExecutionTime()) > properties.getMaxTaskDurationMs();

        boolean stale = jobStatus.totalExecutions() >= properties.getMinExecutionsBeforeIdleCheck()
                && jobStatus.lastCompletionTime() > 0
                && (now - jobStatus.lastCompletionTime()) > properties.getMaxIdleDurationMs();

        boolean failed = "FAILED".equalsIgnoreCase(jobStatus.lastStatus());

        if (failed) {
            log.warn("Scheduler health DOWN - last execution failed: {}", jobStatus.lastError());
            notifyDown("SCHEDULER_FAILED", jobStatus.lastError());
            return builder.down()
                    .withDetail("reason", "LAST_EXECUTION_FAILED")
                    .build();
        }

        if (hung) {
            long runningDuration = now - jobStatus.lastExecutionTime();
            log.warn("Scheduler health DOWN - job appears hung ({} ms)", runningDuration);
            notifyDown("SCHEDULER_HUNG", "Job running for " + runningDuration + "ms");
            return builder.down()
                    .withDetail("reason", "TASK_HUNG")
                    .withDetail("runningDurationMs", runningDuration)
                    .build();
        }

        if (stale) {
            long idleDuration = now - jobStatus.lastCompletionTime();
            log.warn("Scheduler health DOWN - no completion for {} ms", idleDuration);
            notifyDown("SCHEDULER_IDLE", "No job completion for " + idleDuration + "ms");
            return builder.down()
                    .withDetail("reason", "NO_RECENT_COMPLETION")
                    .withDetail("idleDurationMs", idleDuration)
                    .build();
        }

        return builder.build();
    }

    private void notifyDown(String code, String message) {
        notifierProvider.ifAvailable(notifier ->
                notifier.notifyHealthStatusChange("scheduler", "DOWN", code + ": " + message));
    }
}

