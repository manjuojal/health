package com.smartuser.scheduler.task;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import lombok.Getter;

/**
 * Scheduled task service that demonstrates job monitoring
 */
@Service
@Getter
public class ScheduledTaskService {

    private static final Logger log = LoggerFactory.getLogger(ScheduledTaskService.class);

    // Track job execution status
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private final AtomicInteger executionCount = new AtomicInteger(0);
    private final AtomicInteger successCount = new AtomicInteger(0);
    private final AtomicInteger failureCount = new AtomicInteger(0);
    private final AtomicLong lastExecutionTime = new AtomicLong(0);
    private final AtomicLong lastCompletionTime = new AtomicLong(0);
    private volatile String lastError = null;
    private volatile String lastStatus = "NOT_STARTED";
    private final AtomicBoolean hangSimulationEnabled = new AtomicBoolean(false);

    /**
     * Example scheduled task that runs every 30 seconds
     * This simulates a long-running job
     */
    @Scheduled(fixedRate = 30000, initialDelay = 5000)
    public void processDataTask() {
        String taskName = "processDataTask";
        long startTime = System.currentTimeMillis();
        isRunning.set(true);
        lastExecutionTime.set(startTime);
        lastStatus = "RUNNING";
        executionCount.incrementAndGet();

        log.info("=== Starting scheduled task: {} ===", taskName);
        log.info("Execution #{} started at {}", executionCount.get(), LocalDateTime.now());

        try {
            // Simulate work - sleep for 5 seconds
            Thread.sleep(5000);
            simulateHangIfNeeded(taskName);

            // Simulate occasional failures (every 5th execution)
            if (executionCount.get() % 5 == 0) {
                throw new RuntimeException("Simulated failure in scheduled task execution #" + executionCount.get());
            }

            // Success
            successCount.incrementAndGet();
            lastStatus = "COMPLETED";
            lastError = null;
            long duration = System.currentTimeMillis() - startTime;
            lastCompletionTime.set(System.currentTimeMillis());

            log.info("=== Task {} completed successfully in {}ms ===", taskName, duration);
            log.info("Total executions: {}, Success: {}, Failures: {}", 
                    executionCount.get(), successCount.get(), failureCount.get());

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            handleFailure(taskName, e, startTime);
        } catch (Exception e) {
            handleFailure(taskName, e, startTime);
        } finally {
            isRunning.set(false);
        }
    }

    /**
     * Example scheduled task that runs every minute
     * This simulates a quick job
     */
    @Scheduled(fixedRate = 60000, initialDelay = 10000)
    public void quickTask() {
        String taskName = "quickTask";
        long startTime = System.currentTimeMillis();
        isRunning.set(true);
        lastExecutionTime.set(startTime);
        lastStatus = "RUNNING";
        executionCount.incrementAndGet();

        log.info("Quick task started at {}", LocalDateTime.now());

        try {
            // Simulate quick work
            Thread.sleep(1000);
            simulateHangIfNeeded(taskName);

            successCount.incrementAndGet();
            lastStatus = "COMPLETED";
            lastError = null;
            lastCompletionTime.set(System.currentTimeMillis());

            log.info("Quick task completed in {}ms", System.currentTimeMillis() - startTime);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            handleFailure(taskName, e, startTime);
        } catch (Exception e) {
            handleFailure(taskName, e, startTime);
        } finally {
            isRunning.set(false);
        }
    }

    /**
     * Example scheduled task that runs every 2 minutes
     * This can simulate a stuck job if configured incorrectly
     */
    @Scheduled(fixedRate = 120000, initialDelay = 15000)
    public void longRunningTask() {
        String taskName = "longRunningTask";
        long startTime = System.currentTimeMillis();
        isRunning.set(true);
        lastExecutionTime.set(startTime);
        lastStatus = "RUNNING";
        executionCount.incrementAndGet();

        log.info("=== Starting long-running task: {} ===", taskName);

        try {
            // Simulate longer work - 10 seconds
            Thread.sleep(10000);
            simulateHangIfNeeded(taskName);

            successCount.incrementAndGet();
            lastStatus = "COMPLETED";
            lastError = null;
            lastCompletionTime.set(System.currentTimeMillis());

            log.info("=== Long-running task {} completed ===", taskName);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            handleFailure(taskName, e, startTime);
        } catch (Exception e) {
            handleFailure(taskName, e, startTime);
        } finally {
            isRunning.set(false);
        }
    }

    private void handleFailure(String taskName, Exception e, long startTime) {
        failureCount.incrementAndGet();
        lastStatus = "FAILED";
        lastError = e.getMessage();
        long duration = System.currentTimeMillis() - startTime;

        log.error("=== Task {} FAILED after {}ms ===", taskName, duration, e);
        log.error("Error: {}", e.getMessage());
    }

    /**
     * Get job status summary
     */
    public JobStatus getJobStatus() {
        return new JobStatus(
            isRunning.get(),
            executionCount.get(),
            successCount.get(),
            failureCount.get(),
            lastExecutionTime.get(),
            lastCompletionTime.get(),
            lastStatus,
            lastError,
            hangSimulationEnabled.get()
        );
    }

    public void enableHangSimulation() {
        hangSimulationEnabled.set(true);
        log.warn("Hang simulation ENABLED - next job run will block until disabled.");
    }

    public void disableHangSimulation() {
        hangSimulationEnabled.set(false);
        log.warn("Hang simulation DISABLED - any blocked job can resume.");
    }

    public boolean isHangSimulationEnabled() {
        return hangSimulationEnabled.get();
    }

    private void simulateHangIfNeeded(String taskName) throws InterruptedException {
        if (!hangSimulationEnabled.get()) {
            return;
        }

        log.warn("Simulating hung task for {}. Job will remain RUNNING until hang simulation is disabled.", taskName);
        while (hangSimulationEnabled.get()) {
            Thread.sleep(1000);
        }
        log.info("Hang simulation cleared for {} - continuing execution.", taskName);
    }

    /**
     * Job status DTO
     */
    public record JobStatus(
        boolean isRunning,
        int totalExecutions,
        int successCount,
        int failureCount,
        long lastExecutionTime,
        long lastCompletionTime,
        String lastStatus,
        String lastError,
        boolean hangSimulationEnabled
    ) {}
}

