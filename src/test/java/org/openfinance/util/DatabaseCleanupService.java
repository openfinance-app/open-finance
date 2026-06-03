package org.openfinance.util;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.openfinance.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service to clean all database tables in the correct order to satisfy
 * referential integrity
 * constraints. Used primarily in integration tests.
 */
@Component
public class DatabaseCleanupService {

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private TransactionRepository transactionRepository;
    @Autowired
    private TransactionSplitRepository transactionSplitRepository;
    @Autowired
    private TransactionRuleRepository transactionRuleRepository;
    @Autowired
    private RecurringTransactionRepository recurringTransactionRepository;
    @Autowired
    private BudgetRepository budgetRepository;
    @Autowired
    private BudgetAlertRepository budgetAlertRepository;
    @Autowired
    private RealEstateRepository realEstateRepository;
    @Autowired
    private RealEstateValueHistoryRepository realEstateValueHistoryRepository;
    @Autowired
    private RealEstateSimulationRepository realEstateSimulationRepository;
    @Autowired
    private AssetRepository assetRepository;
    @Autowired
    private LiabilityRepository liabilityRepository;
    @Autowired
    private InterestRateVariationRepository interestRateVariationRepository;
    @Autowired
    private AccountRepository accountRepository;
    @Autowired
    private PayeeRepository payeeRepository;
    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private AttachmentRepository attachmentRepository;
    @Autowired
    private AIConversationRepository aiConversationRepository;
    @Autowired
    private InsightRepository insightRepository;
    @Autowired
    private NetWorthRepository netWorthRepository;
    @Autowired
    private SecurityAuditLogRepository securityAuditLogRepository;
    @Autowired
    private UserSettingsRepository userSettingsRepository;
    @Autowired
    private ImportSessionRepository importSessionRepository;
    @Autowired
    private ExchangeRateRepository exchangeRateRepository;
    @Autowired
    private BackupRepository backupRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private InstitutionRepository institutionRepository;
    @Autowired
    private CurrencyRepository currencyRepository;

    @Transactional
    public void execute() {
        // Order matters for referential integrity!

        transactionSplitRepository.deleteAllInBatch();
        transactionRepository.deleteAllInBatch();
        recurringTransactionRepository.deleteAllInBatch();
        transactionRuleRepository.deleteAllInBatch();

        budgetAlertRepository.deleteAllInBatch();
        budgetRepository.deleteAllInBatch();

        realEstateValueHistoryRepository.deleteAllInBatch();
        realEstateSimulationRepository.deleteAllInBatch();
        realEstateRepository.deleteAllInBatch();

        assetRepository.deleteAllInBatch();
        interestRateVariationRepository.deleteAllInBatch();
        liabilityRepository.deleteAllInBatch();

        accountRepository.deleteAllInBatch();
        payeeRepository.deleteAllInBatch();

        // Break self-references before the bulk delete to satisfy FK constraints.
        entityManager
                .createQuery("UPDATE Category c SET c.parentId = null WHERE c.parentId IS NOT NULL")
                .executeUpdate();
        categoryRepository.deleteAllInBatch();

        attachmentRepository.deleteAllInBatch();
        aiConversationRepository.deleteAllInBatch();
        insightRepository.deleteAllInBatch();
        netWorthRepository.deleteAllInBatch();
        securityAuditLogRepository.deleteAllInBatch();
        userSettingsRepository.deleteAllInBatch();
        importSessionRepository.deleteAllInBatch();
        exchangeRateRepository.deleteAllInBatch();
        backupRepository.deleteAllInBatch();
        institutionRepository.deleteAllInBatch();

        userRepository.deleteAllInBatch();
        currencyRepository.deleteAllInBatch();

        // Flush to ensure DELETEs are executed before any subsequent INSERTs
        // (Hibernate defers DELETEs after INSERTs at flush time, which can cause
        // unique constraint violations when re-inserting with the same keys)
        entityManager.flush();
    }
}
