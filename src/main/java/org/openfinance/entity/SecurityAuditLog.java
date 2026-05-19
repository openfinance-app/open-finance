package org.openfinance.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Entity representing a security audit log entry for authentication and access events.
 *
 * <p>Each entry captures who did what, from where, and when. Events are immutable once created — no
 * update operations should be performed on this table.
 *
 * <p>Requirement TASK-15.1.7: Security logging — authentication attempts and authorization
 * failures.
 *
 * @author Open-Finance Development Team
 * @version 1.0
 * @since 2026-03-20
 */
@Entity
@Table(name = "security_audit_log")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SecurityAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The user ID associated with this event. May be {@code null} for events that occur before a
     * user identity is established (e.g., failed login with unknown username).
     */
    @Column(name = "user_id")
    private Long userId;

    /**
     * The username supplied during the event. Stored directly (not as FK) so we can log events for
     * non-existent usernames too.
     */
    @Column(name = "username", nullable = false, length = 100)
    private String username;

    /** The type of security event. */
    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 50)
    private EventType eventType;

    /** The remote IP address of the request. */
    @Column(name = "ip_address", nullable = false, length = 45)
    private String ipAddress;

    /** Optional User-Agent header value for device/browser fingerprinting. */
    @Column(name = "user_agent", length = 512)
    private String userAgent;

    /** Optional free-text details about the event (e.g., reason for lockout). */
    @Column(name = "details", length = 1000)
    private String details;

    /** Timestamp when this log entry was created. Set automatically by Hibernate. */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Enumeration of security event types tracked in the audit log.
     *
     * <p>Requirement TASK-15.1.7: Security logging event categories.
     */
    public enum EventType {
        /** Successful user authentication. */
        LOGIN_SUCCESS,
        /** Failed authentication attempt. */
        LOGIN_FAILURE,
        /** User logout event. */
        LOGOUT,
        /** Account locked after exceeding the maximum failed login attempts. */
        ACCOUNT_LOCKED,
        /** Account manually or automatically unlocked. */
        ACCOUNT_UNLOCKED,
        /** User changed their login password. */
        PASSWORD_CHANGED,
        /** User changed their master (encryption) password. */
        MASTER_PASSWORD_CHANGED,
        /** New user registration. */
        REGISTRATION,
        /** Attempted access to a resource without proper authorisation. */
        UNAUTHORIZED_ACCESS
    }
}
