package org.openfinance.dto;

/** Live work counters; processed rows are not committed until the session completes. */
public record ImportProgressResponse(Phase phase, int processed, int total) {
    public enum Phase {
        IDLE,
        PREPARING,
        AI_CATEGORIZING,
        IMPORTING,
        FINALIZING
    }

    public static ImportProgressResponse idle() {
        return new ImportProgressResponse(Phase.IDLE, 0, 0);
    }
}
