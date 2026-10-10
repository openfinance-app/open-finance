package org.openfinance.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import org.openfinance.dto.ImportProgressResponse;
import org.openfinance.dto.ImportProgressResponse.Phase;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Publishes live counters without writes that would contend with SQLite's import transaction. */
@Component
public class ImportProgressTracker {
    private record Key(Long sessionId, Long userId) {}

    private final Cache<Key, Task> tasks =
            Caffeine.newBuilder().maximumSize(1000).expireAfterWrite(Duration.ofHours(1)).build();

    public Task start(Long sessionId, Long userId) {
        Key key = new Key(sessionId, userId);
        Task task = new Task(key);
        tasks.put(key, task);
        return task;
    }

    public ImportProgressResponse get(Long sessionId, Long userId) {
        Task task = tasks.getIfPresent(new Key(sessionId, userId));
        return task == null ? ImportProgressResponse.idle() : task.snapshot;
    }

    public final class Task implements AutoCloseable {
        private final Key key;
        private volatile ImportProgressResponse snapshot =
                new ImportProgressResponse(Phase.PREPARING, 0, 0);

        private Task(Key key) {
            this.key = key;
        }

        public void update(Phase phase, int processed, int total) {
            snapshot = new ImportProgressResponse(phase, processed, total);
        }

        @Override
        public void close() {
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(
                        new TransactionSynchronization() {
                            @Override
                            public void afterCompletion(int status) {
                                tasks.asMap().remove(key, Task.this);
                            }
                        });
            } else {
                tasks.asMap().remove(key, this);
            }
        }
    }
}
