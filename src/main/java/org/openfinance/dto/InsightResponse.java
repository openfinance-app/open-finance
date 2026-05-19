package org.openfinance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import lombok.*;
import org.openfinance.entity.InsightPriority;
import org.openfinance.entity.InsightType;

/**
 * Data Transfer Object for {@link org.openfinance.entity.Insight}.
 *
 * <p>Contains all insight details for API responses.
 *
 * <p><strong>Example JSON:</strong>
 *
 * <pre>{@code
 * {
 *   "id": 123,
 *   "type": "SPENDING_ANOMALY",
 *   "title": "Unusual Spending Detected",
 *   "description": "Your restaurant spending is 45% higher than last month ($520 vs $360)",
 *   "priority": "HIGH",
 *   "dismissed": false,
 *   "createdAt": "2024-02-03T10:30:00"
 * }
 * }</pre>
 *
 * @since Sprint 11 - AI Assistant Integration (Task 11.4)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InsightResponse {

    /** Unique identifier. */
    private Long id;

    /** Type of insight (SPENDING_ANOMALY, BUDGET_WARNING, etc.). */
    @NotNull(message = "{insight.type.required}")
    private InsightType type;

    /** Brief summary title (max 200 characters). */
    @NotBlank(message = "{insight.title.required}")
    @Size(max = 200, message = "{insight.title.max}")
    private String title;

    /** Detailed description with context (max 2000 characters). */
    @NotBlank(message = "{insight.description.required}")
    @Size(max = 2000, message = "{insight.description.max}")
    private String description;

    /** Priority level (HIGH, MEDIUM, LOW). */
    @NotNull(message = "{insight.priority.required}")
    private InsightPriority priority;

    /** Whether the user has dismissed this insight. */
    private Boolean dismissed;

    /** Timestamp when the insight was generated. */
    private LocalDateTime createdAt;

    /** Display name for the insight type (e.g., "Spending Anomaly"). */
    public String getTypeDisplayName() {
        return type != null ? type.getDisplayName() : null;
    }

    /** Display name for the priority level (e.g., "High Priority"). */
    public String getPriorityDisplayName() {
        return priority != null ? priority.getDisplayName() : null;
    }

    /**
     * Get CSS class for priority badge color.
     *
     * @return CSS class (e.g., "bg-red-100 text-red-800" for HIGH)
     */
    public String getPriorityBadgeClass() {
        if (priority == null) return "";
        return switch (priority) {
            case HIGH -> "bg-red-100 text-red-800 dark:bg-red-900 dark:text-red-200";
            case MEDIUM -> "bg-yellow-100 text-yellow-800 dark:bg-yellow-900 dark:text-yellow-200";
            case LOW -> "bg-blue-100 text-blue-800 dark:bg-blue-900 dark:text-blue-200";
        };
    }
}
