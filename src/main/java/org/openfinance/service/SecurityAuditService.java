package org.openfinance.service;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openfinance.entity.SecurityAuditLog;
import org.openfinance.entity.SecurityAuditLog.EventType;
import org.openfinance.repository.SecurityAuditLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for recording security audit events.
 *
 * <p>All write operations run with propagation {@code NOT_SUPPORTED}, meaning they execute
 * <em>outside</em> any active transaction and are auto-committed immediately by SQLite.
 *
 * <p>Why NOT_SUPPORTED instead of REQUIRES_NEW:<br>
 * SQLite WAL mode uses snapshot isolation. If a nested {@code REQUIRES_NEW} transaction writes to
 * the database while the outer transaction still holds a read snapshot, SQLite raises {@code
 * SQLITE_BUSY_SNAPSHOT} when the outer transaction later tries to commit its own writes. Running
 * the audit log write outside any transaction (auto-commit) avoids this conflict entirely, while
 * still guaranteeing the audit entry is persisted independently of the caller's outcome.
 *
 * <p>Requirement TASK-15.1.7: Security logging — authentication attempts and authorization
 * failures.
 *
 * @author Open-Finance Development Team
 * @version 1.1
 * @since 2026-03-20
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SecurityAuditService {

    private final SecurityAuditLogRepository auditLogRepository;

    /**
     * Records a security event for the given username.
     *
     * <p>This method runs with propagation {@code NOT_SUPPORTED} — it is executed outside any
     * active transaction and is auto-committed by SQLite immediately. This prevents {@code
     * SQLITE_BUSY_SNAPSHOT} errors that occur in WAL mode when a nested {@code REQUIRES_NEW}
     * transaction writes while the caller's read snapshot is still open.
     *
     * @param userId the database user ID (may be {@code null} for pre-authentication events)
     * @param username the username involved in the event
     * @param eventType the type of security event
     * @param request the current HTTP request (used to extract IP and User-Agent)
     * @param details optional additional details about the event
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void logEvent(
            Long userId,
            String username,
            EventType eventType,
            HttpServletRequest request,
            String details) {

        String ipAddress = resolveClientIp(request);
        String userAgent = request != null ? request.getHeader("User-Agent") : null;
        if (userAgent != null && userAgent.length() > 512) {
            userAgent = userAgent.substring(0, 512);
        }

        SecurityAuditLog entry =
                SecurityAuditLog.builder()
                        .userId(userId)
                        .username(username != null ? username : "unknown")
                        .eventType(eventType)
                        .ipAddress(ipAddress)
                        .userAgent(userAgent)
                        .details(details)
                        .build();

        auditLogRepository.save(entry);

        log.info(
                "Security audit: event={}, user={}, ip={}, details={}",
                eventType,
                username,
                ipAddress,
                details);
    }

    /**
     * Records a security event without an HTTP request context (e.g., scheduled jobs).
     *
     * <p>Runs outside any active transaction (auto-committed). See {@link #logEvent(Long, String,
     * EventType, HttpServletRequest, String)} for details.
     *
     * @param userId the database user ID (may be {@code null})
     * @param username the username involved in the event
     * @param eventType the type of security event
     * @param details optional additional details about the event
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void logEvent(Long userId, String username, EventType eventType, String details) {
        logEvent(userId, username, eventType, null, details);
    }

    /**
     * Extracts the real client IP address, handling reverse-proxy {@code X-Forwarded-For} and
     * {@code X-Real-IP} headers.
     *
     * @param request the current HTTP request; may be {@code null}
     * @return the resolved client IP address, or {@code "unknown"} when unavailable
     */
    private String resolveClientIp(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isBlank()) {
            return xRealIp.trim();
        }
        return request.getRemoteAddr();
    }
}
