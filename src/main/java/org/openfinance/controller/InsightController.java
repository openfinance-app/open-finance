package org.openfinance.controller;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import javax.crypto.SecretKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openfinance.dto.InsightResponse;
import org.openfinance.entity.User;
import org.openfinance.service.InsightService;
import org.openfinance.util.EncryptionUtil;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for AI-powered financial insights.
 *
 * <p>Provides endpoints for generating, retrieving, and managing personalized financial insights
 * based on user's spending patterns, budgets, and account activity.
 *
 * <p><strong>Features:</strong>
 *
 * <ul>
 *   <li>Generate fresh insights by analyzing financial data
 *   <li>Retrieve all active insights for dashboard display
 *   <li>Get top N priority insights for quick overview
 *   <li>Dismiss insights to hide them from main view
 * </ul>
 *
 * <p><strong>Endpoints:</strong>
 *
 * <ul>
 *   <li>POST /api/v1/insights/generate - Generate new insights
 *   <li>GET /api/v1/insights - List all active insights
 *   <li>GET /api/v1/insights/top/{limit} - Get top N priority insights
 *   <li>POST /api/v1/insights/{id}/dismiss - Dismiss an insight
 * </ul>
 *
 * <p><strong>Security:</strong>
 *
 * <ul>
 *   <li>All endpoints require JWT authentication
 *   <li>Encryption key required for generate endpoint (X-Encryption-Key header)
 *   <li>Users can only access their own insights
 *   <li>Authorization checks prevent cross-user access
 * </ul>
 *
 * <p><strong>Requirements:</strong>
 *
 * <ul>
 *   <li>REQ-2.17.4: AI-powered insights generation and display
 *   <li>REQ-2.18: Data encryption at rest
 *   <li>REQ-3.2: Authorization checks
 * </ul>
 *
 * @see InsightService
 * @see InsightResponse
 * @author Open-Finance Development Team
 * @version 1.0
 * @since Sprint 11 - AI Assistant Integration (Task 11.4)
 */
@RestController
@RequestMapping("/api/v1/insights")
@RequiredArgsConstructor
@Slf4j
public class InsightController {

    private static final String ENCRYPTION_KEY_HEADER = "X-Encryption-Key";

    private final InsightService insightService;

    /**
     * Generate fresh insights for the authenticated user.
     *
     * <p>This endpoint analyzes the user's financial data (transactions, budgets, accounts, etc.)
     * and generates actionable insights. Existing active insights are deleted before generating new
     * ones.
     *
     * <p><strong>Performance Note:</strong> This is a computationally intensive operation that
     * should not be called on every page load. Use {@code GET /api/v1/insights} instead for regular
     * insight retrieval (cached).
     *
     * <p><strong>Request Headers:</strong>
     *
     * <ul>
     *   <li>Authorization: Bearer {jwt_token}
     *   <li>X-Encryption-Key: {base64_encoded_key}
     * </ul>
     *
     * <p><strong>Success Response (HTTP 201 Created):</strong>
     *
     * <pre>{@code
     * [
     *   {
     *     "id": 1,
     *     "type": "SPENDING_ANOMALY",
     *     "title": "Unusual Spending in Groceries",
     *     "description": "Your groceries spending is 45% higher than last month...",
     *     "priority": "HIGH",
     *     "dismissed": false,
     *     "createdAt": "2026-02-03T14:30:00"
     *   },
     *   {
     *     "id": 2,
     *     "type": "BUDGET_WARNING",
     *     "title": "Entertainment Budget Approaching Limit",
     *     "description": "You've spent 80% of your entertainment budget...",
     *     "priority": "MEDIUM",
     *     "dismissed": false,
     *     "createdAt": "2026-02-03T14:30:00"
     *   }
     * ]
     * }</pre>
     *
     * <p>Requirement REQ-2.17.4: Generate AI-powered insights
     *
     * @param encodedKey Base64-encoded encryption key from header
     * @param authentication Spring Security authentication object
     * @return HTTP 201 Created with list of newly generated insights
     * @throws IllegalArgumentException if encryption key is missing or invalid
     */
    @PostMapping("/generate")
    public ResponseEntity<List<InsightResponse>> generateInsights(
            @RequestHeader(value = ENCRYPTION_KEY_HEADER, required = false) String encodedKey,
            Authentication authentication) {

        log.info("Generating insights for user");

        if (encodedKey == null || encodedKey.trim().isEmpty()) {
            throw new IllegalArgumentException("Encryption key header is required");
        }

        User user = (User) authentication.getPrincipal();
        SecretKey encryptionKey = EncryptionUtil.decodeEncryptionKey(encodedKey);

        List<InsightResponse> insights =
                insightService.generateInsights(user.getId(), encryptionKey);

        log.info("Generated {} insights for user {}", insights.size(), user.getId());

        return ResponseEntity.status(HttpStatus.CREATED).body(insights);
    }

