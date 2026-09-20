package org.openfinance.specification;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openfinance.dto.TransactionSearchCriteria;
import org.openfinance.entity.Account;
import org.openfinance.entity.AccountType;
import org.openfinance.entity.Asset;
import org.openfinance.entity.AssetType;
import org.openfinance.entity.Liability;
import org.openfinance.entity.LiabilityType;
import org.openfinance.entity.MovementType;
import org.openfinance.entity.PropertyType;
import org.openfinance.entity.RealEstateProperty;
import org.openfinance.entity.Transaction;
import org.openfinance.entity.TransactionType;
import org.openfinance.entity.User;
import org.openfinance.repository.AccountRepository;
import org.openfinance.repository.AssetRepository;
import org.openfinance.repository.LiabilityRepository;
import org.openfinance.repository.RealEstateRepository;
import org.openfinance.repository.TransactionRepository;
import org.openfinance.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Repository-level tests for the {@code realEstateId} / {@code assetId} transaction search filters
 * (Task 6): filtering returns only the transactions linked to the given instrument.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@DisplayName("Transaction search filters for linked property / asset")
class TransactionSpecificationTest {

    @Autowired private TransactionRepository transactionRepository;

    @Autowired private UserRepository userRepository;

    @Autowired private AccountRepository accountRepository;

    @Autowired private RealEstateRepository realEstateRepository;

    @Autowired private AssetRepository assetRepository;

    @Autowired private LiabilityRepository liabilityRepository;

    private Long userId;

    private Long accountId;

    private Long propertyId;

    private Long assetId;

    private Long liabilityId;

    @BeforeEach
    void setUp() {
        transactionRepository.deleteAll();
        realEstateRepository.deleteAll();
        assetRepository.deleteAll();
        liabilityRepository.deleteAll();
        accountRepository.deleteAll();
        userRepository.deleteAll();

        User user =
                User.builder()
                        .username("specuser")
                        .email("specuser@test.com")
                        .passwordHash("$2a$10$hashedPasswordExample123456789")
                        .masterPasswordSalt("base64EncodedSaltExample==")
                        .baseCurrency("USD")
                        .build();
        userId = userRepository.save(user).getId();

        accountId =
                accountRepository
                        .save(
                                Account.builder()
                                        .userId(userId)
                                        .name("Checking")
                                        .type(AccountType.CHECKING)
                                        .balance(new BigDecimal("1000.00"))
                                        .currency("USD")
                                        .isActive(true)
                                        .build())
                        .getId();

        propertyId =
                realEstateRepository
                        .save(
                                RealEstateProperty.builder()
                                        .userId(userId)
                                        .name("Cabin")
                                        .address("1 Forest Rd")
                                        .propertyType(PropertyType.RESIDENTIAL)
                                        .purchasePrice(new BigDecimal("100000.00").toPlainString())
                                        .purchaseDate(LocalDate.now().minusYears(2))
                                        .currentValue(new BigDecimal("120000.00").toPlainString())
                                        .currency("USD")
                                        .build())
                        .getId();

        assetId =
                assetRepository
                        .save(
                                Asset.builder()
                                        .userId(userId)
                                        .type(AssetType.VEHICLE)
                                        .name("Car")
                                        .quantity(BigDecimal.ONE)
                                        .purchasePrice(new BigDecimal("20000.00"))
                                        .currentPrice(new BigDecimal("20000.00"))
                                        .currency("USD")
                                        .purchaseDate(LocalDate.now().minusYears(1))
                                        .build())
                        .getId();

        Liability mortgage = new Liability();
        mortgage.setUserId(userId);
        mortgage.setName("Mortgage");
        mortgage.setType(LiabilityType.MORTGAGE);
        mortgage.setPrincipal("200000");
        mortgage.setCurrentBalance("99200");
        mortgage.setInterestRate("3.5");
        mortgage.setMinimumPayment("1200");
        mortgage.setCurrency("USD");
        mortgage.setStartDate(LocalDate.now().minusYears(2));
        liabilityId = liabilityRepository.save(mortgage).getId();
    }

    private Transaction linkedTx(Long realEstateId, Long linkedAssetId, MovementType movementType) {
        return Transaction.builder()
                .userId(userId)
                .accountId(accountId)
                .type(TransactionType.EXPENSE)
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .date(LocalDate.now())
                .movementType(movementType)
                .realEstateId(realEstateId)
                .assetId(linkedAssetId)
                .isDeleted(false)
                .build();
    }

