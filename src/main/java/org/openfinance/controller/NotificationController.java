package org.openfinance.controller;

import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openfinance.dto.NotificationResponse;
import org.openfinance.service.ExchangeRateService;
import org.openfinance.service.NotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for system notifications.
 *
 * <p>Provides endpoints for retrieving various types of notifications that require user attention,
 * and for triggering in-app actions surfaced by those notifications (e.g. refreshing exchange
 * rates).
 */
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Slf4j
public class NotificationController {

    private final NotificationService notificationService;
    private final ExchangeRateService exchangeRateService;

    /**
     * Gets all notifications for the authenticated user.
     *
     * @param userId the user ID from authentication context
     * @return list of notifications
     */
    @GetMapping
    public ResponseEntity<List<NotificationResponse>> getNotifications(
            @RequestAttribute("userId") Long userId) {

        log.debug("GET /api/v1/notifications - userId: {}", userId);

        List<NotificationResponse> notifications = notificationService.getNotifications(userId);

        log.debug("Returning {} notifications for user {}", notifications.size(), userId);

        return ResponseEntity.ok(notifications);
    }

    /**
     * Gets the count of notifications for the authenticated user.
     *
     * @param userId the user ID from authentication context
     * @return notification count
     */
    @GetMapping("/count")
    public ResponseEntity<Integer> getNotificationCount(@RequestAttribute("userId") Long userId) {

        log.debug("GET /api/v1/notifications/count - userId: {}", userId);

        List<NotificationResponse> notifications = notificationService.getNotifications(userId);
        int count = notifications.size();

        log.debug("Returning notification count {} for user {}", count, userId);

        return ResponseEntity.ok(count);
    }

    /**
     * Triggers an immediate exchange rate refresh.
     *
     * <p>This endpoint is surfaced as an inline action on the {@code STALE_EXCHANGE_RATES}
     * notification so that any authenticated user can refresh stale rates directly from the
     * notification dropdown without navigating to the currencies management page.
     *
     * @return a JSON object with {@code updatedCount} indicating how many rates were stored, and a
     *     human-readable {@code message}
     */
    @PostMapping("/actions/update-exchange-rates")
    public ResponseEntity<Map<String, Object>> triggerExchangeRateUpdate() {
        log.info("Exchange rate update triggered via notification action");

        int updatedCount = exchangeRateService.updateExchangeRates();

        log.info("Exchange rate update completed: {} rates refreshed", updatedCount);

        return ResponseEntity.ok(
                Map.of(
                        "message",
                        "Exchange rates updated successfully",
                        "updatedCount",
                        updatedCount));
    }
}
