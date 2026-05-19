package org.openfinance.repository;

import java.time.LocalDateTime;
import java.util.List;
import org.openfinance.entity.SecurityAuditLog;
import org.openfinance.entity.SecurityAuditLog.EventType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * JPA repository for {@link SecurityAuditLog} entities.
 *
 * <p>Provides query methods for retrieving security events by user, type, IP address, and time
 * range. Audit log entries are immutable — no modification queries are provided.
 *
 * <p>Requirement TASK-15.1.7: Security audit log persistence.
 *
 * @author Open-Finance Development Team
 * @version 1.0
 * @since 2026-03-20
 */
@Repository
public interface SecurityAuditLogRepository extends JpaRepository<SecurityAuditLog, Long> {

    /**
     * Finds all audit log entries for a specific user, ordered by most recent first.
     *
     * @param userId the user ID to filter by
     * @param pageable pagination parameters
     * @return page of audit log entries
     */
    Page<SecurityAuditLog> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    /**
     * Finds all audit log entries of a given event type, ordered by most recent first.
     *
     * @param eventType the event type to filter by
     * @param pageable pagination parameters
     * @return page of audit log entries
     */
    Page<SecurityAuditLog> findByEventTypeOrderByCreatedAtDesc(
            EventType eventType, Pageable pageable);

    /**
     * Counts failed login attempts for a given username since the specified timestamp.
     *
     * <p>Used by the account lockout logic to determine whether the threshold has been exceeded.
     * Requirement TASK-15.1.8: Account lockout — failed attempt counting.
     *
     * @param username the username to check
     * @param since the start of the time window
     * @return number of failed login events within the window
     */
    @Query(
            "SELECT COUNT(e) FROM SecurityAuditLog e "
                    + "WHERE e.username = :username "
                    + "AND e.eventType = 'LOGIN_FAILURE' "
                    + "AND e.createdAt >= :since")
    long countFailedLoginsForUserSince(
            @Param("username") String username, @Param("since") LocalDateTime since);

    /**
     * Finds the most recent audit entries from a given IP address across all event types.
     *
     * @param ipAddress the IP address to query
     * @param limit maximum number of entries to return
     * @return list of audit log entries from that IP, most recent first
     */
    @Query(
            "SELECT e FROM SecurityAuditLog e WHERE e.ipAddress = :ipAddress "
                    + "ORDER BY e.createdAt DESC")
    List<SecurityAuditLog> findRecentByIpAddress(
            @Param("ipAddress") String ipAddress, Pageable limit);

    /**
     * Returns all entries created between two timestamps, ordered by most recent first.
     *
     * @param from the start of the time range (inclusive)
     * @param to the end of the time range (inclusive)
     * @param pageable pagination parameters
     * @return page of audit log entries within the time range
     */
    Page<SecurityAuditLog> findByCreatedAtBetweenOrderByCreatedAtDesc(
            LocalDateTime from, LocalDateTime to, Pageable pageable);
}
