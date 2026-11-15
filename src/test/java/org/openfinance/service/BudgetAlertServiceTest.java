package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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

    @ParameterizedTest
    @ValueSource(
            strings = {
                "create-user",
                "update-user",
                "delete-id",
                "delete-user",
                "find-budget",
                "find-user",
                "unread-user",
                "count-user",
                "read-id",
                "read-user",
                "read-all-user"
            })
    void incompleteScopeCannotAccessOrChangeAlertState(String invalid) {
        UUID id = UUID.randomUUID();
        assertThatThrownBy(
                        () -> {
                            switch (invalid) {
                                case "create-user" -> service.createAlert(1L, null, BigDecimal.TEN);
                                case "update-user" -> service.updateAlert(id, null, null, null);
                                case "delete-id" -> service.deleteAlert(null, 7L);
                                case "delete-user" -> service.deleteAlert(id, null);
                                case "find-budget" -> service.findAlertsByBudget(null, 7L);
                                case "find-user" -> service.findAlertsByBudget(1L, null);
                                case "unread-user" -> service.findUnreadAlerts(null);
                                case "count-user" -> service.countUnreadAlerts(null);
                                case "read-id" -> service.markAlertAsRead(null, 7L);
                                case "read-user" -> service.markAlertAsRead(id, null);
                                case "read-all-user" -> service.markAllAlertsAsRead(null);
                            }
                        })
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(alertRepository, budgetRepository, budgetService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"unknown", "0", "1", "-0.01"})
    void budgetsWithoutAPercentageOnlyTriggerForAnActuallyNegativeRemainder(String remaining) {
        BudgetAlert alert = alert(1L);
        BudgetProgressResponse progress =
                BudgetProgressResponse.builder()
                        .remaining(remaining.equals("unknown") ? null : new BigDecimal(remaining))
                        .build();
        when(alertRepository.findEnabledAlertsByUserId(7L)).thenReturn(List.of(alert));
        when(budgetService.calculateBudgetProgress(1L, 7L)).thenReturn(progress);
        assertThat(service.checkBudgetAlertsAfterTransaction(7L))
                .hasSize(remaining.startsWith("-") ? 1 : 0);
        assertThat(alert.getLastTriggered() != null).isEqualTo(remaining.startsWith("-"));
    }

    @Test
    void consecutiveAlertsWithUnavailableProgressDoNotReuseAnotherBudgetsProgress() {
        BudgetAlert first = alert(1L),
                missing = alert(2L),
                anotherMissing = alert(2L),
                last = alert(3L);
        when(alertRepository.findEnabledAlertsByUserId(7L))
                .thenReturn(List.of(first, missing, anotherMissing, last));
        when(budgetService.calculateBudgetProgress(1L, 7L)).thenReturn(progress());
        when(budgetService.calculateBudgetProgress(2L, 7L)).thenReturn(null);
        when(budgetService.calculateBudgetProgress(3L, 7L)).thenReturn(progress());
        assertThat(service.checkBudgetAlertsAfterTransaction(7L)).containsExactly(first, last);
        verify(budgetService).calculateBudgetProgress(2L, 7L);
        verify(alertRepository, never()).save(missing);
        verify(alertRepository, never()).save(anotherMissing);
    }

    @ParameterizedTest
    @ValueSource(strings = {"future", "expired"})
    void savingAnAlertOutsideItsBudgetPeriodDoesNotEvaluateSpending(String period) {
        BudgetAlert existing = alert(1L);
        if (period.equals("future")) existing.getBudget().setStartDate(LocalDate.now().plusDays(1));
        else existing.getBudget().setEndDate(LocalDate.now().minusDays(1));
        when(alertRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(alertRepository.save(existing)).thenReturn(existing);
        BudgetAlert updated = service.updateAlert(existing.getId(), 7L, null, true);
        assertThat(updated.getLastTriggered()).isNull();
        verifyNoInteractions(budgetService);
    }

    @Test
    void reapplyingTheSameThresholdDoesNotConflictWithTheAlertItself() {
        BudgetAlert existing = alert(1L);
        when(alertRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(alertRepository.findByBudgetIdAndThreshold(1L, new BigDecimal("80")))
                .thenReturn(Optional.of(existing));
        when(alertRepository.save(existing)).thenReturn(existing);
        assertThat(service.updateAlert(existing.getId(), 7L, new BigDecimal("80"), false))
                .isSameAs(existing);
        assertThat(existing.isEnabled()).isFalse();
        verifyNoInteractions(budgetService);
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
