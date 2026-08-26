package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openfinance.entity.NetWorth;
import org.openfinance.repository.NetWorthRepository;

/** Unit tests for {@link NetWorthSnapshotWriter}. */
@ExtendWith(MockitoExtension.class)
@DisplayName("NetWorthSnapshotWriter Unit Tests")
class NetWorthSnapshotWriterTest {

    @Mock private NetWorthRepository netWorthRepository;

    @InjectMocks private NetWorthSnapshotWriter writer;

    private final Long userId = 1L;
    private final LocalDate date = LocalDate.of(2026, 1, 31);

    @Test
    @DisplayName("Should insert a new snapshot when none exists for the date")
    void shouldInsertWhenNoneExists() {
        when(netWorthRepository.findByUserIdAndSnapshotDate(userId, date))
                .thenReturn(Optional.empty());
        when(netWorthRepository.save(any(NetWorth.class))).thenAnswer(inv -> inv.getArgument(0));

        NetWorth result =
                writer.upsert(
                        userId,
                        date,
                        "EUR",
                        new BigDecimal("5000.00"),
                        BigDecimal.ZERO,
                        new BigDecimal("5000.00"),
                        42L);

        ArgumentCaptor<NetWorth> captor = ArgumentCaptor.forClass(NetWorth.class);
        verify(netWorthRepository).save(captor.capture());
        NetWorth persisted = captor.getValue();

        assertThat(persisted.getId()).isNull();
        assertThat(persisted.getUserId()).isEqualTo(userId);
        assertThat(persisted.getSnapshotDate()).isEqualTo(date);
        assertThat(persisted.getCurrency()).isEqualTo("EUR");
        assertThat(persisted.getCurrencyId()).isEqualTo(42L);
        assertThat(persisted.getTotalAssets()).isEqualByComparingTo(new BigDecimal("5000.00"));
        assertThat(persisted.getTotalLiabilities()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(persisted.getNetWorth()).isEqualByComparingTo(new BigDecimal("5000.00"));
        assertThat(result).isSameAs(persisted);
    }

    @Test
    @DisplayName("Should update the existing snapshot in place when one exists for the date")
    void shouldUpdateExistingInPlace() {
        NetWorth existing =
                NetWorth.builder()
                        .id(7L)
                        .userId(userId)
                        .snapshotDate(date)
                        .currency("USD")
                        .totalAssets(new BigDecimal("5000.00"))
                        .totalLiabilities(BigDecimal.ZERO)
                        .netWorth(new BigDecimal("5000.00"))
                        .build();

        when(netWorthRepository.findByUserIdAndSnapshotDate(userId, date))
                .thenReturn(Optional.of(existing));
        when(netWorthRepository.save(any(NetWorth.class))).thenAnswer(inv -> inv.getArgument(0));

        NetWorth result =
                writer.upsert(
                        userId,
                        date,
                        "EUR",
                        new BigDecimal("7000.00"),
                        new BigDecimal("1000.00"),
                        new BigDecimal("6000.00"),
                        42L);

        // Same managed entity is mutated and saved (no new row).
        verify(netWorthRepository).save(existing);
        assertThat(result).isSameAs(existing);
        assertThat(result.getId()).isEqualTo(7L);
        assertThat(result.getCurrency()).isEqualTo("EUR");
        assertThat(result.getCurrencyId()).isEqualTo(42L);
        assertThat(result.getTotalAssets()).isEqualByComparingTo(new BigDecimal("7000.00"));
        assertThat(result.getTotalLiabilities()).isEqualByComparingTo(new BigDecimal("1000.00"));
        assertThat(result.getNetWorth()).isEqualByComparingTo(new BigDecimal("6000.00"));
    }
}
