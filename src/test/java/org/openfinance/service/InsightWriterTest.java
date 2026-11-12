package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openfinance.config.EncryptionProperties;
import org.openfinance.entity.Insight;
import org.openfinance.entity.InsightType;
import org.openfinance.entity.User;
import org.openfinance.repository.InsightRepository;
import org.openfinance.security.EncryptionContext;
import org.openfinance.service.impl.InsightWriterImpl;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
class InsightWriterTest {
    @Mock InsightRepository repository;

    @Test
    void appendingDetectorAlertsPreservesExistingDismissalsAndDeduplicatesTheBatch() {
        Insight existing = detectorAlert("unusual:new-payee:1");
        Insight fresh = detectorAlert("unusual:new-payee:2");
        Insight duplicate = detectorAlert(fresh.getSourceKey());
        when(repository.findExistingSourceKeys(
                        1L,
                        List.of(
                                existing.getSourceKey(),
                                fresh.getSourceKey(),
                                duplicate.getSourceKey())))
                .thenReturn(List.of(existing.getSourceKey()));
        EncryptionProperties encryption = new EncryptionProperties();
        encryption.setEnabled(false);

        assertThat(
                        new InsightWriterImpl(repository, encryption)
                                .appendUnusual(1L, List.of(existing, fresh, duplicate)))
                .isEqualTo(1);

        verify(repository).saveAll(List.of(fresh));
    }

    @Test
    void refusesDetectorAlertsBelongingToAnotherUser() {
        Insight alert = detectorAlert("unusual:new-payee:1");
        alert.setUser(User.builder().id(2L).build());
        EncryptionProperties encryption = new EncryptionProperties();
        encryption.setEnabled(false);

        assertThatThrownBy(
                        () ->
                                new InsightWriterImpl(repository, encryption)
                                        .appendUnusual(1L, List.of(alert)))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(repository);
    }

    @Test
    void requiresAnEncryptionSessionToPublishDetectorAlerts() {
        EncryptionContext.clear();
        EncryptionProperties encryption = new EncryptionProperties();
        encryption.setEnabled(true);
        assertThatThrownBy(
                        () ->
                                new InsightWriterImpl(repository, encryption)
                                        .appendUnusual(
                                                1L, List.of(detectorAlert("unusual:new-payee:1"))))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(repository);
    }

    private Insight detectorAlert(String key) {
        return Insight.builder()
                .user(User.builder().id(1L).build())
                .type(InsightType.UNUSUAL_TRANSACTION)
                .sourceKey(key)
                .build();
    }

    @Test
    void failedCompetitorRefreshKeepsOffersButRemovesAnObsoleteLocalRatio() {
        Insight summary = insight(1L, InsightType.RECURRING_BILLING, false);
        summary.setSourceKey("recurring:summary");
        Insight ratio = insight(2L, InsightType.RECURRING_BILLING, false);
        ratio.setSourceKey("recurring:ratio");
        Insight offer = insight(3L, InsightType.RECURRING_BILLING, false);
        offer.setSourceKey("recurring:competitor:10");
        when(repository.findByUser_IdOrderByPriorityAscCreatedAtDesc(1L))
                .thenReturn(List.of(summary, ratio, offer));
        Insight refreshed = insight(null, InsightType.RECURRING_BILLING, false);
        refreshed.setSourceKey("recurring:summary");
        refreshed.setDescription("Updated local totals");
        EncryptionProperties encryption = new EncryptionProperties();
        encryption.setEnabled(false);
        new InsightWriterImpl(repository, encryption)
                .replaceGenerated(1L, List.of(refreshed), Set.of(InsightType.RECURRING_BILLING));
        ArgumentCaptor<Iterable<Insight>> deleted = ArgumentCaptor.forClass(Iterable.class);
        verify(repository).deleteAll(deleted.capture());
        assertThat(deleted.getValue()).containsExactly(ratio);
        assertThat(summary.getDescription()).isEqualTo("Updated local totals");
    }

    @Test
    void partialRefreshPreservesExternalResultsDismissalsAndDetectorAlerts() {
        Insight staleLocal = insight(1L, InsightType.SPENDING_ANOMALY, false);
        Insight external = insight(2L, InsightType.REGION_COMPARISON, false);
        Insight dismissed = insight(3L, InsightType.BUDGET_WARNING, true);
        Insight detector = insight(4L, InsightType.UNUSUAL_TRANSACTION, false);
        when(repository.findByUser_IdOrderByPriorityAscCreatedAtDesc(1L))
                .thenReturn(List.of(staleLocal, external, dismissed, detector));
        EncryptionProperties encryption = new EncryptionProperties();
        encryption.setEnabled(false);
        new InsightWriterImpl(repository, encryption)
                .replaceGenerated(1L, List.of(), Set.of(InsightType.REGION_COMPARISON));
        ArgumentCaptor<Iterable<Insight>> deleted = ArgumentCaptor.forClass(Iterable.class);
        verify(repository).deleteAll(deleted.capture());
        assertThat(deleted.getValue()).containsExactly(staleLocal);
    }

    private Insight insight(Long id, InsightType type, boolean dismissed) {
        return Insight.builder().id(id).type(type).dismissed(dismissed).title(type.name()).build();
    }
}
