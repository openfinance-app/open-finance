package org.openfinance.service.ai;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** A whole operation has a bounded deadline, including multi-call insight generation. */
@Component
public class AIRequestLimits {
    @Value("${application.ai.request-timeout-seconds:600}")
    private int timeoutSeconds = 600;

    public Duration timeout() {
        return Duration.ofSeconds(Math.max(1, Math.min(timeoutSeconds, 600)));
    }

    public long deadline() {
        return System.nanoTime() + timeout().toNanos();
    }

    public Duration remaining(long deadline) {
        long remaining = deadline - System.nanoTime();
        if (remaining <= 0) throw new AIProviderException("AI", "Operation deadline exceeded");
        return Duration.ofNanos(remaining);
    }
}
