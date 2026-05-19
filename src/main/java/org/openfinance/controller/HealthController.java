package org.openfinance.controller;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Health Check Controller
 *
 * <p>Provides basic health check and application status endpoints for monitoring and diagnostics.
 *
 * <p>Requirements: REQ-1.1 (Basic API), REQ-3.2.2 (Availability)
 *
 * @version 0.1.0
 * @since 2026-01-30
 */
@RestController
@RequestMapping("/api/v1/health")
@Slf4j
public class HealthController {

    @Value("${spring.application.name}")
    private String applicationName;

    @Value("${application.version}")
    private String applicationVersion;

    /**
     * Health check endpoint Returns application status, version, and current timestamp.
     *
     * @return ResponseEntity containing application health information
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> health() {
        log.debug("Health check endpoint called");

        Map<String, Object> response = new HashMap<>();
        response.put("status", "UP");
        response.put("application", applicationName);
        response.put("version", applicationVersion);
        response.put("timestamp", LocalDateTime.now());
        response.put("java", System.getProperty("java.version"));

        return ResponseEntity.ok(response);
    }

    /**
     * Detailed application information endpoint Returns comprehensive application metadata
     * including runtime information.
     *
     * @return ResponseEntity containing detailed application information
     */
    @GetMapping("/info")
    public ResponseEntity<Map<String, Object>> info() {
        log.debug("Info endpoint called");

        Map<String, Object> response = new HashMap<>();
        response.put(
                "application",
                Map.of(
                        "name", applicationName,
                        "version", applicationVersion,
                        "description", "Personal Wealth Management Application"));

        response.put(
                "runtime",
                Map.of(
                        "java", System.getProperty("java.version"),
                        "javaVendor", System.getProperty("java.vendor"),
                        "os", System.getProperty("os.name"),
                        "osVersion", System.getProperty("os.version"),
                        "osArch", System.getProperty("os.arch")));

        response.put(
                "memory",
                Map.of(
                        "totalMemory", Runtime.getRuntime().totalMemory(),
                        "freeMemory", Runtime.getRuntime().freeMemory(),
                        "maxMemory", Runtime.getRuntime().maxMemory(),
                        "availableProcessors", Runtime.getRuntime().availableProcessors()));

        response.put("timestamp", LocalDateTime.now());

        return ResponseEntity.ok(response);
    }
}
