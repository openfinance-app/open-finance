package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.openfinance.dto.ImportProgressResponse;
import org.openfinance.dto.ImportProgressResponse.Phase;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class ImportProgressTrackerTest {
    @Test
    void publishesCountersAcrossThreadsAndKeepsFinalizingUntilCommit() {
        ImportProgressTracker tracker = new ImportProgressTracker();
        TransactionSynchronizationManager.initSynchronization();
        try {
            try (ImportProgressTracker.Task task = tracker.start(1L, 2L)) {
                task.update(Phase.IMPORTING, 500, 3083);
                assertThat(CompletableFuture.supplyAsync(() -> tracker.get(1L, 2L)).join())
                        .isEqualTo(new ImportProgressResponse(Phase.IMPORTING, 500, 3083));
                assertThat(tracker.get(1L, 3L).phase()).isEqualTo(Phase.IDLE);
                task.update(Phase.FINALIZING, 3083, 3083);
            }
            assertThat(tracker.get(1L, 2L).phase()).isEqualTo(Phase.FINALIZING);
            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(
                            callback ->
                                    callback.afterCompletion(
                                            TransactionSynchronization.STATUS_COMMITTED));
            assertThat(tracker.get(1L, 2L).phase()).isEqualTo(Phase.IDLE);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void anOlderReviewCannotClearNewerConfirmationProgress() {
        ImportProgressTracker tracker = new ImportProgressTracker();
        ImportProgressTracker.Task review = tracker.start(1L, 2L);
        try (ImportProgressTracker.Task confirmation = tracker.start(1L, 2L)) {
            confirmation.update(Phase.IMPORTING, 1, 2);
            review.update(Phase.AI_CATEGORIZING, 15, 30);
            review.close();
            assertThat(tracker.get(1L, 2L).phase()).isEqualTo(Phase.IMPORTING);
        }
        assertThat(tracker.get(1L, 2L).phase()).isEqualTo(Phase.IDLE);
    }
}
