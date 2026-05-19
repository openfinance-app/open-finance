package org.openfinance.controller;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.crypto.SecretKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openfinance.dto.BudgetAlertRequest;
import org.openfinance.dto.BudgetAlertResponse;
import org.openfinance.dto.BudgetProgressResponse;
import org.openfinance.entity.BudgetAlert;
import org.openfinance.mapper.BudgetAlertMapper;
import org.openfinance.service.BudgetAlertService;
import org.openfinance.service.BudgetService;
import org.openfinance.util.ControllerUtil;
import org.openfinance.util.EncryptionUtil;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for managing budget alerts.
 *
 * <p>Provides endpoints for creating, retrieving, updating, and deleting budget alerts. Budget
 * alerts notify users when spending approaches or exceeds budgeted amounts.
 *
 * <p><strong>Endpoints:</strong>
 *
 * <ul>
 *   <li>POST /api/v1/budgets/alerts?budgetId={id} - Create alert
 *   <li>GET /api/v1/budgets/alerts/{budgetId} - Get alerts for budget
 *   <li>GET /api/v1/budgets/alerts/unread - Get unread alerts
 *   <li>GET /api/v1/budgets/alerts/unread/count - Get unread count
 *   <li>PUT /api/v1/budgets/alerts/{alertId} - Update alert
 *   <li>PUT /api/v1/budgets/alerts/{alertId}/read - Mark as read
 *   <li>PUT /api/v1/budgets/alerts/read-all - Mark all as read
 *   <li>DELETE /api/v1/budgets/alerts/{alertId} - Delete alert
 * </ul>
 *
 * <p><strong>Requirements:</strong>
 *
 * <ul>
 *   <li>REQ-2.9.4: Budget alert system
 *   <li>REQ-2.9.4.1: Alert creation and configuration
 *   <li>REQ-2.9.4.2: Alert notifications and tracking
 * </ul>
 *
 * @author Open-Finance Development Team
 * @version 1.0
 * @since 1.0
 */
@RestController
@RequestMapping("/api/v1/budgets/alerts")
@RequiredArgsConstructor
@Slf4j
public class BudgetAlertController {

    private final BudgetAlertService alertService;
    private final BudgetService budgetService;
    private final BudgetAlertMapper alertMapper;

