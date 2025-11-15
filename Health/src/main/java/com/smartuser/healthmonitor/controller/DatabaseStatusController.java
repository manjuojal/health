package com.smartuser.healthmonitor.controller;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.util.HashMap;
import java.util.Map;

import javax.sql.DataSource;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.smartuser.healthmonitor.HealthMonitorProperties;

import lombok.extern.slf4j.Slf4j;

/**
 * REST controller to expose database connection status and heartbeat
 */
@Slf4j
@RestController
@RequestMapping("/api/health")
public class DatabaseStatusController {

    private final DataSource dataSource;
    private final HealthMonitorProperties properties;

    public DatabaseStatusController(DataSource dataSource, HealthMonitorProperties properties) {
        this.dataSource = dataSource;
        this.properties = properties;
        log.info("DatabaseStatusController initialized - endpoint available at /api/health/db-status");
    }

    /**
     * Test endpoint to verify controller is registered
     */
    @GetMapping("/test")
    public ResponseEntity<Map<String, String>> test() {
        Map<String, String> response = new HashMap<>();
        response.put("status", "OK");
        response.put("message", "DatabaseStatusController is working");
        response.put("endpoint", "/api/health/db-status");
        return ResponseEntity.ok(response);
    }

    /**
     * Get database connection status and heartbeat
     * Endpoint: GET /api/health/db-status
     */
    @GetMapping("/db-status")
    public ResponseEntity<Map<String, Object>> getDatabaseStatus() {
        Map<String, Object> response = new HashMap<>();
        
        if (!properties.getDatabase().isEnabled()) {
            response.put("status", "DISABLED");
            response.put("message", "Database monitoring is disabled");
            return ResponseEntity.ok(response);
        }
        
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            
            // Test heartbeat with connection validation
            long startTime = System.currentTimeMillis();
            boolean isValid = connection.isValid(2);
            long responseTime = System.currentTimeMillis() - startTime;
            
            response.put("status", isValid ? "CONNECTED" : "DISCONNECTED");
            response.put("heartbeat", responseTime + "ms");
            response.put("database", metaData.getDatabaseProductName());
            response.put("databaseVersion", metaData.getDatabaseProductVersion());
            response.put("driverName", metaData.getDriverName());
            response.put("driverVersion", metaData.getDriverVersion());
            if (properties.getDatabase().isExposeConnectionInfo()) {
                response.put("url", metaData.getURL());
                response.put("username", metaData.getUserName());
            }
            response.put("readOnly", connection.isReadOnly());
            response.put("autoCommit", connection.getAutoCommit());
            
            // Database-specific information
            Map<String, Object> dbInfo = new HashMap<>();
            try {
                dbInfo.put("catalog", connection.getCatalog());
                dbInfo.put("schema", connection.getSchema());
                dbInfo.put("maxConnections", metaData.getMaxConnections());
                dbInfo.put("defaultTransactionIsolation", metaData.getDefaultTransactionIsolation());
            } catch (Exception e) {
                // Some databases may not support all metadata
                log.debug("Could not retrieve some database metadata: {}", e.getMessage());
            }
            response.put("databaseInfo", dbInfo);
            
            response.put("timestamp", System.currentTimeMillis());
            
        } catch (Exception e) {
            log.error("Failed to get database status", e);
            response.put("status", "ERROR");
            response.put("error", e.getMessage());
            response.put("errorType", e.getClass().getSimpleName());
            response.put("timestamp", System.currentTimeMillis());
        }
        
        return ResponseEntity.ok(response);
    }
}