    @Test
    @DisplayName("realEstateId filter returns only transactions linked to that property")
    void cashFlowDrilldownExcludesTransferLegsBeforePagination() {
        Transaction purchase = transactionRepository.save(linkedTx(null, null, null));
        Transaction source = linkedTx(null, null, null);
        source.setTransferId("savings-transfer");
        transactionRepository.save(source);
        Transaction destination = linkedTx(null, null, null);
        destination.setTransferId("savings-transfer");
        destination.setType(TransactionType.INCOME);
        transactionRepository.save(destination);
        Transaction legacy = linkedTx(null, null, null);
        legacy.setType(TransactionType.TRANSFER);
        transactionRepository.save(legacy);
        TransactionSearchCriteria criteria =
                TransactionSearchCriteria.builder()
                        .type(TransactionType.EXPENSE)
                        .excludeTransfers(true)
                        .dateFrom(LocalDate.now())
                        .dateTo(LocalDate.now())
                        .build();

        org.springframework.data.domain.Page<Transaction> results =
                transactionRepository.findAll(
                        TransactionSpecification.buildSpecification(userId, criteria, null),
                        org.springframework.data.domain.PageRequest.of(0, 1));

        assertThat(results.getTotalElements()).isEqualTo(1);
        assertThat(results.getContent())
                .extracting(Transaction::getId)
                .containsExactly(purchase.getId());
        criteria.setType(null);
        assertThat(
                        transactionRepository.findAll(
                                TransactionSpecification.buildSpecification(
                                        userId, criteria, null)))
                .extracting(Transaction::getId)
                .containsExactly(purchase.getId());
        criteria.setExcludeTransfers(false);
        assertThat(
                        transactionRepository.count(
                                TransactionSpecification.buildSpecification(
                                        userId, criteria, null)))
                .isEqualTo(4);
    }

    @Test
    @DisplayName("realEstateId filter returns only transactions linked to that property")
    void realEstateIdFilterReturnsOnlyLinkedTransactions() {
        transactionRepository.save(linkedTx(propertyId, null, MovementType.CAPITAL_IMPROVEMENT));
        transactionRepository.save(linkedTx(null, assetId, MovementType.MAINTENANCE));
        transactionRepository.save(linkedTx(null, null, null));

        TransactionSearchCriteria criteria =
                TransactionSearchCriteria.builder().realEstateId(propertyId).build();

        List<Transaction> results =
                transactionRepository.findAll(
                        TransactionSpecification.buildSpecification(userId, criteria, null));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getRealEstateId()).isEqualTo(propertyId);
    }

    @Test
    @DisplayName("assetId filter returns only transactions linked to that asset")
    void assetIdFilterReturnsOnlyLinkedTransactions() {
        transactionRepository.save(linkedTx(propertyId, null, MovementType.CAPITAL_IMPROVEMENT));
        transactionRepository.save(linkedTx(null, assetId, MovementType.MAINTENANCE));
        transactionRepository.save(linkedTx(null, null, null));

        TransactionSearchCriteria criteria =
                TransactionSearchCriteria.builder().assetId(assetId).build();

        List<Transaction> results =
                transactionRepository.findAll(
                        TransactionSpecification.buildSpecification(userId, criteria, null));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getAssetId()).isEqualTo(assetId);
    }

    @Test
    @DisplayName("liabilityId filter returns only transactions linked to that liability")
    void liabilityIdFilterReturnsOnlyLinkedTransactions() {
        transactionRepository.save(
                Transaction.builder()
                        .userId(userId)
                        .accountId(accountId)
                        .type(TransactionType.EXPENSE)
                        .amount(new BigDecimal("100.00"))
                        .currency("USD")
                        .date(LocalDate.now())
                        .movementType(MovementType.REPAYMENT)
                        .liabilityId(liabilityId)
                        .isDeleted(false)
                        .build());
        transactionRepository.save(linkedTx(null, null, null));

        TransactionSearchCriteria criteria =
                TransactionSearchCriteria.builder().liabilityId(liabilityId).build();

        List<Transaction> results =
                transactionRepository.findAll(
                        TransactionSpecification.buildSpecification(userId, criteria, null));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getLiabilityId()).isEqualTo(liabilityId);
    }
}
