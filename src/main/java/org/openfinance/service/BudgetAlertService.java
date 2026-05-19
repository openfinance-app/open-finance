package org.openfinance.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import javax.crypto.SecretKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openfinance.dto.BudgetProgressResponse;
import org.openfinance.entity.Budget;
import org.openfinance.entity.BudgetAlert;
import org.openfinance.repository.BudgetAlertRepository;
import org.openfinance.repository.BudgetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing budget alerts and monitoring budget thresholds.
 *
 * <p>This service handles:
 *
 * <ul>
 *   <li>Checking budget progress against alert thresholds
 *   <li>Triggering alerts when thresholds are exceeded
 *   <li>Managing alert lifecycle (trigger, read, reset)
 *   <li>Integration with transaction creation flow
 * </ul>
 *
 * <p><strong>Alert Trigger Logic:</strong> After each transaction creation, this service:
 *
 * <ol>
 *   <li>Finds all enabled alerts for user's budgets
 *   <li>Calculates current spending percentage for each budget
 *   <li>Triggers alert if spending >= threshold and not recently triggered
 *   <li>Marks alert as unread for user notification
 * </ol>
 *
 * <p><strong>Requirements:</strong>
 *
 * <ul>
 *   <li>REQ-2.9.4: Budget alert system
 *   <li>REQ-2.9.4.1: Trigger alerts when spending exceeds percentage
 *   <li>REQ-2.9.4.2: Track alert history and prevent duplicates
 * </ul>
 *
 * @author Open-Finance Development Team
 * @version 1.0
 * @since 1.0
 */
