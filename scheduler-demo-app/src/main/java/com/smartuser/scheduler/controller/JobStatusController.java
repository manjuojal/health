package com.smartuser.scheduler.controller;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.smartuser.scheduler.task.ScheduledTaskService;

/**
 * REST controller to check job status
 */
@RestController
@RequestMapping("/api/jobs")
public class JobStatusController {

    private final ScheduledTaskService scheduledTaskService;

    public JobStatusController(ScheduledTaskService scheduledTaskService) {
        this.scheduledTaskService = scheduledTaskService;
    }

    /**
     * Get current job status
     */
    @GetMapping("/status")
    public ResponseEntity<ScheduledTaskService.JobStatus> getJobStatus() {
        return ResponseEntity.ok(scheduledTaskService.getJobStatus());
    }

    /**
     * Get job health status (based on job execution status)
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> getJobHealth() {
        ScheduledTaskService.JobStatus status = scheduledTaskService.getJobStatus();
        
        Map<String, Object> response = new HashMap<>();
        response.put("status", status.lastStatus());
        response.put("isRunning", status.isRunning());
        response.put("totalExecutions", status.totalExecutions());
        response.put("successCount", status.successCount());
        response.put("failureCount", status.failureCount());
        response.put("lastExecutionTime", status.lastExecutionTime());
        response.put("lastCompletionTime", status.lastCompletionTime());
        if (status.lastError() != null) {
            response.put("lastError", status.lastError());
        }
        
        return ResponseEntity.ok(response);
    }

    /**
     * Enable hang simulation so the next running job blocks
     */
    @PostMapping("/hang/enable")
    public ResponseEntity<Map<String, Object>> enableHangSimulation() {
        scheduledTaskService.enableHangSimulation();
        return ResponseEntity.accepted().body(Map.of(
                "hangSimulationEnabled", true,
                "message", "Hang simulation enabled. Next running job will appear hung."
        ));
    }

    /**
     * Disable hang simulation so blocked jobs can continue
     */
    @PostMapping("/hang/disable")
    public ResponseEntity<Map<String, Object>> disableHangSimulation() {
        scheduledTaskService.disableHangSimulation();
        return ResponseEntity.ok(Map.of(
                "hangSimulationEnabled", false,
                "message", "Hang simulation disabled. Jobs will resume."
        ));
    }

    /**
     * Get current hang simulation flag
     */
    @GetMapping("/hang/status")
    public ResponseEntity<Map<String, Object>> hangSimulationStatus() {
        return ResponseEntity.ok(Map.of(
                "hangSimulationEnabled", scheduledTaskService.isHangSimulationEnabled()
        ));
    }

    /**
     * Test endpoint to trigger an exception
     */
    @GetMapping("/test-error")
    public ResponseEntity<String> testError() {
        throw new RuntimeException("Test exception for health monitoring");
    }
}

