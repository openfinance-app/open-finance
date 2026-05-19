package org.openfinance.util;

import org.openfinance.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service to clean all database tables in the correct order to satisfy referential integrity
 * constraints. Used primarily in integration tests.
 */
@Component
public class DatabaseCleanupService {

    @Autowired private TransactionRepository transactionRepository;
    @Autowired private TransactionSplitRepository transactionSplitRepository;
    @Autowired private TransactionRuleRepository transactionRuleRepository;
    @Autowired private RecurringTransactionRepository recurringTransactionRepository;
    @Autowired private BudgetRepository budgetRepository;
    @Autowired private BudgetAlertRepository budgetAlertRepository;
    @Autowired private RealEstateRepository realEstateRepository;
    @Autowired private RealEstateValueHistoryRepository realEstateValueHistoryRepository;
    @Autowired private RealEstateSimulationRepository realEstateSimulationRepository;
    @Autowired private AssetRepository assetRepository;
    @Autowired private LiabilityRepository liabilityRepository;
    @Autowired private InterestRateVariationRepository interestRateVariationRepository;
    @Autowired private AccountRepository accountRepository;
    @Autowired private PayeeRepository payeeRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private AttachmentRepository attachmentRepository;
    @Autowired private AIConversationRepository aiConversationRepository;
    @Autowired private InsightRepository insightRepository;
    @Autowired private NetWorthRepository netWorthRepository;
    @Autowired private SecurityAuditLogRepository securityAuditLogRepository;
    @Autowired private UserSettingsRepository userSettingsRepository;
    @Autowired private ImportSessionRepository importSessionRepository;
    @Autowired private ExchangeRateRepository exchangeRateRepository;
    @Autowired private BackupRepository backupRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private InstitutionRepository institutionRepository;
    @Autowired private CurrencyRepository currencyRepository;

    @Transactional
    public void execute() {
        // Order matters for referential integrity!

        transactionSplitRepository.deleteAll();
        transactionRepository.deleteAll();
        recurringTransactionRepository.deleteAll();
        transactionRuleRepository.deleteAll();

        budgetAlertRepository.deleteAll();
        budgetRepository.deleteAll();

        realEstateValueHistoryRepository.deleteAll();
        realEstateSimulationRepository.deleteAll();
        realEstateRepository.deleteAll();

        assetRepository.deleteAll();
        interestRateVariationRepository.deleteAll();
        liabilityRepository.deleteAll();

        accountRepository.deleteAll();
        payeeRepository.deleteAll();

        // Hierarchical cleanup for categories
        categoryRepository
                .findAll()
                .forEach(
                        cat -> {
                            if (cat.getParentId() != null) {
                                cat.setParentId(null);
                                categoryRepository.save(cat);
                            }
                        });
        categoryRepository.deleteAll();

        attachmentRepository.deleteAll();
        aiConversationRepository.deleteAll();
        insightRepository.deleteAll();
        netWorthRepository.deleteAll();
        securityAuditLogRepository.deleteAll();
        userSettingsRepository.deleteAll();
        importSessionRepository.deleteAll();
        exchangeRateRepository.deleteAll();
        backupRepository.deleteAll();
        institutionRepository.deleteAll();

        userRepository.deleteAll();
        currencyRepository.deleteAll();
    }
}
