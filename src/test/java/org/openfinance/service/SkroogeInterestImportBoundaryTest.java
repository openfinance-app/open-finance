package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openfinance.dto.SkroogeImportMetadata;
import org.openfinance.entity.Account;
import org.openfinance.entity.InterestDayCountBasis;
import org.openfinance.entity.InterestPeriod;
import org.openfinance.entity.InterestRateVariation;
import org.openfinance.entity.InterestValueDateMode;
import org.openfinance.repository.AccountRepository;
import org.openfinance.repository.InterestRateVariationRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName(
        "Skrooge interest import is owner scoped, repeatable and preserves account conventions")
class SkroogeInterestImportBoundaryTest {
    @Mock private AccountRepository accounts;
    @Mock private InterestRateVariationRepository variations;
    @InjectMocks private SkroogeInterestImportServiceImpl service;
    private static final LocalDate START = LocalDate.of(2025, 1, 1);

    private SkroogeImportMetadata metadata() {
        return SkroogeImportMetadata.builder()
                .sourceInterestRateCount(1)
                .interestRates(
                        List.of(
                                new SkroogeImportMetadata.SkroogeInterestRate(
                                        1L,
                                        10L,
                                        new SkroogeImportMetadata.InterestSettings(
                                                new BigDecimal("2.5"),
                                                BigDecimal.ZERO,
                                                START,
                                                InterestValueDateMode.TRANSACTION_DATE,
                                                InterestValueDateMode.TRANSACTION_DATE,
                                                InterestDayCountBasis.ACTUAL_365))))
                .interestWarnings(List.of())
                .build();
    }

    private Account account(Boolean enabled, InterestPeriod period) {
        return Account.builder()
                .id(20L)
                .userId(7L)
                .isInterestEnabled(enabled)
                .interestPeriod(period)
                .build();
    }

    private InterestRateVariation existing() {
        return InterestRateVariation.builder()
                .id(2L)
                .accountId(20L)
                .validFrom(START)
                .rate(new BigDecimal("2.500"))
                .taxRate(new BigDecimal("30"))
                .creditValueDateMode(InterestValueDateMode.TRANSACTION_DATE)
                .debitValueDateMode(InterestValueDateMode.TRANSACTION_DATE)
                .dayCountBasis(InterestDayCountBasis.ACTUAL_365)
                .build();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void missingOrForeignAccountsAreSkippedWithoutWritingInterest(boolean mapped) {
        SkroogeImportMetadata metadata = metadata();
        service.importInterest(metadata, mapped ? Map.of(10L, 20L) : Map.of(), 7L);
        assertThat(metadata.getSkippedInterestRateCount()).isEqualTo(1);
        assertThat(metadata.getInterestWarnings())
                .containsExactly(new SkroogeImportMetadata.RuleWarning(1L, "accountMissing"));
        verifyNoInteractions(variations);
        if (mapped) verify(accounts).findByIdAndUserId(20L, 7L);
        else verifyNoInteractions(accounts);
    }

    @ParameterizedTest
    @ValueSource(strings = {"same", "credit", "debit", "basis", "rate"})
    void reconcilesExistingPeriodsWithoutDuplicatingOrOverwritingConflictingRates(String changed) {
        Account account = account(true, InterestPeriod.MONTHLY);
        when(accounts.findByIdAndUserId(20L, 7L)).thenReturn(Optional.of(account));
        InterestRateVariation variation = existing();
        switch (changed) {
            case "credit" -> variation.setCreditValueDateMode(InterestValueDateMode.FORTNIGHT);
            case "debit" -> variation.setDebitValueDateMode(InterestValueDateMode.DAYS_1);
            case "basis" -> variation.setDayCountBasis(InterestDayCountBasis.THIRTY_360);
            case "rate" -> variation.setRate(new BigDecimal("3"));
            default -> {}
        }
        when(variations.findByAccountIdOrderByValidFromDesc(20L)).thenReturn(List.of(variation));
        SkroogeImportMetadata metadata = metadata();
        service.importInterest(metadata, Map.of(10L, 20L), 7L);
        if (changed.equals("same")) {
            assertThat(metadata.getExistingInterestRateCount()).isEqualTo(1);
            verify(variations, never()).save(any());
        } else if (changed.equals("rate")) {
            assertThat(metadata.getSkippedInterestRateCount()).isEqualTo(1);
            assertThat(metadata.getInterestWarnings())
                    .containsExactly(new SkroogeImportMetadata.RuleWarning(1L, "conflict"));
            assertThat(variation.getRate()).isEqualByComparingTo("3");
            verify(variations, never()).save(any());
        } else {
            assertThat(metadata.getImportedInterestRateCount()).isEqualTo(1);
            verify(variations).save(variation);
            assertThat(variation.getCreditValueDateMode())
                    .isEqualTo(InterestValueDateMode.TRANSACTION_DATE);
            assertThat(variation.getDebitValueDateMode())
                    .isEqualTo(InterestValueDateMode.TRANSACTION_DATE);
            assertThat(variation.getDayCountBasis()).isEqualTo(InterestDayCountBasis.ACTUAL_365);
        }
        assertThat(variation.getTaxRate()).isEqualByComparingTo("30");
        assertThat(account.getInterestPeriod()).isEqualTo(InterestPeriod.MONTHLY);
        verify(accounts, never()).save(any());
    }

    @ParameterizedTest
    @CsvSource({"true,MONTHLY", "false,MONTHLY", ",MONTHLY", "true,", "false,", ","})
    void enablesImportedInterestWithoutReplacingAnExistingPaymentPeriod(
            Boolean enabled, InterestPeriod period) {
        Account account = account(enabled, period);
        when(accounts.findByIdAndUserId(20L, 7L)).thenReturn(Optional.of(account));
        SkroogeImportMetadata metadata = metadata();
        service.importInterest(metadata, Map.of(10L, 20L), 7L);
        assertThat(metadata.getImportedInterestRateCount()).isEqualTo(1);
        assertThat(account.getIsInterestEnabled()).isTrue();
        assertThat(account.getInterestPeriod())
                .isEqualTo(period == null ? InterestPeriod.ANNUAL : period);
        if (!Boolean.TRUE.equals(enabled) || period == null) verify(accounts).save(account);
        else verify(accounts, never()).save(any());
    }

    @Test
    void retryResetsExecutionWarningsWhileRetainingUnsupportedSourceRows() {
        SkroogeImportMetadata metadata = metadata();
        metadata.setSourceInterestRateCount(2);
        metadata.setImportedInterestRateCount(3);
        metadata.setExistingInterestRateCount(4);
        metadata.setInterestWarnings(
                List.of(
                        new SkroogeImportMetadata.RuleWarning(9L, "unsupported"),
                        new SkroogeImportMetadata.RuleWarning(1L, "conflict")));
        service.importInterest(metadata, Map.of(), 7L);
        assertThat(metadata.getSkippedInterestRateCount()).isEqualTo(2);
        assertThat(metadata.getImportedInterestRateCount()).isZero();
        assertThat(metadata.getExistingInterestRateCount()).isZero();
        assertThat(metadata.getInterestWarnings())
                .containsExactly(
                        new SkroogeImportMetadata.RuleWarning(9L, "unsupported"),
                        new SkroogeImportMetadata.RuleWarning(1L, "accountMissing"));
    }
}
