package org.openfinance.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link LoggingAspect}'s slow-execution threshold, which must stay consistent with
 * {@code PerformanceMonitoringAspect} (500 ms) and the Hibernate slow-query setting.
 */
@DisplayName("LoggingAspect slow-execution threshold")
class LoggingAspectTest {

    private final LoggingAspect aspect = new LoggingAspect();

    @Test
    @DisplayName("Threshold is 500 ms, matching PerformanceMonitoringAspect")
    void thresholdIs500ms() {
        assertThat(LoggingAspect.SLOW_EXECUTION_THRESHOLD_MS).isEqualTo(500L);
    }

    @Test
    @DisplayName("Flags executions between 500 ms and the old 1000 ms threshold as slow")
    void flagsExecutionsSlowerThan500ms() {
        assertThat(aspect.isSlowExecution(501)).isTrue();
        assertThat(aspect.isSlowExecution(999)).isTrue(); // was NOT slow under the old 1000 ms rule
        assertThat(aspect.isSlowExecution(500)).isFalse();
        assertThat(aspect.isSlowExecution(0)).isFalse();
    }
}
