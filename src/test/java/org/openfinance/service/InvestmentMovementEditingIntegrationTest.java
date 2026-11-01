package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openfinance.config.TestDatabaseConfig;
import org.openfinance.dto.HistoricalPrice;
import org.openfinance.dto.InvestmentMovementRequest;
import org.openfinance.dto.InvestmentMovementRequest.Operation;
import org.openfinance.dto.InvestmentMovementRequest.Pricing;
import org.openfinance.dto.LoginRequest;
import org.openfinance.dto.TransactionRequest;
import org.openfinance.dto.TransactionResponse;
import org.openfinance.dto.TransferUpdateRequest;
import org.openfinance.dto.UserRegistrationRequest;
import org.openfinance.entity.Account;
import org.openfinance.entity.AccountType;
import org.openfinance.entity.Asset;
import org.openfinance.entity.AssetType;
import org.openfinance.entity.Transaction;
import org.openfinance.entity.TransactionType;
import org.openfinance.exception.InvalidTransactionException;
import org.openfinance.repository.AccountRepository;
import org.openfinance.repository.AssetRepository;
import org.openfinance.repository.TransactionRepository;
import org.openfinance.repository.UserRepository;
import org.openfinance.security.EncryptionContext;
import org.openfinance.security.EncryptionKeyCache;
import org.openfinance.util.DatabaseCleanupService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/** Real SQLite, migrations, encrypted values, transaction boundaries and balance reconciliation. */
@SpringBootTest
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
@Import(TestDatabaseConfig.class)
@ActiveProfiles("test")
class InvestmentMovementEditingIntegrationTest {
    private static final LocalDate OPENING = LocalDate.of(2025, 7, 20);
    private static final LocalDate TRANSFER_DATE = LocalDate.of(2026, 6, 24);
    @MockBean private MarketDataService marketData;
    @MockBean private ExchangeRateService exchangeRates;
    @MockBean private OperationHistoryService history;
    @Autowired private TransactionService service;
    @Autowired private InvestmentMovementService investments;
    @Autowired private AccountService accountService;
    @Autowired private AssetService assetService;
    @Autowired private AccountRepository accounts;
    @Autowired private AssetRepository assets;
    @Autowired private TransactionRepository transactions;
    @Autowired private UserRepository users;
    @Autowired private UserService userService;
    @Autowired private AuthService auth;
    @Autowired private EncryptionKeyCache keys;
    @Autowired private DatabaseCleanupService cleanup;
    @Autowired private org.springframework.test.web.servlet.MockMvc http;
    @Autowired private com.fasterxml.jackson.databind.ObjectMapper json;
    private org.openfinance.dto.LoginResponse login;
    private Long userId;
    private Account btc;
    private Account destination;
    private Asset holding;

    @BeforeEach
    void setUp() {
        cleanup.execute();
        when(exchangeRates.convert(any(), anyString(), anyString()))
                .thenAnswer(i -> i.getArgument(0));
        when(exchangeRates.convert(any(), anyString(), anyString(), any()))
                .thenAnswer(i -> i.getArgument(0));
        when(exchangeRates.getExchangeRate(anyString(), anyString(), any()))
                .thenReturn(BigDecimal.ONE);
        userService.registerUser(
                UserRegistrationRequest.builder()
                        .username("movement-editor")
                        .email("movement-editor@example.com")
                        .password("Password123!")
                        .masterPassword("Master123!")
                        .skipSeeding(true)
                        .build());
        userId = users.findByUsername("movement-editor").orElseThrow().getId();
        login =
                auth.login(
                        LoginRequest.builder()
                                .username("movement-editor")
                                .password("Password123!")
                                .masterPassword("Master123!")
                                .build());
        EncryptionContext.setKey(keys.getKeyBySessionToken(login.getEncryptionKey()).orElseThrow());
        btc = account("BTC");
        destination = account("Polymarket");
        holding =
                assets.save(
                        Asset.builder()
                                .userId(userId)
                                .accountId(btc.getId())
                                .name("Bitcoin")
                                .type(AssetType.CRYPTO)
                                .symbol("BTC-EUR")
                                .currency("EUR")
                                .purchaseDate(OPENING)
                                .quantity(BigDecimal.ZERO)
                                .purchasePrice(BigDecimal.ZERO)
                                .currentPrice(new BigDecimal("69380.22"))
                                .importSource("skrooge:unit:5")
                                .importOpeningQuantity(BigDecimal.ZERO)
                                .importOpeningCost(BigDecimal.ZERO)
                                .build());
    }

