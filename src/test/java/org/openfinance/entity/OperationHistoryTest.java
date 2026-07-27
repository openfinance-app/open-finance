package org.openfinance.entity;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.TimeZone;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link OperationHistory} entity lifecycle hooks.
 *
 * <p>Pins the timestamp convention: {@code createdAt} is deliberately stamped in
 * <strong>UTC</strong> (not the system-default zone every other entity uses). The Undo/Redo history
 * API filters by a client-supplied ISO {@code Instant} ("since") which {@code
 * OperationHistoryController} converts via {@code LocalDateTime.ofInstant(since, ZoneOffset.UTC)}.
 * For that {@code createdAt >= since} comparison to hold across timezones, {@code createdAt} must
 * live in the same UTC wall-clock frame. These tests guard against a well-intentioned "consistency"
 * refactor silently breaking the filter.
 */
@DisplayName("OperationHistory Entity Tests")
class OperationHistoryTest {

    @Test
    @DisplayName("onCreate should stamp createdAt in UTC to match the Instant-based 'since' filter")
    void onCreateShouldStampCreatedAtInUtc() {
        TimeZone original = TimeZone.getDefault();
        try {
            // Asia/Tokyo is a stable UTC+9 zone with no DST, so system-local time is
            // unambiguously 9 hours ahead of UTC.
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"));

            OperationHistory history = OperationHistory.builder().build();

            LocalDateTime utcBefore = LocalDateTime.now(ZoneOffset.UTC);
            history.onCreate();
            LocalDateTime utcAfter = LocalDateTime.now(ZoneOffset.UTC);

            LocalDateTime createdAt = history.getCreatedAt();
            assertThat(createdAt).isNotNull();
            // Falls within the UTC window; a system-zone stamp would land ~9 hours ahead and
            // fail this bound (which is exactly what breaks the 'since' filter).
            assertThat(createdAt).isBetween(utcBefore.minusSeconds(1), utcAfter.plusSeconds(1));
        } finally {
            TimeZone.setDefault(original);
        }
    }

    @Test
    @DisplayName("onCreate should not overwrite an explicitly-set createdAt")
    void onCreateShouldPreserveExplicitCreatedAt() {
        LocalDateTime explicit = LocalDateTime.of(2020, 1, 1, 0, 0);
        OperationHistory history = OperationHistory.builder().createdAt(explicit).build();

        history.onCreate();

        assertThat(history.getCreatedAt()).isEqualTo(explicit);
    }
}
