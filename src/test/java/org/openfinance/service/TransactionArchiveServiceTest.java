package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TransactionArchiveServiceTest {

    @Mock private EntityManager entityManager;

    @Mock private Query query;

    @Mock private OperationHistoryService operationHistoryService;

    @InjectMocks private TransactionArchiveService transactionArchiveService;

    @Test
    @DisplayName("should archive old transactions for user")
    void shouldArchiveOldTransactionsForUser() {
        // Given
        Long userId = 1L;
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.executeUpdate()).thenReturn(5, 5); // 5 archived, 5 deleted

        // When
        int result = transactionArchiveService.archiveOldTransactionsForUser(userId);

        // Then
        assertThat(result).isEqualTo(5);
        verify(entityManager, times(2)).createNativeQuery(anyString());
        verify(query, times(2)).setParameter(eq("userId"), eq(userId));
        verify(query, times(2)).setParameter(eq("cutoffDate"), anyString());
        verify(query, times(2)).executeUpdate();
    }

    @Test
    @DisplayName("should return correct archived transaction count")
    void shouldReturnArchivedTransactionCount() {
        // Given
        Long userId = 1L;
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.getSingleResult()).thenReturn(10L);

        // When
        long result = transactionArchiveService.getArchivedTransactionCount(userId);

        // Then
        assertThat(result).isEqualTo(10L);
        verify(entityManager).createNativeQuery(contains("COUNT(*) FROM transactions_archive"));
        verify(query).setParameter("userId", userId);
        verify(query).getSingleResult();
    }
}
