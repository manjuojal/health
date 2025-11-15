package com.smartuser.healthmonitor.advice;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;

import com.smartuser.healthmonitor.notifier.HealthStatusNotifier;

import lombok.extern.slf4j.Slf4j;

/**
 * Global exception handler to catch uncaught REST exceptions
 */
@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    private final HealthStatusNotifier notifier;

    public GlobalExceptionHandler(HealthStatusNotifier notifier) {
        this.notifier = notifier;
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRuntimeException(RuntimeException ex, WebRequest request) {
        String path = request.getDescription(false).replace("uri=", "");
        
        // Ignore favicon.ico and other static resource requests
        if (shouldIgnoreException(ex, path)) {
            log.debug("Ignoring exception for static resource: {}", path);
            Map<String, Object> body = new HashMap<>();
            body.put("status", HttpStatus.NOT_FOUND.value());
            body.put("error", "Not Found");
            body.put("message", "Static resource not found");
            body.put("path", path);
            return new ResponseEntity<>(body, HttpStatus.NOT_FOUND);
        }
        
        String message = "Runtime exception: " + ex.getMessage();
        notifier.notifyError(message, ex);

        Map<String, Object> body = new HashMap<>();
        body.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
        body.put("error", ex.getClass().getSimpleName());
        body.put("message", ex.getMessage());
        body.put("path", path);
        body.put("timestamp", System.currentTimeMillis());

        return new ResponseEntity<>(body, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleException(Exception ex, WebRequest request) {
        String path = request.getDescription(false).replace("uri=", "");
        
        // Handle NoResourceFoundException (Spring 6+) for missing static resources
        String exceptionClassName = ex.getClass().getName();
        if (exceptionClassName.equals("org.springframework.web.servlet.resource.NoResourceFoundException")) {
            log.debug("Static resource not found: {}", path);
            Map<String, Object> body = new HashMap<>();
            body.put("status", HttpStatus.NOT_FOUND.value());
            body.put("error", "Not Found");
            body.put("message", "Static resource not found");
            body.put("path", path);
            return new ResponseEntity<>(body, HttpStatus.NOT_FOUND);
        }
        
        // Ignore favicon.ico and other static resource requests
        if (shouldIgnoreException(ex, path)) {
            log.debug("Ignoring exception for static resource: {}", path);
            Map<String, Object> body = new HashMap<>();
            body.put("status", HttpStatus.NOT_FOUND.value());
            body.put("error", "Not Found");
            body.put("message", "Static resource not found");
            body.put("path", path);
            return new ResponseEntity<>(body, HttpStatus.NOT_FOUND);
        }
        
        String message = "Uncaught exception: " + ex.getMessage();
        notifier.notifyError(message, ex);

        Map<String, Object> body = new HashMap<>();
        body.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
        body.put("error", ex.getClass().getSimpleName());
        body.put("message", ex.getMessage());
        body.put("path", path);
        body.put("timestamp", System.currentTimeMillis());

        return new ResponseEntity<>(body, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /**
     * Check if exception should be ignored (not tracked as error)
     * Ignores favicon.ico and other static resource requests
     */
    private boolean shouldIgnoreException(Exception ex, String path) {
        if (path == null) {
            return false;
        }
        
        String lowerPath = path.toLowerCase();
        String message = ex.getMessage();
        
        // Ignore favicon.ico requests
        if (lowerPath.contains("favicon.ico")) {
            return true;
        }
        
        // Ignore static resource exceptions (including NoResourceFoundException messages)
        if (message != null && (
            message.contains("favicon.ico") ||
            message.contains("No static resource") ||
            message.contains("Static resource") ||
            message.contains("NoResourceFoundException") ||
            (message.contains("No handler found") && lowerPath.contains("/favicon"))
        )) {
            return true;
        }
        
        // Check if it's a NoResourceFoundException type (Spring 6+)
        String exceptionClassName = ex.getClass().getName();
        if (exceptionClassName.equals("org.springframework.web.servlet.resource.NoResourceFoundException")) {
            return true;
        }
        
        // Ignore common static resource paths
        String[] staticResourcePatterns = {
            "/favicon.ico",
            "/robots.txt",
            "/.well-known/",
            "/static/",
            "/public/",
            "/assets/"
        };
        
        for (String pattern : staticResourcePatterns) {
            if (lowerPath.contains(pattern)) {
                return true;
            }
        }
        
        return false;
    }
}

