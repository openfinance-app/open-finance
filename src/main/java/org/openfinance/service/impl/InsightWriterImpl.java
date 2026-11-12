package org.openfinance.service.impl;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.openfinance.entity.Insight;
import org.openfinance.entity.InsightType;
import org.openfinance.repository.InsightRepository;
import org.openfinance.service.InsightWriter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Atomically publishes a successful refresh without losing dismissals or detector-owned alerts. */
@Service
@RequiredArgsConstructor
public class InsightWriterImpl implements InsightWriter {
    private final InsightRepository repository;
    private final org.openfinance.config.EncryptionProperties encryptionProperties;

    @Override
    @Transactional
    public int appendUnusual(Long userId, List<Insight> detected) {
        requireEncryptionSession();
        if (detected.isEmpty()) return 0;
        if (detected.stream()
                .anyMatch(
                        insight ->
                                insight.getUser() == null
                                        || !userId.equals(insight.getUser().getId())
                                        || insight.getType() != InsightType.UNUSUAL_TRANSACTION
                                        || insight.getSourceKey() == null
                                        || insight.getSourceKey().isBlank())) {
            throw new IllegalArgumentException(
                    "Detector alerts require an owned user and a source key");
        }
        // FinancialWriteIsolation locks this owner before the read. Recheck after analysis so
        // overlapping jobs cannot insert duplicates or overwrite a dismissed alert.
        Set<String> existing =
                new HashSet<>(
                        repository.findExistingSourceKeys(
                                userId, detected.stream().map(Insight::getSourceKey).toList()));
        List<Insight> pending =
                detected.stream().filter(insight -> existing.add(insight.getSourceKey())).toList();
        if (!pending.isEmpty()) repository.saveAll(pending);
        return pending.size();
    }

    @Override
    @Transactional
    public List<Insight> replaceGenerated(
            Long userId, List<Insight> generated, Set<InsightType> unavailableTypes) {
        requireEncryptionSession();
        List<Insight> previous = repository.findByUser_IdOrderByPriorityAscCreatedAtDesc(userId);
        Set<Long> retained = new HashSet<>();
        boolean recurringTotalsAvailable =
                generated.stream()
                        .anyMatch(insight -> "recurring:summary".equals(insight.getSourceKey()));
        for (Insight next : generated) {
            Insight existing =
                    previous.stream().filter(old -> sameSource(old, next)).findFirst().orElse(null);
            if (existing == null) {
                repository.save(next);
            } else {
                retained.add(existing.getId());
                existing.setSourceKey(next.getSourceKey());
                existing.setTitle(next.getTitle());
                existing.setDescription(next.getDescription());
                existing.setPriority(next.getPriority());
                repository.save(existing);
            }
        }
        repository.deleteAll(
                previous.stream()
                        .filter(old -> old.getType() != InsightType.UNUSUAL_TRANSACTION)
                        .filter(
                                old ->
                                        !unavailableTypes.contains(old.getType())
                                                || (recurringTotalsAvailable
                                                        && old.getType()
                                                                == InsightType.RECURRING_BILLING
                                                        && ("recurring:summary"
                                                                        .equals(old.getSourceKey())
                                                                || "recurring:ratio"
                                                                        .equals(
                                                                                old
                                                                                        .getSourceKey()))))
                        .filter(old -> !Boolean.TRUE.equals(old.getDismissed()))
                        .filter(old -> !retained.contains(old.getId()))
                        .toList());
        repository.flush();
        return repository.findByUser_IdAndDismissedFalse(userId);
    }

    private void requireEncryptionSession() {
        if (encryptionProperties.isEnabled()
                && org.openfinance.security.EncryptionContext.getKey() == null) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Encryption session required");
        }
    }

    private boolean sameSource(Insight old, Insight next) {
        if (old.getType() == InsightType.UNUSUAL_TRANSACTION) return false;
        if (old.getSourceKey() != null) return old.getSourceKey().equals(next.getSourceKey());
        // Adopt legacy dismissals on the first refresh, before stable identities were stored.
        return old.getType() == next.getType() && old.getTitle().equals(next.getTitle());
    }
}
