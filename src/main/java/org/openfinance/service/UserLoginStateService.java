package org.openfinance.service;

import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openfinance.entity.User;
import org.openfinance.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service responsible for persisting login-state changes on the {@link User} entity.
 *
 * <p>Each public method in this service opens its <em>own, fresh</em> {@link Transactional} context
 * (default {@link Propagation#REQUIRED} but always called from a caller that carries {@code
 * NOT_SUPPORTED}, so a brand-new transaction is started). This guarantees that the JDBC connection
 * used for the {@code UPDATE users} statement has <strong>never</strong> executed a prior read
 * inside the same WAL snapshot, eliminating the root cause of {@code SQLITE_BUSY_SNAPSHOT} errors.
 *
 * <h3>Why a separate service?</h3>
 *
 * Spring AOP proxies only intercept calls through the proxy; {@code @Transactional} on a {@code
 * private} or self-invoked method has no effect. By extracting writes into a dedicated bean the
 * proxy boundary is preserved and each write method genuinely starts a new transaction.
 *
 * <p>Requirement TASK-15.1.7: Track last login IP and timestamp on successful authentication.
 *
 * <p>Requirement TASK-15.1.8: Reset failed-login counter on success; lock account after N
 * consecutive failures.
 *
 * @author Open-Finance Development Team
 * @version 1.0
 * @since 2026-03-20
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserLoginStateService {

    private final UserRepository userRepository;

    /**
     * Resets the failed-login counter, clears any lockout, and records the successful login
     * metadata (timestamp and IP address) for the given user.
     *
     * <p>Runs in a fresh {@link Propagation#REQUIRED} transaction. Because the caller ({@link
     * AuthService#login}) runs with {@code NOT_SUPPORTED}, no outer transaction is active at call
     * time, so a brand-new connection/transaction is always used — no stale WAL snapshot.
     *
     * @param userId the ID of the authenticated user
     * @param ipAddress the client IP address resolved from the HTTP request headers
     * @throws jakarta.persistence.PersistenceException if the database write fails
     * @throws IllegalArgumentException if the user is not found
     */
    @Transactional
    public void recordLoginSuccess(Long userId, String ipAddress) {
        User user =
                userRepository
                        .findById(userId)
                        .orElseThrow(
                                () -> new IllegalArgumentException("User not found: " + userId));

        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(LocalDateTime.now());
        user.setLastLoginIp(ipAddress);
        userRepository.save(user);
        log.debug("Recorded login success for user '{}' from IP {}", user.getUsername(), ipAddress);
    }

    /**
     * Increments the failed-login counter and, if {@code newAttempts >= maxAttempts}, locks the
     * account for {@code lockoutMinutes} minutes.
     *
     * <p>Runs in a fresh {@link Propagation#REQUIRED} transaction (same WAL-safety reasoning as
     * {@link #recordLoginSuccess}).
     *
     * @param userId the ID of the user whose counter should be incremented
     * @param maxAttempts the threshold at which the account is locked
     * @param lockoutMinutes duration (in minutes) for which the account is locked once threshold is
     *     reached
     * @return the updated number of failed login attempts after this call
     * @throws jakarta.persistence.PersistenceException if the database write fails
     * @throws IllegalArgumentException if the user is not found
     */
    @Transactional
    public int recordLoginFailure(Long userId, int maxAttempts, int lockoutMinutes) {
        User user =
                userRepository
                        .findById(userId)
                        .orElseThrow(
                                () -> new IllegalArgumentException("User not found: " + userId));

        int newAttempts = user.getFailedLoginAttempts() + 1;
        user.setFailedLoginAttempts(newAttempts);

        if (newAttempts >= maxAttempts) {
            LocalDateTime lockUntil = LocalDateTime.now().plusMinutes(lockoutMinutes);
            user.setLockedUntil(lockUntil);
            log.warn(
                    "Account '{}' locked until {} after {} failed attempts",
                    user.getUsername(),
                    lockUntil,
                    newAttempts);
        }

        userRepository.save(user);
        log.debug(
                "Recorded login failure for user '{}': attempt {}/{}",
                user.getUsername(),
                newAttempts,
                maxAttempts);
        return newAttempts;
    }
}