@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class BudgetAlertService {

    private final BudgetAlertRepository alertRepository;
    private final BudgetRepository budgetRepository;
    private final BudgetService budgetService;

    /**
     * Minimum time between alert triggers for the same alert (in hours). Prevents spamming user
     * with duplicate alerts.
     */
    private static final int ALERT_COOLDOWN_HOURS = 24;

    /**
     * Checks all budget alerts after a transaction is created.
     *
     * <p>This method should be called after every transaction creation to ensure budget monitoring
     * is up-to-date. It checks all active budgets for the user and triggers alerts if spending
     * thresholds are exceeded.
     *
     * <p><strong>Example:</strong> User creates a $50 grocery transaction. System checks grocery
     * budget: spent $450 of $500 (90%). Alert configured for 80% threshold triggers: "Warning: 90%
     * of grocery budget used".
     *
     * <p>Requirement REQ-2.9.4.1: Trigger alerts after transaction creation
     *
     * @param userId the ID of the user who created the transaction
     * @param encryptionKey the encryption key for decrypting budget amounts
     * @return list of triggered alerts (for notification display)
     * @throws IllegalArgumentException if userId or encryptionKey is null
     */
    public List<BudgetAlert> checkBudgetAlertsAfterTransaction(
            Long userId, SecretKey encryptionKey) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        if (encryptionKey == null) {
            throw new IllegalArgumentException("Encryption key cannot be null");
        }

        log.debug("Checking budget alerts for user: {}", userId);

        // Find all enabled alerts for this user
        List<BudgetAlert> enabledAlerts = alertRepository.findEnabledAlertsByUserId(userId);
        if (enabledAlerts.isEmpty()) {
            log.debug("No enabled alerts found for user: {}", userId);
            return List.of();
        }

        log.debug("Found {} enabled alerts for user: {}", enabledAlerts.size(), userId);

        // Group alerts by budget ID for efficient processing
        List<BudgetAlert> triggeredAlerts = new ArrayList<>();
        Long currentBudgetId = null;
        BudgetProgressResponse currentProgress = null;

        for (BudgetAlert alert : enabledAlerts) {
            Long budgetId = alert.getBudget().getId();

            // Fetch budget progress (cache per budget to avoid redundant calls)
            if (!budgetId.equals(currentBudgetId)) {
                currentBudgetId = budgetId;
                try {
                    currentProgress =
                            budgetService.calculateBudgetProgress(budgetId, userId, encryptionKey);
                } catch (Exception e) {
                    log.error(
                            "Error fetching budget progress for budget {}: {}",
                            budgetId,
                            e.getMessage());
                    continue;
                }
            }

            // Check if alert should trigger
            if (shouldTriggerAlert(alert, currentProgress)) {
                alert.trigger();
                alertRepository.save(alert);
                triggeredAlerts.add(alert);
                log.info(
                        "Triggered alert {} for budget {} (threshold: {}%, spent: {}%)",
                        alert.getId(),
                        budgetId,
                        alert.getThreshold(),
                        currentProgress.getPercentageSpent());
            }
        }

        if (!triggeredAlerts.isEmpty()) {
            log.info("Triggered {} budget alerts for user: {}", triggeredAlerts.size(), userId);
        }

        return triggeredAlerts;
    }

    /**
     * Determines if an alert should trigger based on budget progress.
     *
     * <p>Alert triggers if ALL conditions are met:
     *
     * <ul>
     *   <li>Alert is enabled
     *   <li>Spending percentage >= alert threshold
     *   <li>Alert not triggered recently (cooldown period passed)
     * </ul>
     *
     * @param alert the budget alert to check
     * @param progress the current budget progress
     * @return true if alert should trigger, false otherwise
     */
    private boolean shouldTriggerAlert(BudgetAlert alert, BudgetProgressResponse progress) {
        if (!alert.isEnabled()) {
            return false;
        }

        // Check if spending exceeds threshold
        BigDecimal spentPercentage = progress.getPercentageSpent();
        if (spentPercentage.compareTo(alert.getThreshold()) < 0) {
            return false;
        }

        // Check cooldown period to avoid duplicate alerts
        if (alert.getLastTriggered() != null) {
            LocalDateTime cooldownExpiry = alert.getLastTriggered().plusHours(ALERT_COOLDOWN_HOURS);
            if (LocalDateTime.now().isBefore(cooldownExpiry)) {
                log.debug(
                        "Alert {} in cooldown period (last triggered: {})",
                        alert.getId(),
                        alert.getLastTriggered());
                return false;
            }
        }

        return true;
    }

    /**
     * Creates a new budget alert with specified threshold.
     *
     * <p>Common threshold values:
     *
     * <ul>
     *   <li>50.00 - Early warning at halfway point
     *   <li>75.00 - Standard warning (3/4 spent)
     *   <li>90.00 - Critical warning (near limit)
     *   <li>100.00 - Budget exceeded
     * </ul>
     *
     * <p>Requirement REQ-2.9.4.2: Create and configure alerts
     *
     * @param budgetId the ID of the budget to monitor
     * @param userId the ID of the user (for authorization)
     * @param threshold the percentage threshold (1.00-150.00)
     * @return the created alert
     * @throws IllegalArgumentException if budget doesn't exist or doesn't belong to user
     * @throws IllegalStateException if alert already exists for this budget and threshold
     */
    public BudgetAlert createAlert(Long budgetId, Long userId, BigDecimal threshold) {
        if (budgetId == null || userId == null || threshold == null) {
            throw new IllegalArgumentException("BudgetId, userId, and threshold cannot be null");
        }

        // Validate threshold range
        if (threshold.compareTo(BigDecimal.ONE) < 0
                || threshold.compareTo(new BigDecimal("150.00")) > 0) {
            throw new IllegalArgumentException("Threshold must be between 1.00 and 150.00");
        }

        // Verify budget exists and belongs to user
        Budget budget =
                budgetRepository
                        .findByIdAndUserId(budgetId, userId)
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "Budget not found or access denied"));

        // Check if alert already exists
        alertRepository
                .findByBudgetIdAndThreshold(budgetId, threshold)
                .ifPresent(
                        existing -> {
                            throw new IllegalStateException(
                                    "Alert already exists for this budget and threshold");
                        });

        // Create alert
        BudgetAlert alert =
                BudgetAlert.builder()
                        .budget(budget)
                        .threshold(threshold)
                        .isEnabled(true)
                        .isRead(true) // Not triggered yet, so marked as read
                        .build();

        BudgetAlert saved = alertRepository.save(alert);
        log.info(
                "Created alert {} for budget {} with threshold {}%",
                saved.getId(), budgetId, threshold);

        return saved;
    }

    /**
     * Updates an existing budget alert.
     *
     * <p>Can update threshold and enabled status. Cannot change associated budget.
     *
     * @param alertId the ID of the alert to update
     * @param userId the ID of the user (for authorization)
     * @param threshold the new threshold (optional, null = no change)
     * @param isEnabled the new enabled status (optional, null = no change)
     * @return the updated alert
     * @throws IllegalArgumentException if alert doesn't exist or doesn't belong to user
     */
    public BudgetAlert updateAlert(
            java.util.UUID alertId, Long userId, BigDecimal threshold, Boolean isEnabled) {
        if (alertId == null || userId == null) {
            throw new IllegalArgumentException("AlertId and userId cannot be null");
        }

        // Find alert and verify ownership
        BudgetAlert alert =
                alertRepository
                        .findById(alertId)
                        .orElseThrow(() -> new IllegalArgumentException("Alert not found"));

        if (!alert.getBudget().getUserId().equals(userId)) {
            throw new IllegalArgumentException("Access denied: alert belongs to different user");
        }

        // Update threshold if provided
        if (threshold != null) {
            if (threshold.compareTo(BigDecimal.ONE) < 0
                    || threshold.compareTo(new BigDecimal("150.00")) > 0) {
                throw new IllegalArgumentException("Threshold must be between 1.00 and 150.00");
            }

            // Check for duplicate threshold
            BigDecimal finalThreshold = threshold;
            alertRepository
                    .findByBudgetIdAndThreshold(alert.getBudget().getId(), threshold)
                    .ifPresent(
                            existing -> {
                                if (!existing.getId().equals(alertId)) {
                                    throw new IllegalStateException(
                                            "Another alert already exists with threshold "
                                                    + finalThreshold);
                                }
                            });

            alert.setThreshold(threshold);
        }

        // Update enabled status if provided
        if (isEnabled != null) {
            alert.setEnabled(isEnabled);
        }

        BudgetAlert updated = alertRepository.save(alert);
        log.info(
                "Updated alert {}: threshold={}, enabled={}",
                alertId,
                updated.getThreshold(),
                updated.isEnabled());

        return updated;
    }

    /**
     * Deletes a budget alert.
     *
     * @param alertId the ID of the alert to delete
     * @param userId the ID of the user (for authorization)
     * @throws IllegalArgumentException if alert doesn't exist or doesn't belong to user
     */
    public void deleteAlert(java.util.UUID alertId, Long userId) {
        if (alertId == null || userId == null) {
            throw new IllegalArgumentException("AlertId and userId cannot be null");
        }

        BudgetAlert alert =
                alertRepository
                        .findById(alertId)
                        .orElseThrow(() -> new IllegalArgumentException("Alert not found"));

        if (!alert.getBudget().getUserId().equals(userId)) {
            throw new IllegalArgumentException("Access denied: alert belongs to different user");
        }

        alertRepository.delete(alert);
        log.info("Deleted alert {} for user {}", alertId, userId);
    }

    /**
     * Finds all alerts for a specific budget.
     *
     * @param budgetId the ID of the budget
     * @param userId the ID of the user (for authorization)
     * @return list of alerts for the budget
     * @throws IllegalArgumentException if budget doesn't belong to user
     */
    public List<BudgetAlert> findAlertsByBudget(Long budgetId, Long userId) {
        if (budgetId == null || userId == null) {
            throw new IllegalArgumentException("BudgetId and userId cannot be null");
        }

        // Verify budget ownership
        budgetRepository
                .findByIdAndUserId(budgetId, userId)
                .orElseThrow(
                        () -> new IllegalArgumentException("Budget not found or access denied"));

        return alertRepository.findByBudgetId(budgetId);
    }

    /**
     * Finds all unread alerts for a user.
     *
     * <p>Used to display notification badge and alert list in UI.
     *
     * @param userId the ID of the user
     * @return list of unread alerts
     */
    public List<BudgetAlert> findUnreadAlerts(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }

        return alertRepository.findUnreadAlertsByUserId(userId);
    }

    /**
     * Counts unread alerts for a user.
     *
     * <p>Used for notification badge count in UI.
     *
     * @param userId the ID of the user
     * @return count of unread alerts
     */
    public long countUnreadAlerts(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }

        return alertRepository.countUnreadAlertsByUserId(userId);
    }

    /**
     * Marks an alert as read/acknowledged.
     *
     * @param alertId the ID of the alert
     * @param userId the ID of the user (for authorization)
     * @throws IllegalArgumentException if alert doesn't exist or doesn't belong to user
     */
    public void markAlertAsRead(java.util.UUID alertId, Long userId) {
        if (alertId == null || userId == null) {
            throw new IllegalArgumentException("AlertId and userId cannot be null");
        }

        BudgetAlert alert =
                alertRepository
                        .findById(alertId)
                        .orElseThrow(() -> new IllegalArgumentException("Alert not found"));

        if (!alert.getBudget().getUserId().equals(userId)) {
            throw new IllegalArgumentException("Access denied: alert belongs to different user");
        }

        alert.markAsRead();
        alertRepository.save(alert);
        log.debug("Marked alert {} as read for user {}", alertId, userId);
    }

    /**
     * Marks all alerts as read for a user.
     *
     * <p>Used when user clicks "Mark all as read" in notifications.
     *
     * @param userId the ID of the user
     * @return number of alerts marked as read
     */
    public int markAllAlertsAsRead(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }

        int count = alertRepository.markAllAlertsAsRead(userId);
        log.info("Marked {} alerts as read for user {}", count, userId);

        return count;
    }

    /**
     * Resets alerts when budget period rolls over.
     *
     * <p>Clears lastTriggered timestamp and marks as read, allowing alerts to trigger again in the
     * new budget period.
     *
     * @param budgetIds list of budget IDs that have started new period
     * @return number of alerts reset
     */
    public int resetAlertsForNewPeriod(List<Long> budgetIds) {
        if (budgetIds == null || budgetIds.isEmpty()) {
            return 0;
        }

        int count = alertRepository.resetAlertsForBudgets(budgetIds);
        log.info("Reset {} alerts for {} budgets entering new period", count, budgetIds.size());

        return count;
    }
}
