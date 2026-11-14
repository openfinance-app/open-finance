package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openfinance.repository.AccountRepository;
import org.openfinance.repository.AssetRepository;
import org.openfinance.repository.BudgetRepository;
import org.openfinance.repository.LiabilityRepository;
import org.openfinance.repository.RealEstateRepository;
import org.openfinance.repository.RecurringTransactionRepository;
import org.openfinance.repository.TransactionRepository;
import org.openfinance.repository.UserRepository;
import org.openfinance.security.EncryptionService;
import org.openfinance.security.KeyManagementService;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * The runner is a legacy one-shot repair tool that self-disables: encryption moved to JPA
 * AttributeConverters, so {@code run()} must never touch the database or the crypto services,
 * whether the property is on or off. The private repair machinery below {@code run()} is dead code
 * until the class is removed; this suite pins the only reachable, supported contract.
 */
@DisplayName("RepairRunner (self-disabled legacy runner)")
class RepairRunnerTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final BudgetRepository budgetRepository = mock(BudgetRepository.class);
    private final RecurringTransactionRepository recurringRepository =
            mock(RecurringTransactionRepository.class);
    private final TransactionRepository transactionRepository = mock(TransactionRepository.class);
    private final AccountRepository accountRepository = mock(AccountRepository.class);
    private final AssetRepository assetRepository = mock(AssetRepository.class);
    private final RealEstateRepository realEstateRepository = mock(RealEstateRepository.class);
    private final LiabilityRepository liabilityRepository = mock(LiabilityRepository.class);
    private final EncryptionService encryptionService = mock(EncryptionService.class);
    private final KeyManagementService keyManagement = mock(KeyManagementService.class);

    private RepairRunner runner(boolean enabled) {
        RepairRunner runner =
                new RepairRunner(
                        userRepository,
                        budgetRepository,
                        recurringRepository,
                        transactionRepository,
                        accountRepository,
                        assetRepository,
                        realEstateRepository,
                        liabilityRepository,
                        encryptionService,
                        keyManagement);
        ReflectionTestUtils.setField(runner, "repairEnabled", enabled);
        ReflectionTestUtils.setField(runner, "repairExecute", true);
        return runner;
    }

    private void verifyNothingTouched() {
        verifyNoInteractions(
                userRepository,
                budgetRepository,
                recurringRepository,
                transactionRepository,
                accountRepository,
                assetRepository,
                realEstateRepository,
                liabilityRepository,
                encryptionService,
                keyManagement);
    }

    @Test
    @DisplayName("Disabled by default: run() is a no-op")
    void disabledByDefault() {
        assertThatCode(() -> runner(false).run()).doesNotThrowAnyException();
        verifyNothingTouched();
    }

    @Test
    @DisplayName("Even when enabled (and execute=true) the runner only warns and touches nothing")
    void enabledStillTouchesNothing() {
        assertThatCode(() -> runner(true).run("ignored")).doesNotThrowAnyException();
        verifyNothingTouched();
    }
}
