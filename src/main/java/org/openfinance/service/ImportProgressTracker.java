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
        private final Task parent;
        private final int offset;
        private final int batchTotal;
        private volatile ImportProgressResponse snapshot =
                new ImportProgressResponse(Phase.PREPARING, 0, 0);

        private Task(Key key) {
            this(key, null, 0, 0);
        }

        private Task(Key key, Task parent, int offset, int batchTotal) {
            this.key = key;
            this.parent = parent;
            this.offset = offset;
            this.batchTotal = batchTotal;
        }

        public Task slice(int offset, int total) {
            return new Task(key, this, offset, total);
        }

        public void update(Phase phase, int processed, int total) {
            if (parent == null) snapshot = new ImportProgressResponse(phase, processed, total);
            else parent.update(phase, offset + processed, batchTotal);
        }

        @Override
        public void close() {
            if (parent != null) return;
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