    /**
     * Retrieve all active (non-dismissed) insights for the authenticated user.
     *
     * <p>Returns cached insights ordered by priority (HIGH → MEDIUM → LOW) and creation date. To
     * refresh insights, call {@code POST /api/v1/insights/generate}.
     *
     * <p><strong>Request Headers:</strong>
     *
     * <ul>
     *   <li>Authorization: Bearer {jwt_token}
     * </ul>
     *
     * <p><strong>Success Response (HTTP 200 OK):</strong>
     *
     * <pre>{@code
     * [
     *   {
     *     "id": 1,
     *     "type": "SPENDING_ANOMALY",
     *     "title": "Unusual Spending in Groceries",
     *     "description": "Your groceries spending is 45% higher than last month...",
     *     "priority": "HIGH",
     *     "dismissed": false,
     *     "createdAt": "2026-02-03T14:30:00",
     *     "typeDisplayName": "Spending Anomaly",
     *     "priorityDisplayName": "High",
     *     "priorityBadgeClass": "badge-danger"
     *   }
     * ]
     * }</pre>
     *
     * <p>Requirement REQ-2.17.4: Display insights on dashboard
     *
     * @param authentication Spring Security authentication object
     * @return HTTP 200 OK with list of active insights
     */
    @GetMapping
    public ResponseEntity<List<InsightResponse>> getInsights(Authentication authentication) {
        User user = (User) authentication.getPrincipal();

        log.debug("Fetching insights for user {}", user.getId());

        List<InsightResponse> insights = insightService.getInsights(user.getId());

        return ResponseEntity.ok(insights);
    }

    /**
     * Retrieve top N priority insights for dashboard display.
     *
     * <p>Returns the most important insights (highest priority first) limited to the specified
     * count. Useful for dashboard widgets that show only a few key insights.
     *
     * <p><strong>Request Headers:</strong>
     *
     * <ul>
     *   <li>Authorization: Bearer {jwt_token}
     * </ul>
     *
     * <p><strong>Path Parameters:</strong>
     *
     * <ul>
     *   <li>limit - Maximum number of insights to return (1-20)
     * </ul>
     *
     * <p><strong>Example Request:</strong>
     *
     * <pre>{@code
     * GET /api/v1/insights/top/3
     * }</pre>
     *
     * <p><strong>Success Response (HTTP 200 OK):</strong>
     *
     * <pre>{@code
     * [
     *   {
     *     "id": 1,
     *     "type": "BUDGET_WARNING",
     *     "title": "Groceries Budget Exceeded",
     *     "description": "You've exceeded your groceries budget by $50.00...",
     *     "priority": "HIGH",
     *     "dismissed": false,
     *     "createdAt": "2026-02-03T14:30:00"
     *   },
     *   {
     *     "id": 2,
     *     "type": "CASH_FLOW_WARNING",
     *     "title": "Low Account Balance",
     *     "description": "Your checking account balance is low ($75.00)...",
     *     "priority": "HIGH",
     *     "dismissed": false,
     *     "createdAt": "2026-02-03T14:30:00"
     *   },
     *   {
     *     "id": 3,
     *     "type": "SAVINGS_OPPORTUNITY",
     *     "title": "Subscription Review Opportunity",
     *     "description": "You're spending $45.00/month on subscriptions...",
     *     "priority": "MEDIUM",
     *     "dismissed": false,
     *     "createdAt": "2026-02-03T14:30:00"
     *   }
     * ]
     * }</pre>
     *
     * <p>Requirement REQ-2.17.4: Display top insights on dashboard
     *
     * @param limit Maximum number of insights to return (1-20)
     * @param authentication Spring Security authentication object
     * @return HTTP 200 OK with list of top N insights
     * @throws IllegalArgumentException if limit is out of range
     */
    @GetMapping("/top/{limit}")
    public ResponseEntity<List<InsightResponse>> getTopInsights(
            @PathVariable
                    @Min(value = 1, message = "Limit must be at least 1")
                    @Max(value = 20, message = "Limit cannot exceed 20")
                    int limit,
            Authentication authentication) {

        User user = (User) authentication.getPrincipal();

        log.debug("Fetching top {} insights for user {}", limit, user.getId());

        List<InsightResponse> insights = insightService.getTopInsights(user.getId(), limit);

        return ResponseEntity.ok(insights);
    }

    /**
     * Dismiss an insight.
     *
     * <p>Marks an insight as dismissed, hiding it from the main insights view. Dismissed insights
     * are retained for history but not shown in active lists. Old dismissed insights are
     * automatically cleaned up after 7 days.
     *
     * <p><strong>Request Headers:</strong>
     *
     * <ul>
     *   <li>Authorization: Bearer {jwt_token}
     * </ul>
     *
     * <p><strong>Path Parameters:</strong>
     *
     * <ul>
     *   <li>id - Insight ID to dismiss
     * </ul>
     *
     * <p><strong>Example Request:</strong>
     *
     * <pre>{@code
     * POST /api/v1/insights/123/dismiss
     * }</pre>
     *
     * <p><strong>Success Response (HTTP 204 No Content)</strong>
     *
     * <p>Requirement REQ-2.17.4: Allow users to dismiss insights
     *
     * @param id Insight ID
     * @param authentication Spring Security authentication object
     * @return HTTP 204 No Content on success
     * @throws org.openfinance.exception.ResourceNotFoundException if insight not found or not owned
     *     by user
     */
    @PostMapping("/{id}/dismiss")
    public ResponseEntity<Void> dismissInsight(
            @PathVariable Long id, Authentication authentication) {

        User user = (User) authentication.getPrincipal();

        log.info("Dismissing insight {} for user {}", id, user.getId());

        insightService.dismissInsight(id, user.getId());

        return ResponseEntity.noContent().build();
    }
}