    @AfterEach
    void clearEncryption() {
        EncryptionContext.clear();
    }

    @Test
    void authenticatedApiExposesQuotesAndEditsTransferUnitsWithNestedValidation() throws Exception {
        acquisition("1", "100000");
        TransactionResponse transfer = cashTransfer();
        quoteAt("100000");
        http.perform(
                        get("/api/v1/assets/{id}/investment-quote", holding.getId())
                                .header("Authorization", "Bearer " + login.getToken())
                                .header("X-Encryption-Session", login.getEncryptionKey())
                                .param("amount", "377.65")
                                .param("currency", "EUR")
                                .param("date", TRANSFER_DATE.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(0.0037765));
        TransferUpdateRequest edit = transferEdit(transfer);
        edit.setInvestment(
                InvestmentMovementRequest.builder()
                        .assetId(holding.getId())
                        .operation(Operation.REMOVE)
                        .pricing(Pricing.HISTORICAL)
                        .build());
        http.perform(
                        put("/api/v1/transactions/transfers/{id}", transfer.getTransferId())
                                .header("Authorization", "Bearer " + login.getToken())
                                .header("X-Encryption-Session", login.getEncryptionKey())
                                .contentType("application/json")
                                .content(json.writeValueAsBytes(edit)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.investmentQuantity").value(-0.0037765))
                .andExpect(jsonPath("$.assetName").value("Bitcoin"))
                .andExpect(jsonPath("$.investmentCurrency").value("EUR"));
        edit.setInvestment(
                edit.getInvestment().toBuilder()
                        .pricing(Pricing.QUANTITY)
                        .quantity(new BigDecimal("0.1234567890123456789"))
                        .build());
        http.perform(
                        put("/api/v1/transactions/transfers/{id}", transfer.getTransferId())
                                .header("Authorization", "Bearer " + login.getToken())
                                .header("X-Encryption-Session", login.getEncryptionKey())
                                .contentType("application/json")
                                .content(json.writeValueAsBytes(edit)))
                .andExpect(status().isBadRequest());
        EncryptionContext.setKey(keys.getKeyBySessionToken(login.getEncryptionKey()).orElseThrow());
        assertQuantity("0.9962235");
        assertThat(json.readValue("{}", TransactionRequest.class).isInvestmentPresent()).isFalse();
        assertThat(
                        json.readValue("{\"investment\":null}", TransactionRequest.class)
                                .isInvestmentPresent())
                .isTrue();
    }

    @Test
    void previewsInsufficientUnitsAndExplainsTheRejectedTransferWithoutChangingBalances()
            throws Exception {
        acquisition("0.00453915", "461.00078382204");
        acquisition("0.00072004", "73.128009513504");
        acquisition("0.00162876", "165.418555601376");
        TransactionResponse transfer = cashTransfer();
        quoteAt("53721.890625");

        http.perform(
                        get("/api/v1/assets/{id}/investment-quote", holding.getId())
                                .header("Authorization", "Bearer " + login.getToken())
                                .header("X-Encryption-Session", login.getEncryptionKey())
                                .param("amount", "377.65")
                                .param("currency", "EUR")
                                .param("date", TRANSFER_DATE.toString())
                                .param("transactionId", transfer.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(0.007029722811435399))
                .andExpect(jsonPath("$.availableQuantity").value(0.00688795));

        TransferUpdateRequest edit = transferEdit(transfer);
        edit.setInvestment(
                InvestmentMovementRequest.builder()
                        .assetId(holding.getId())
                        .operation(Operation.REMOVE)
                        .pricing(Pricing.HISTORICAL)
                        .build());
        http.perform(
                        put("/api/v1/transactions/transfers/{id}", transfer.getTransferId())
                                .header("Authorization", "Bearer " + login.getToken())
                                .header("X-Encryption-Session", login.getEncryptionKey())
                                .header("Accept-Language", "en")
                                .contentType("application/json")
                                .content(json.writeValueAsBytes(edit)))
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.message")
                                .value(
                                        org.hamcrest.Matchers.containsString(
                                                "0.007029722811435399")))
                .andExpect(
                        jsonPath("$.message")
                                .value(org.hamcrest.Matchers.containsString("0.00688795")))
                .andExpect(
                        jsonPath("$.message")
                                .value(org.hamcrest.Matchers.containsString("2026-06-24")));
        EncryptionContext.setKey(keys.getKeyBySessionToken(login.getEncryptionKey()).orElseThrow());
        assertQuantity("0.00688795");
        assertCash(btc, "-377.65");
        assertCash(destination, "377.65");
        assertThat(transactions.findById(transfer.getId()).orElseThrow().getAssetId()).isNull();
    }

    @Test
    void quoteAvailabilityReservesLaterWithdrawalsAndExcludesTheMovementBeingEdited() {
        acquisition("1", "100000");
        TransactionRequest removal = movement(Operation.REMOVE, "0.6", "60000");
        removal.setDate(TRANSFER_DATE.plusDays(1));
        TransactionResponse withdrawal = service.createTransaction(userId, removal);
        TransactionRequest laterAcquisition = movement(Operation.ADD, "5", "500000");
        laterAcquisition.setDate(TRANSFER_DATE.plusDays(2));
        service.createTransaction(userId, laterAcquisition);
        quoteAt("100000");

        assertThat(
                        investments
                                .quote(
                                        holding.getId(),
                                        userId,
                                        new BigDecimal("377.65"),
                                        "EUR",
                                        TRANSFER_DATE,
                                        null)
                                .getAvailableQuantity())
                .isEqualByComparingTo("0.4");
        assertThat(
                        investments
                                .quote(
                                        holding.getId(),
                                        userId,
                                        new BigDecimal("377.65"),
                                        "EUR",
                                        TRANSFER_DATE,
                                        withdrawal.getId())
                                .getAvailableQuantity())
                .isEqualByComparingTo("1");
        assertQuantity("5.4");
    }

    @Test
    void quoteAvailabilityUsesManualOpeningPositionsOnlyFromTheirOpeningDate() {
        holding.setImportSource(null);
        holding.setImportOpeningQuantity(null);
        holding.setImportOpeningCost(null);
        holding.setQuantity(new BigDecimal("0.5"));
        assets.save(holding);
        quoteAt("100000");
        assertThat(
                        investments
                                .quote(
                                        holding.getId(),
                                        userId,
                                        new BigDecimal("377.65"),
                                        "EUR",
                                        TRANSFER_DATE,
                                        null)
                                .getAvailableQuantity())
                .isEqualByComparingTo("0.5");
        holding.setPurchaseDate(TRANSFER_DATE.plusDays(1));
        assets.save(holding);
        assertThat(
                        investments
                                .quote(
                                        holding.getId(),
                                        userId,
                                        new BigDecimal("377.65"),
                                        "EUR",
                                        TRANSFER_DATE,
                                        null)
                                .getAvailableQuantity())
                .isZero();
        assertThat(assets.findById(holding.getId()).orElseThrow().getImportOpeningQuantity())
                .isNull();
    }

    @Test
    void historicalEstimatesRespectOriginalCurrencyAndManualOverridePersists() {
        holding.setCurrency("USD");
        assets.save(holding);
        when(exchangeRates.convert(any(), anyString(), anyString(), any()))
                .thenAnswer(
                        invocation -> {
                            BigDecimal amount = invocation.getArgument(0);
                            return "USD".equals(invocation.getArgument(2))
                                    ? amount.multiply(new BigDecimal("2"))
                                    : amount.divide(new BigDecimal("2"));
                        });
        when(marketData.getHistoricalPrices(anyString(), any(), any(), anyString()))
                .thenReturn(
                        List.of(
                                HistoricalPrice.builder()
                                        .date(OPENING)
                                        .currency("USD")
                                        .close(new BigDecimal("100"))
                                        .build()));
        TransactionRequest buy = movement(Operation.BUY, "1", "50");
        buy.setOriginalAmount(new BigDecimal("100"));
        buy.setOriginalCurrency("USD");
        buy.setConversionRate(new BigDecimal("0.5"));
        buy.setInvestment(buy.getInvestment().toBuilder().pricing(Pricing.HISTORICAL).build());
        TransactionResponse created = service.createTransaction(userId, buy);
        assertQuantity("1");
        assertCash(btc, "-50");
        assertThat(created.getInvestmentValue()).isEqualByComparingTo("100");
        clearInvocations(marketData, exchangeRates);
        buy.setNotes("Metadata only");
        service.updateTransaction(created.getId(), userId, buy);
        verifyNoInteractions(marketData, exchangeRates);
        buy.setInvestment(buy.getInvestment().toBuilder().pricing(Pricing.QUANTITY).build());
        TransactionResponse override = service.updateTransaction(created.getId(), userId, buy);
        assertThat(override.getInvestmentPriceSource()).isEqualTo("RECORDED");
        assertQuantity("1");
    }

    @Test
    void convertsCashTransferToHoldingRemovalAndPreservesSavedEstimateOnMetadataEdits() {
        acquisition("0.00688795", "699.54734893692");
        TransactionResponse transfer = cashTransfer();
        assertCash(btc, "-377.65");
        quoteAt("50000"); // Deterministic test price, not a claim about the market on that date.
        TransferUpdateRequest edit = transferEdit(transfer);
        edit.setInvestment(
                InvestmentMovementRequest.builder()
                        .assetId(holding.getId())
                        .operation(Operation.REMOVE)
                        .pricing(Pricing.HISTORICAL)
                        .build());
        // Too little BTC for this deliberately low quote: the entire transfer edit rolls back.
        assertThatThrownBy(() -> service.updateTransfer(transfer.getTransferId(), userId, edit))
                .isInstanceOf(InvalidTransactionException.class);
        assertCash(btc, "-377.65");
        assertQuantity("0.00688795");
        quoteAt("100000");
        TransactionResponse result = service.updateTransfer(transfer.getTransferId(), userId, edit);
        assertThat(result.getInvestmentQuantity()).isEqualByComparingTo("-0.0037765");
        assertThat(result.getInvestmentPriceSource()).isEqualTo("HISTORICAL_CLOSE");
        assertThat(result.getInvestmentPriceDate()).isEqualTo(TRANSFER_DATE);
        assertThat(result.getInvestmentPrice()).isEqualByComparingTo("100000");
        assertThat(result.isInvestmentCash()).isFalse();
        assertQuantity("0.00311145");
        assertCash(btc, "0");
        assertCash(destination, "377.65");
        assertThat(accountService.getAccountById(btc.getId(), userId).getBalance())
                .isEqualByComparingTo(
                        new BigDecimal("0.00311145").multiply(new BigDecimal("69380.22")));
        clearInvocations(marketData, exchangeRates);
        edit.setNotes("Reviewed");
        service.updateTransfer(transfer.getTransferId(), userId, edit);
        assertQuantity("0.00311145");
        verifyNoInteractions(marketData);
        assertThat(
                        investments
                                .quote(
                                        holding.getId(),
                                        userId,
                                        new BigDecimal("200"),
                                        "EUR",
                                        TRANSFER_DATE,
                                        result.getId())
                                .getPrice())
                .isEqualByComparingTo("100000");
        verifyNoInteractions(marketData);
        service.deleteTransaction(result.getId(), userId);
        assertQuantity("0.00688795");
        assertCash(btc, "0");
        assertCash(destination, "0");
    }

    @Test
    void editsImportedUnitsAndMovesTheirLinkWithoutDoubleCountingCash() {
        TransactionResponse purchase = acquisition("0.00688795", "699.54734893692");
        Asset other =
                assets.save(
                        Asset.builder()
                                .userId(userId)
                                .accountId(destination.getId())
                                .name("Other BTC")
                                .type(AssetType.CRYPTO)
                                .symbol("BTC-EUR")
                                .currency("EUR")
                                .purchaseDate(OPENING)
                                .quantity(BigDecimal.ONE)
                                .purchasePrice(new BigDecimal("100"))
                                .currentPrice(new BigDecimal("200"))
                                .build());
        TransactionRequest edit = movement(Operation.ADD, "0.004", "400");
        edit.setAccountId(destination.getId());
        edit.setInvestment(edit.getInvestment().toBuilder().assetId(other.getId()).build());
        service.updateTransaction(purchase.getId(), userId, edit);
        assertQuantity("0");
        assertThat(assets.findById(other.getId()).orElseThrow().getQuantity())
                .isEqualByComparingTo("1.004");
        assertCash(btc, "0");
        assertCash(destination, "0");
        service.deleteTransaction(purchase.getId(), userId);
        assertThat(assets.findById(other.getId()).orElseThrow().getQuantity())
                .isEqualByComparingTo("1");
    }

    @Test
    void rejectsEditingOrDeletingAnAcquisitionNeededByLaterRemovalAndRollsBackCash() {
        TransactionResponse purchase = acquisition("1", "100");
        TransactionRequest remove = movement(Operation.REMOVE, "0.75", "75");
        remove.setDate(TRANSFER_DATE);
        service.createTransaction(userId, remove);
        assertThatThrownBy(
                        () ->
                                service.updateTransaction(
                                        purchase.getId(),
                                        userId,
                                        movement(Operation.ADD, "0.5", "50")))
                .isInstanceOf(InvalidTransactionException.class);
        assertThatThrownBy(() -> service.deleteTransaction(purchase.getId(), userId))
                .isInstanceOf(InvalidTransactionException.class);
        assertQuantity("0.25");
        assertThat(transactions.findById(purchase.getId()).orElseThrow().getInvestmentQuantity())
                .isEqualByComparingTo("1");
        assertCash(btc, "0");
    }

    @Test
    void canReplaceAnInvestmentWithAPhysicalCostWithoutLosingTheNewLink() {
        TransactionResponse buy =
                service.createTransaction(userId, movement(Operation.BUY, "1", "100"));
        Asset vehicle =
                assets.save(
                        Asset.builder()
                                .userId(userId)
                                .name("Vehicle")
                                .type(AssetType.VEHICLE)
                                .quantity(BigDecimal.ONE)
                                .purchasePrice(new BigDecimal("200"))
                                .currentPrice(new BigDecimal("200"))
                                .currency("EUR")
                                .purchaseDate(OPENING)
                                .build());
        TransactionRequest edit = movement(Operation.BUY, "1", "50");
        edit.setInvestment(null);
        edit.setAssetId(vehicle.getId());
        edit.setMovementType(org.openfinance.entity.MovementType.CAPITAL_IMPROVEMENT);
        TransactionResponse updated = service.updateTransaction(buy.getId(), userId, edit);
        assertThat(updated.getInvestmentQuantity()).isNull();
        assertThat(updated.getAssetId()).isEqualTo(vehicle.getId());
        assertQuantity("0");
        assertCash(btc, "-50");
        assertThat(assetService.getAssetById(vehicle.getId(), userId).getTotalCost())
                .isEqualByComparingTo("250");
        TransactionRequest conflicting = movement(Operation.ADD, "1", "100");
        conflicting.setAssetId(vehicle.getId());
        assertThatThrownBy(() -> service.createTransaction(userId, conflicting))
                .isInstanceOf(InvalidTransactionException.class);
        assertQuantity("0");
    }

    @Test
    void buySellAndUnlinkUpdateCashAndCostBasis() {
        TransactionResponse buy =
                service.createTransaction(userId, movement(Operation.BUY, "2", "200"));
        assertCash(btc, "-200");
        TransactionRequest sell = movement(Operation.SELL, "0.5", "70");
        sell.setDate(TRANSFER_DATE);
        TransactionResponse sale = service.createTransaction(userId, sell);
        assertQuantity("1.5");
        assertThat(assets.findById(holding.getId()).orElseThrow().getTotalCost())
                .isEqualByComparingTo("150");
        assertCash(btc, "-130");
        sell.setInvestment(null);
        service.updateTransaction(sale.getId(), userId, sell);
        assertQuantity("2");
        assertCash(btc, "-130");
        service.deleteTransaction(buy.getId(), userId);
        assertQuantity("0");
        assertCash(btc, "70");
    }

    @Test
    void requiresExactDatedQuoteAndValidatesHoldingOwnershipAndAccount() {
        when(marketData.getHistoricalPrices(anyString(), any(), any(), anyString()))
                .thenReturn(
                        List.of(
                                HistoricalPrice.builder()
                                        .date(TRANSFER_DATE.minusDays(1))
                                        .currency("EUR")
                                        .close(new BigDecimal("100000"))
                                        .build()));
        assertThatThrownBy(
                        () ->
                                investments.quote(
                                        holding.getId(),
                                        userId,
                                        BigDecimal.TEN,
                                        "EUR",
                                        TRANSFER_DATE,
                                        null))
                .isInstanceOf(InvalidTransactionException.class)
                .hasMessageContaining("historical price");
        assertThatThrownBy(
                        () ->
                                investments.quote(
                                        holding.getId(),
                                        userId + 1,
                                        BigDecimal.TEN,
                                        "EUR",
                                        TRANSFER_DATE,
                                        null))
                .isInstanceOf(InvalidTransactionException.class);
        TransactionRequest wrongAccount = movement(Operation.ADD, "1", "100");
        wrongAccount.setAccountId(destination.getId());
        assertThatThrownBy(() -> service.createTransaction(userId, wrongAccount))
                .isInstanceOf(InvalidTransactionException.class);
        assertQuantity("0");
        assertCash(destination, "0");
    }

    @Test
    void manualHoldingOpeningPositionSurvivesRecalculationAndDeletion() {
        holding.setImportSource(null);
        holding.setImportOpeningQuantity(null);
        holding.setImportOpeningCost(null);
        holding.setQuantity(new BigDecimal("2"));
        holding.setPurchasePrice(new BigDecimal("100"));
        assets.save(holding);
        TransactionResponse removal =
                service.createTransaction(userId, movement(Operation.REMOVE, "0.25", "50"));
        accountService.recalculateBalance(btc.getId(), userId);
        assertQuantity("1.75");
        assertThatThrownBy(() -> assetService.deleteAsset(holding.getId(), userId))
                .isInstanceOf(InvalidTransactionException.class);
        service.deleteTransaction(removal.getId(), userId);
        assertQuantity("2");
    }

    private Account account(String name) {
        return accounts.save(
                Account.builder()
                        .userId(userId)
                        .name(name)
                        .type(AccountType.INVESTMENT)
                        .currency("EUR")
                        .openingDate(OPENING)
                        .balance(BigDecimal.ZERO)
                        .openingBalance(BigDecimal.ZERO)
                        .build());
    }

    private TransactionResponse acquisition(String quantity, String amount) {
        // Seed the shape of an imported holdings-only movement, without an edit marker or price
        // snapshot.
        Transaction tx =
                transactions.save(
                        Transaction.builder()
                                .userId(userId)
                                .accountId(btc.getId())
                                .assetId(holding.getId())
                                .type(TransactionType.INCOME)
                                .date(OPENING)
                                .amount(new BigDecimal(amount))
                                .currency("EUR")
                                .investmentQuantity(new BigDecimal(quantity))
                                .investmentCash(false)
                                .accountAmount(BigDecimal.ZERO)
                                .accountCurrency("EUR")
                                .build());
        accountService.recalculateBalance(btc.getId(), userId);
        return service.getTransactionById(tx.getId(), userId);
    }

    private TransactionRequest movement(Operation operation, String quantity, String amount) {
        return TransactionRequest.builder()
                .accountId(btc.getId())
                .type(
                        operation == Operation.ADD || operation == Operation.SELL
                                ? TransactionType.INCOME
                                : TransactionType.EXPENSE)
                .date(OPENING)
                .currency("EUR")
                .amount(new BigDecimal(amount))
                .description("Investment movement")
                .investment(
                        InvestmentMovementRequest.builder()
                                .assetId(holding.getId())
                                .operation(operation)
                                .quantity(new BigDecimal(quantity))
                                .build())
                .build();
    }

    private TransactionResponse cashTransfer() {
        return service.createTransfer(
                userId,
                TransactionRequest.builder()
                        .accountId(btc.getId())
                        .toAccountId(destination.getId())
                        .type(TransactionType.TRANSFER)
                        .date(TRANSFER_DATE)
                        .currency("EUR")
                        .amount(new BigDecimal("377.65"))
                        .description("CREDIT POLYMARKET")
                        .build());
    }

    private TransferUpdateRequest transferEdit(TransactionResponse tx) {
        return TransferUpdateRequest.builder()
                .fromAccountId(btc.getId())
                .toAccountId(destination.getId())
                .amount(tx.getAmount())
                .currency(tx.getCurrency())
                .date(tx.getDate())
                .description(tx.getDescription())
                .build();
    }

    private void quoteAt(String price) {
        when(marketData.getHistoricalPrices(anyString(), any(), any(), anyString()))
                .thenReturn(
                        List.of(
                                HistoricalPrice.builder()
                                        .symbol("BTC-EUR")
                                        .date(TRANSFER_DATE)
                                        .currency("EUR")
                                        .close(new BigDecimal(price))
                                        .build()));
    }

    private void assertQuantity(String expected) {
        assertThat(assets.findById(holding.getId()).orElseThrow().getQuantity())
                .isEqualByComparingTo(expected);
    }

    private void assertCash(Account account, String expected) {
        assertThat(accounts.findById(account.getId()).orElseThrow().getBalance())
                .isEqualByComparingTo(expected);
    }
}