    /**
     * Creates a new budget alert.
     *
     * <p>Example: POST /api/v1/budgets/alerts?budgetId=123 Body: {"threshold": 75.00, "isEnabled":
     * true}
     *
     * @param budgetId the ID of the budget to monitor
     * @param request the alert configuration
     * @param authentication the authenticated user
     * @return 201 Created with alert details
     */
    @PostMapping
    public ResponseEntity<BudgetAlertResponse> createAlert(
            @RequestParam Long budgetId,
            @Valid @RequestBody BudgetAlertRequest request,
            Authentication authentication) {

        Long userId = ControllerUtil.extractUserId(authentication);
        log.debug(
                "Creating alert for budget {} by user {}: threshold={}%",
                budgetId, userId, request.getThreshold());

        BudgetAlert alert = alertService.createAlert(budgetId, userId, request.getThreshold());

        if (request.getIsEnabled() != null && request.getIsEnabled() != alert.isEnabled()) {
            alert = alertService.updateAlert(alert.getId(), userId, null, request.getIsEnabled());
        }

        BudgetAlertResponse response = alertMapper.toResponse(alert);

        log.info(
                "Alert created: id={}, budgetId={}, threshold={}%",
                alert.getId(), budgetId, request.getThreshold());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Gets all alerts for a specific budget.
     *
     * <p>Example: GET /api/v1/budgets/alerts/123
     *
     * @param budgetId the ID of the budget
     * @param encryptionKeyHeader optional encryption key for current spending data
     * @param authentication the authenticated user
     * @return 200 OK with list of alerts
     */
    @GetMapping("/{budgetId}")
    public ResponseEntity<List<BudgetAlertResponse>> getAlertsByBudget(
            @PathVariable Long budgetId,
            @RequestHeader(value = "X-Encryption-Key", required = false) String encryptionKeyHeader,
            Authentication authentication) {

        Long userId = ControllerUtil.extractUserId(authentication);
        log.debug("Fetching alerts for budget {} by user {}", budgetId, userId);

        List<BudgetAlert> alerts = alertService.findAlertsByBudget(budgetId, userId);

        if (encryptionKeyHeader != null && !encryptionKeyHeader.isEmpty()) {
            SecretKey encryptionKey = EncryptionUtil.decodeEncryptionKey(encryptionKeyHeader);
            BudgetProgressResponse progress =
                    budgetService.calculateBudgetProgress(budgetId, userId, encryptionKey);

            List<BudgetAlertResponse> responses =
                    alerts.stream()
                            .map(
                                    alert ->
                                            alertMapper.toResponseWithProgress(
                                                    alert, progress.getPercentageSpent()))
                            .collect(Collectors.toList());

            return ResponseEntity.ok(responses);
        } else {
            List<BudgetAlertResponse> responses =
                    alerts.stream().map(alertMapper::toResponse).collect(Collectors.toList());

            return ResponseEntity.ok(responses);
        }
    }

    /**
     * Gets all unread alerts for the authenticated user.
     *
     * <p>Example: GET /api/v1/budgets/alerts/unread
     *
     * @param authentication the authenticated user
     * @return 200 OK with list of unread alerts
     */
    @GetMapping("/unread")
    public ResponseEntity<List<BudgetAlertResponse>> getUnreadAlerts(
            Authentication authentication) {
        Long userId = ControllerUtil.extractUserId(authentication);
        log.debug("Fetching unread alerts for user {}", userId);

        List<BudgetAlert> alerts = alertService.findUnreadAlerts(userId);
        List<BudgetAlertResponse> responses =
                alerts.stream().map(alertMapper::toResponse).collect(Collectors.toList());

        log.debug("Found {} unread alerts for user {}", responses.size(), userId);
        return ResponseEntity.ok(responses);
    }

    /**
     * Gets count of unread alerts for notification badge.
     *
     * <p>Example: GET /api/v1/budgets/alerts/unread/count
     *
     * @param authentication the authenticated user
     * @return 200 OK with unread count
     */
    @GetMapping("/unread/count")
    public ResponseEntity<Long> getUnreadAlertCount(Authentication authentication) {
        Long userId = ControllerUtil.extractUserId(authentication);
        long count = alertService.countUnreadAlerts(userId);

        log.debug("Unread alert count for user {}: {}", userId, count);
        return ResponseEntity.ok(count);
    }

    /**
     * Updates an existing budget alert.
     *
     * <p>Example: PUT /api/v1/budgets/alerts/{alertId} Body: {"threshold": 90.00, "isEnabled":
     * false}
     *
     * @param alertId the ID of the alert to update
     * @param request the update request
     * @param authentication the authenticated user
     * @return 200 OK with updated alert
     */
    @PutMapping("/{alertId}")
    public ResponseEntity<BudgetAlertResponse> updateAlert(
            @PathVariable UUID alertId,
            @Valid @RequestBody BudgetAlertRequest request,
            Authentication authentication) {

        Long userId = ControllerUtil.extractUserId(authentication);
        log.debug("Updating alert {} by user {}", alertId, userId);

        BudgetAlert alert =
                alertService.updateAlert(
                        alertId, userId, request.getThreshold(), request.getIsEnabled());
        BudgetAlertResponse response = alertMapper.toResponse(alert);

        log.info(
                "Alert updated: id={}, threshold={}%, enabled={}",
                alertId, alert.getThreshold(), alert.isEnabled());
        return ResponseEntity.ok(response);
    }

    /**
     * Marks an alert as read/acknowledged.
     *
     * <p>Example: PUT /api/v1/budgets/alerts/{alertId}/read
     *
     * @param alertId the ID of the alert
     * @param authentication the authenticated user
     * @return 204 No Content
     */
    @PutMapping("/{alertId}/read")
    public ResponseEntity<Void> markAlertAsRead(
            @PathVariable UUID alertId, Authentication authentication) {

        Long userId = ControllerUtil.extractUserId(authentication);
        log.debug("Marking alert {} as read by user {}", alertId, userId);

        alertService.markAlertAsRead(alertId, userId);

        log.debug("Alert {} marked as read", alertId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Marks all alerts as read for the authenticated user.
     *
     * <p>Example: PUT /api/v1/budgets/alerts/read-all
     *
     * @param authentication the authenticated user
     * @return 200 OK with count of alerts marked as read
     */
    @PutMapping("/read-all")
    public ResponseEntity<Integer> markAllAlertsAsRead(Authentication authentication) {
        Long userId = ControllerUtil.extractUserId(authentication);
        log.debug("Marking all alerts as read for user {}", userId);

        int count = alertService.markAllAlertsAsRead(userId);

        log.info("Marked {} alerts as read for user {}", count, userId);
        return ResponseEntity.ok(count);
    }

    /**
     * Deletes a budget alert.
     *
     * <p>Example: DELETE /api/v1/budgets/alerts/{alertId}
     *
     * @param alertId the ID of the alert to delete
     * @param authentication the authenticated user
     * @return 204 No Content
     */
    @DeleteMapping("/{alertId}")
    public ResponseEntity<Void> deleteAlert(
            @PathVariable UUID alertId, Authentication authentication) {

        Long userId = ControllerUtil.extractUserId(authentication);
        log.debug("Deleting alert {} by user {}", alertId, userId);

        alertService.deleteAlert(alertId, userId);

        log.info("Alert deleted: id={}", alertId);
        return ResponseEntity.noContent().build();
    }
}
