package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openfinance.dto.BudgetProgressResponse;
import org.openfinance.entity.Budget;
import org.openfinance.entity.BudgetAlert;
import org.openfinance.repository.BudgetAlertRepository;
import org.openfinance.repository.BudgetRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("Budget alerts handle cooldown, ownership, and unavailable budget progress")
class BudgetAlertServiceTest {
    @Mock BudgetAlertRepository alertRepository;
    @Mock BudgetRepository budgetRepository;
    @Mock BudgetService budgetService;
    @InjectMocks BudgetAlertService service;

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"0", "0.99", "150.01", "-10"})
    void invalidThresholdsCannotCreateOrChangeAlerts(String threshold) {
        BudgetAlert existing = alert(1L);
        BigDecimal invalid = new BigDecimal(threshold);
        assertThatThrownBy(() -> service.createAlert(1L, 7L, invalid))
                .isInstanceOf(IllegalArgumentException.class);
        when(alertRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
        assertThatThrownBy(() -> service.updateAlert(existing.getId(), 7L, invalid, false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(existing.getThreshold()).isEqualByComparingTo("80");
        assertThat(existing.isEnabled()).isTrue();
        verify(alertRepository, never()).save(any());
    }

    @Test
    void missingIdentifiersAndMissingAlertsAreRejectedWithoutWriting() {
        assertThatThrownBy(() -> service.checkBudgetAlertsAfterTransaction(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.createAlert(null, 7L, BigDecimal.TEN))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.createAlert(1L, 7L, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.updateAlert(null, 7L, BigDecimal.TEN, true))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.updateAlert(UUID.randomUUID(), 7L, BigDecimal.TEN, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
        assertThatThrownBy(() -> service.createAlert(999L, 7L, BigDecimal.TEN))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("access denied");
        verify(alertRepository, never()).save(any());
    }

    @Test
    void cooldownSuppressesRepeatedNotificationsButAllowsExpiredAlerts() {
        BudgetAlert recent = alert(1L);
        recent.setLastTriggered(LocalDateTime.now().minusHours(1));
        BudgetAlert expired = alert(1L);
        expired.setLastTriggered(LocalDateTime.now().minusHours(25));
        BudgetAlert disabled = alert(1L);
        disabled.setEnabled(false);
        when(alertRepository.findEnabledAlertsByUserId(7L))
                .thenReturn(List.of(recent, expired, disabled));
        when(budgetService.calculateBudgetProgress(1L, 7L)).thenReturn(progress());
        assertThat(service.checkBudgetAlertsAfterTransaction(7L)).containsExactly(expired);
        assertThat(expired.isRead()).isFalse();
        assertThat(recent.getLastTriggered()).isBefore(LocalDateTime.now().minusMinutes(59));
        verify(alertRepository).save(expired);
        verify(budgetService).calculateBudgetProgress(1L, 7L);
    }

    @Test
    void missingProgressForOneBudgetDoesNotHideOtherBudgetAlerts() {
        BudgetAlert unavailable = alert(1L);
        BudgetAlert available = alert(2L);
        when(alertRepository.findEnabledAlertsByUserId(7L))
                .thenReturn(List.of(unavailable, available));
        when(budgetService.calculateBudgetProgress(1L, 7L))
                .thenThrow(new IllegalStateException("No exchange rate"));
        when(budgetService.calculateBudgetProgress(2L, 7L)).thenReturn(progress());
        assertThat(service.checkBudgetAlertsAfterTransaction(7L)).containsExactly(available);
        verify(alertRepository, never()).save(unavailable);
    }

    @Test
    void cannotMutateAnotherUsersAlertOrCreateDuplicateThresholds() {
        BudgetAlert existing = alert(1L);
        when(alertRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
        assertThatThrownBy(() -> service.updateAlert(existing.getId(), 99L, BigDecimal.TEN, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Access denied");
        assertThatThrownBy(() -> service.deleteAlert(existing.getId(), 99L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Access denied");
        assertThatThrownBy(() -> service.markAlertAsRead(existing.getId(), 99L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Access denied");
        when(budgetRepository.findByIdAndUserId(1L, 7L))
                .thenReturn(Optional.of(existing.getBudget()));
        when(alertRepository.findByBudgetIdAndThreshold(1L, new BigDecimal("80")))
                .thenReturn(Optional.of(existing));
        assertThatThrownBy(() -> service.createAlert(1L, 7L, new BigDecimal("80")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already exists");
        verify(alertRepository, never()).save(any());
    }

    @Test
    void conflictingThresholdUpdateLeavesTheOriginalAlertUnchanged() {
        BudgetAlert existing = alert(1L);
        BudgetAlert other = alert(1L);
        when(alertRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(alertRepository.findByBudgetIdAndThreshold(1L, new BigDecimal("90")))
                .thenReturn(Optional.of(other));
        assertThatThrownBy(
                        () ->
                                service.updateAlert(
                                        existing.getId(), 7L, new BigDecimal("90"), false))
                .isInstanceOf(IllegalStateException.class);
        assertThat(existing.getThreshold()).isEqualByComparingTo("80");
        assertThat(existing.isEnabled()).isTrue();
        verify(alertRepository, never()).save(any());
    }

    @Test
    void unavailableProgressDoesNotPreventSavingAValidAlertConfiguration() {
        BudgetAlert existing = alert(1L);
        when(budgetRepository.findByIdAndUserId(1L, 7L))
                .thenReturn(Optional.of(existing.getBudget()));
        when(alertRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(budgetService.calculateBudgetProgress(1L, 7L))
                .thenThrow(new IllegalStateException("No exchange rate"));
        BudgetAlert created = service.createAlert(1L, 7L, new BigDecimal("90"));
        assertThat(created.getThreshold()).isEqualByComparingTo("90");
        assertThat(created.getLastTriggered()).isNull();
        assertThat(created.isRead()).isTrue();
    }

    @Test
    void rolloverResetsOnlyTheRequestedBudgetsAndEmptyInputDoesNothing() {
        assertThat(service.resetAlertsForNewPeriod(null)).isZero();
        assertThat(service.resetAlertsForNewPeriod(List.of())).isZero();
        when(alertRepository.resetAlertsForBudgets(List.of(1L, 2L))).thenReturn(3);
        assertThat(service.resetAlertsForNewPeriod(List.of(1L, 2L))).isEqualTo(3);
        verify(alertRepository).resetAlertsForBudgets(List.of(1L, 2L));
    }

    private BudgetAlert alert(long budgetId) {
        return BudgetAlert.builder()
                .id(UUID.randomUUID())
                .budget(
                        Budget.builder()
                                .id(budgetId)
                                .userId(7L)
                                .startDate(LocalDate.now().minusDays(3))
                                .endDate(LocalDate.now().plusDays(3))
                                .build())
                .threshold(new BigDecimal("80"))
                .isEnabled(true)
                .isRead(true)
                .build();
    }

    private BudgetProgressResponse progress() {
        return BudgetProgressResponse.builder()
                .percentageSpent(new BigDecimal("80"))
                .startDate(LocalDate.now().minusDays(3))
                .remaining(new BigDecimal("20"))
                .build();
    }
}
