package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openfinance.dto.OperationHistoryResponse;
import org.openfinance.entity.EntityType;
import org.openfinance.entity.OperationHistory;
import org.openfinance.entity.OperationType;
import org.openfinance.repository.OperationHistoryRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class OperationHistoryServiceTest {

    @Mock private OperationHistoryRepository historyRepository;

    @Mock private ObjectMapper objectMapper;

    @InjectMocks private OperationHistoryService service;

    private final Long USER_ID = 1L;
    private final Pageable pageable = Pageable.ofSize(20);

    private OperationHistory entryAt(LocalDateTime createdAt) {
        // OperationHistory has @Data + @Builder + @NoArgsConstructor — setters are available
        OperationHistory h = new OperationHistory();
        h.setId(1L);
        h.setUserId(USER_ID);
        h.setEntityType(EntityType.ACCOUNT);
        h.setEntityId(10L);
        h.setEntityLabel("Test");
        h.setOperationType(OperationType.CREATE);
        h.setCreatedAt(createdAt);
        return h;
    }

    @Test
    void getHistory_noFilters_delegatesToUnfilteredRepository() {
        when(historyRepository.findByUserIdOrderByCreatedAtDesc(USER_ID, pageable))
                .thenReturn(new PageImpl<>(List.of()));

        Page<OperationHistoryResponse> result = service.getHistory(USER_ID, null, null, pageable);

        verify(historyRepository).findByUserIdOrderByCreatedAtDesc(USER_ID, pageable);
        assertThat(result).isEmpty();
    }

    @Test
    void getHistory_sinceFilter_delegatesToSinceRepository() {
        LocalDateTime since = LocalDateTime.now().minusHours(1);
        OperationHistory recent = entryAt(LocalDateTime.now());

        when(historyRepository.findByUserIdAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(
                        eq(USER_ID), eq(since), any()))
                .thenReturn(new PageImpl<>(List.of(recent)));

        Page<OperationHistoryResponse> result = service.getHistory(USER_ID, null, since, pageable);

        verify(historyRepository)
                .findByUserIdAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(
                        USER_ID, since, pageable);
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    void getHistory_entityTypeFilter_delegatesToEntityTypeRepository() {
        OperationHistory entry = entryAt(LocalDateTime.now());

        when(historyRepository.findByUserIdAndEntityTypeOrderByCreatedAtDesc(
                        eq(USER_ID), eq(EntityType.ACCOUNT), any()))
                .thenReturn(new PageImpl<>(List.of(entry)));

        Page<OperationHistoryResponse> result =
                service.getHistory(USER_ID, EntityType.ACCOUNT, null, pageable);

        verify(historyRepository)
                .findByUserIdAndEntityTypeOrderByCreatedAtDesc(
                        USER_ID, EntityType.ACCOUNT, pageable);
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    void getHistory_bothFilters_delegatesToCombinedRepository() {
        LocalDateTime since = LocalDateTime.now().minusHours(1);
        OperationHistory entry = entryAt(LocalDateTime.now());

        when(historyRepository
                        .findByUserIdAndEntityTypeAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(
                                eq(USER_ID), eq(EntityType.ASSET), eq(since), any()))
                .thenReturn(new PageImpl<>(List.of(entry)));

        Page<OperationHistoryResponse> result =
                service.getHistory(USER_ID, EntityType.ASSET, since, pageable);

        verify(historyRepository)
                .findByUserIdAndEntityTypeAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(
                        USER_ID, EntityType.ASSET, since, pageable);
        assertThat(result.getTotalElements()).isEqualTo(1);
    }
}
