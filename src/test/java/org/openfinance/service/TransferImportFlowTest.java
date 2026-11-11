package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openfinance.config.TestDatabaseConfig;
import org.openfinance.dto.AccountRequest;
import org.openfinance.dto.LoginRequest;
import org.openfinance.dto.LoginResponse;
import org.openfinance.dto.UserRegistrationRequest;
import org.openfinance.entity.AccountType;
import org.openfinance.entity.ImportSession;
import org.openfinance.entity.ImportSession.ImportStatus;
import org.openfinance.entity.Transaction;
import org.openfinance.repository.AccountRepository;
import org.openfinance.repository.AssetRepository;
import org.openfinance.repository.CurrencyRepository;
import org.openfinance.repository.TransactionRepository;
import org.openfinance.repository.UserRepository;
import org.openfinance.security.EncryptionContext;
import org.openfinance.security.EncryptionKeyCache;
import org.openfinance.util.DatabaseCleanupService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@Import(TestDatabaseConfig.class)
@ActiveProfiles("test")
class TransferImportFlowTest {
    @MockBean private ExchangeRateService exchangeRateService;
    @MockBean private OperationHistoryService operationHistoryService;
    @Autowired private UserService userService;
    @Autowired private AuthService authService;
    @Autowired private EncryptionKeyCache encryptionKeyCache;
    @Autowired private FileStorageService files;
    @Autowired private ImportService imports;
    @Autowired private AccountService accountService;
    @Autowired private TransactionService transactions;
    @Autowired private DashboardService dashboard;
    @Autowired private AccountRepository accounts;
    @Autowired private TransactionRepository ledger;
    @Autowired private AssetRepository assets;
    @Autowired private UserRepository users;
    @Autowired private CurrencyRepository currencies;
    @Autowired private org.openfinance.repository.NetWorthRepository snapshots;
    @Autowired private DatabaseCleanupService cleanup;
    private Long userId;

    @BeforeEach
    void setUp() {
        cleanup.execute();
        for (String currency : List.of("EUR", "USD")) {
            currencies.save(
                    org.openfinance.entity.Currency.builder()
                            .code(currency)
                            .name(currency)
                            .symbol(currency)
                            .build());
        }
        lenient()
                .when(exchangeRateService.getExchangeRate(anyString(), anyString(), any()))
                .thenReturn(BigDecimal.ONE);
        lenient()
                .when(exchangeRateService.convert(any(), anyString(), anyString()))
                .thenAnswer(call -> call.getArgument(0));
        lenient()
                .when(exchangeRateService.convert(any(), anyString(), anyString(), any()))
                .thenAnswer(call -> call.getArgument(0));
        userService.registerUser(
                UserRegistrationRequest.builder()
                        .username("transfer_importer")
                        .email("transfers@example.com")
                        .password("Password123!")
                        .masterPassword("Master123!")
                        .skipSeeding(true)
                        .build());
        userId = users.findByUsername("transfer_importer").orElseThrow().getId();
        userService.updateBaseCurrency(userId, "EUR");
        LoginResponse login =
                authService.login(
                        LoginRequest.builder()
                                .username("transfer_importer")
                                .password("Password123!")
                                .masterPassword("Master123!")
                                .build());
        EncryptionContext.setKey(
                encryptionKeyCache.getKeyBySessionToken(login.getEncryptionKey()).orElseThrow());
    }

    @AfterEach
    void clearKey() {
        EncryptionContext.clear();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void qifExternalFundingPreservesBothLegsInEitherOrder(boolean cashFirst) throws Exception {
        String cash =
                qifAccount(
                        "Checking",
                        "Bank",
                        """
                D09/28/2026
                T-100
                PExternal purchase
                L[Brokerage]
                ^
                """);
        String holding =
                qifAccount(
                        "Brokerage",
                        "Invst",
                        """
                D09/28/2026
                NBuyX
                YFund
                Q2
                I49.99
                T99.98
                L[Checking]
                $100
                ^
                """);
        String file = cashFirst ? cash + holding : holding + cash;
        assertThat(importFile("funding.qif", file).getErrorCount()).isZero();
        List<Transaction> rows = ledger.findByUserId(userId);
        assertThat(rows).hasSize(2);
        assertPair(rows);
        Transaction shares =
                rows.stream()
                        .filter(tx -> tx.getInvestmentQuantity() != null)
                        .findFirst()
                        .orElseThrow();
        assertThat(shares.getInvestmentQuantity()).isEqualByComparingTo("2");
        assertThat(shares.getAmount()).isEqualByComparingTo("99.98");
        assertThat(shares.getBalanceAmount()).isZero();
        assertThat(accounts.findById(shares.getToAccountId()).orElseThrow().getBalance())
                .isEqualByComparingTo("-100");
        assertThat(assets.findById(shares.getAssetId()).orElseThrow().getQuantity())
                .isEqualByComparingTo("2");
        assertCashFlow("0", "0");
        assertThat(importFile("funding-again.qif", file).getErrorCount()).isZero();
        assertThat(ledger.findByUserId(userId)).hasSize(2);
        transactions.deleteTransaction(shares.getId(), userId);
        assertThat(accounts.findById(shares.getToAccountId()).orElseThrow().getBalance()).isZero();
        assertThat(assets.findById(shares.getAssetId()).orElseThrow().getQuantity()).isZero();
    }

    @Test
    void invalidTransferDoesNotRollBackOtherImportedRows() throws Exception {
        String file =
                qifAccount("Checking", "Bank", "D09/28/2026\nT200\nPSalary\n^\n")
                        + qifAccount(
                                "Brokerage",
                                "Invst",
                                "D09/28/2026\nNBuyX\nYFund\nQ2\nI50\nT100\nL[Brokerage]\n^\n");
        ImportSession result = importFile("invalid-transfer.qif", file);
        assertThat(result.getStatus()).isEqualTo(ImportStatus.COMPLETED);
        assertThat(result.getErrorCount()).isEqualTo(1);
        assertThat(ledger.findByUserId(userId))
                .singleElement()
                .satisfies(
                        transaction -> {
                            assertThat(transaction.getAmount()).isEqualByComparingTo("200");
                            assertThat(transaction.getTransferId()).isNull();
                        });
    }

    @Test
    void qifSellXCreatesOnlyTheExternalCashCredit() throws Exception {
        String file =
                qifAccount(
                        "Brokerage",
                        "Invst",
                        """
                D09/01/2026
                NShrsIn
                YFund
                Q2
                I50
                ^
                D09/28/2026
                NSellX
                YFund
                Q2
                I60
                T120
                L[Checking]
                $120
                ^
                """);
        assertThat(importFile("sale.qif", file).getErrorCount()).isZero();
        assertPair(
                ledger.findByUserId(userId).stream()
                        .filter(tx -> tx.getTransferId() != null)
                        .toList());
        assertThat(accounts.findByUserId(userId))
                .filteredOn(account -> "Checking".equals(account.getName()))
                .singleElement()
                .satisfies(account -> assertThat(account.getBalance()).isEqualByComparingTo("120"));
        assertThat(assets.findByUserId(userId))
                .singleElement()
                .satisfies(asset -> assertThat(asset.getQuantity()).isZero());
        assertCashFlow("0", "0");
    }

    @Test
    void qifShareTransferPreservesDifferentValuationsWithoutCash() throws Exception {
        String file =
                qifAccount(
                                "Old",
                                "Invst",
                                "D09/01/2026\nNShrsIn\nYFund\nQ2\nI50\n^\nD09/28/2026\nNShrsOut\nYFund\nQ2\nI50\nL[New]\n^\n")
                        + qifAccount(
                                "New",
                                "Invst",
                                "D09/29/2026\nNShrsIn\nYFund\nQ2\nI51\nL[Old]\n^\n");
        assertThat(importFile("shares.qif", file).getErrorCount()).isZero();
        List<Transaction> rows =
                ledger.findByUserId(userId).stream()
                        .filter(tx -> tx.getTransferId() != null)
                        .toList();
        assertPair(rows);
        assertThat(rows)
                .extracting(Transaction::getAmount)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactlyInAnyOrder(new BigDecimal("100"), new BigDecimal("102"));
        assertThat(rows).allSatisfy(tx -> assertThat(tx.getBalanceAmount()).isZero());
        assertCashFlow("0", "0");
        assertThat(importFile("shares-again.qif", file).getErrorCount()).isZero();
        assertThat(ledger.findByUserId(userId)).hasSize(3);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ofx", "qfx"})
    void ofxCashTransfersKeepReferencesAndExternalPayments(String extension) throws Exception {
        String a =
                bankStatement(
                        "A",
                        "EUR",
                        bankRow("DEBIT", "-25", "shop", "", "")
                                + bankRow("CREDIT", "1000", "salary", "", "")
                                + bankRow("XFER", "-100", "same-fitid", "B", "out")
                                + bankRow("XFER", "40", "back-a", "B", "back")
                                + bankRow("XFER", "-60", "external", "NOT-OWNED", "")
                                + bankRow("XFER", "-10", "unknown", "", ""));
        String b =
                bankStatement(
                        "B",
                        "EUR",
                        bankRow("XFER", "100", "same-fitid", "A", "out")
                                + bankRow("XFER", "-40", "back-b", "A", "back"));
        String file = "<OFX>" + a + b + "</OFX>";
        if (extension.equals("ofx")) file = sgml(file);
        assertThat(importFile("cash." + extension, file).getErrorCount()).isZero();
        List<Transaction> rows = ledger.findByUserId(userId);
        assertThat(rows).hasSize(8);
        assertThat(accounts.findByUserId(userId)).hasSize(2);
        assertThat(rows.stream().filter(tx -> tx.getTransferId() != null)).hasSize(4);
        assertThat(rows.stream().filter(tx -> "same-fitid".equals(tx.getExternalReference())))
                .hasSize(2);
        assertCashFlow("1000", "95");
        assertThat(importFile("cash-again." + extension, file).getErrorCount()).isZero();
        assertThat(ledger.findByUserId(userId)).hasSize(8);
    }

    @Test
    void creditCardPaymentIsLinkedToTheBankDebit() throws Exception {
        String card =
                bankStatement("Card", "EUR", bankRow("XFER", "100", "card-credit", "A", "payment"))
                        .replace("BANKMSGSRSV1", "CREDITCARDMSGSRSV1")
                        .replace("STMTTRNRS", "CCSTMTTRNRS")
                        .replace("STMTRS", "CCSTMTRS")
                        .replace("BANKACCTFROM", "CCACCTFROM");
        String file =
                "<OFX>"
                        + bankStatement(
                                "A",
                                "EUR",
                                bankRow("XFER", "-100", "bank-debit", "Card", "payment")
                                        .replace("BANKACCTTO", "CCACCTTO"))
                        + card
                        + "</OFX>";
        assertThat(importFile("payment.qfx", file).getErrorCount()).isZero();
        assertPair(ledger.findByUserId(userId));
        assertThat(accounts.findByUserId(userId))
                .filteredOn(account -> "Card".equals(account.getName()))
                .singleElement()
                .satisfies(
                        account ->
                                assertThat(account.getType()).isEqualTo(AccountType.CREDIT_CARD));
        assertCashFlow("0", "0");
    }

    @Test
    void sharedServerReferenceLinksActualForeignCurrencyLegs() throws Exception {
        String file =
                "<OFX>"
                        + bankStatement("EU", "EUR", bankRow("XFER", "-100", "euro", "", "shared"))
                        + bankStatement(
                                "US",
                                "USD",
                                bankRow("XFER", "112", "dollar", "", "shared")
                                        .replace("20260928", "20260930"))
                        + "</OFX>";
        assertThat(importFile("fx.qfx", file).getErrorCount()).isZero();
        List<Transaction> rows = ledger.findByUserId(userId);
        assertPair(rows);
        assertThat(rows)
                .filteredOn(tx -> "USD".equals(tx.getCurrency()))
                .singleElement()
                .satisfies(tx -> assertThat(tx.getAmount()).isEqualByComparingTo("112"));
        assertCashFlow("0", "0");
    }

    @Test
    void laterCounterStatementAttachesFitidWithoutCreatingAnotherTransfer() throws Exception {
        accountService.createAccount(
                userId,
                AccountRequest.builder()
                        .name("Savings")
                        .accountNumber("B")
                        .type(AccountType.SAVINGS)
                        .currency("EUR")
                        .initialBalance(BigDecimal.ZERO)
                        .openingDate(LocalDate.of(2026, 1, 1))
                        .build());
        String outgoing =
                "<OFX>"
                        + bankStatement("A", "EUR", bankRow("XFER", "-100", "debit", "B", ""))
                        + "</OFX>";
        String incoming =
                "<OFX>"
                        + bankStatement("B", "EUR", bankRow("XFER", "100", "credit", "A", ""))
                        + "</OFX>";
        assertThat(importFile("out.ofx", outgoing).getErrorCount()).isZero();
        assertThat(importFile("in.ofx", incoming).getErrorCount()).isZero();
        List<Transaction> rows = ledger.findByUserId(userId);
        assertPair(rows);
        assertThat(rows)
                .extracting(Transaction::getExternalReference)
                .containsExactlyInAnyOrder("debit", "credit");
        assertThat(importFile("in-again.ofx", incoming).getErrorCount()).isZero();
        assertThat(importFile("out-again.ofx", outgoing).getErrorCount()).isZero();
        assertThat(ledger.findByUserId(userId)).hasSize(2);
    }

    @Test
    void laterForeignCounterStatementReplacesInferredAmountUsingServerReference() throws Exception {
        accountService.createAccount(
                userId,
                AccountRequest.builder()
                        .name("Dollars")
                        .accountNumber("US")
                        .type(AccountType.SAVINGS)
                        .currency("USD")
                        .initialBalance(BigDecimal.ZERO)
                        .openingDate(LocalDate.of(2026, 1, 1))
                        .build());
        String outgoing =
                "<OFX>"
                        + bankStatement(
                                "EU", "EUR", bankRow("XFER", "-100", "debit", "US", "fx-42"))
                        + "</OFX>";
        String incoming =
                "<OFX>"
                        + bankStatement(
                                "US",
                                "USD",
                                bankRow("XFER", "112", "credit", "EU", "fx-42")
                                        .replace("20260928", "20261002"))
                        + "</OFX>";
        assertThat(importFile("eu.ofx", outgoing).getErrorCount()).isZero();
        org.openfinance.entity.NetWorth inferred =
                snapshots.save(
                        org.openfinance.entity.NetWorth.builder()
                                .userId(userId)
                                .snapshotDate(LocalDate.of(2026, 9, 30))
                                .currency("EUR")
                                .totalAssets(BigDecimal.ZERO)
                                .totalLiabilities(BigDecimal.ZERO)
                                .netWorth(BigDecimal.ZERO)
                                .build());
        assertThat(importFile("us.qfx", incoming).getErrorCount()).isZero();
        assertThat(snapshots.findById(inferred.getId())).isEmpty();
        List<Transaction> rows = ledger.findByUserId(userId);
        assertPair(rows);
        assertThat(rows)
                .filteredOn(tx -> "credit".equals(tx.getExternalReference()))
                .singleElement()
                .satisfies(
                        tx -> {
                            assertThat(tx.getAmount()).isEqualByComparingTo("112");
                            assertThat(tx.getDate()).isEqualTo(LocalDate.of(2026, 10, 2));
                            assertThat(
                                            accounts.findById(tx.getAccountId())
                                                    .orElseThrow()
                                                    .getBalance())
                                    .isEqualByComparingTo("112");
                        });
        assertThat(importFile("eu-again.ofx", outgoing).getErrorCount()).isZero();
        assertThat(importFile("us-again.qfx", incoming).getErrorCount()).isZero();
        assertThat(ledger.findByUserId(userId)).hasSize(2);
        assertCashFlow("0", "0");
    }

    @Test
    void ofxSecurityTransfersPreserveHoldingsWithoutInventingCash() throws Exception {
        String file =
                "<OFX>"
                        + investmentStatement("Old", "External", "IN", "opening", "50")
                                .replace("20260928", "20260901")
                        + investmentStatement("Old", "New", "OUT", "out", "50")
                        + investmentStatement("New", "Old", "IN", "in", "51")
                        + "</OFX>";
        assertThat(importFile("holdings.qfx", file).getErrorCount()).isZero();
        List<Transaction> rows =
                ledger.findByUserId(userId).stream()
                        .filter(tx -> tx.getTransferId() != null)
                        .toList();
        assertPair(rows);
        assertThat(rows)
                .allSatisfy(
                        tx -> {
                            assertThat(tx.getBalanceAmount()).isZero();
                            assertThat(tx.getAssetId()).isNotNull();
                        });
        assertThat(assets.findByUserId(userId))
                .extracting(asset -> asset.getQuantity())
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactlyInAnyOrder(BigDecimal.ZERO, new BigDecimal("2"));
        assertCashFlow("0", "0");
        assertThat(importFile("holdings-again.qfx", file).getErrorCount()).isZero();
        assertThat(ledger.findByUserId(userId)).hasSize(3);
    }

    private void assertPair(List<Transaction> rows) {
        assertThat(rows).hasSize(2);
        assertThat(rows.getFirst().getTransferId())
                .isNotBlank()
                .isEqualTo(rows.getLast().getTransferId());
        assertThat(rows.getFirst().getToAccountId()).isEqualTo(rows.getLast().getAccountId());
        assertThat(rows.getLast().getToAccountId()).isEqualTo(rows.getFirst().getAccountId());
        assertThat(rows).allSatisfy(tx -> assertThat(tx.getCategoryId()).isNull());
    }

    private void assertCashFlow(String income, String expenses) {
        Map<String, BigDecimal> flow =
                dashboard.getCashFlow(userId, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));
        assertThat(flow.get("income")).isEqualByComparingTo(income);
        assertThat(flow.get("expenses")).isEqualByComparingTo(expenses);
    }

    private ImportSession importFile(String name, String contents) throws Exception {
        String upload =
                files.storeFile(
                        new MockMultipartFile(
                                "file",
                                name,
                                "application/octet-stream",
                                contents.getBytes(StandardCharsets.UTF_8)),
                        userId);
        ImportSession session = imports.startImport(upload, userId, null, name);
        for (int attempt = 0; attempt < 100; attempt++) {
            ImportStatus status = imports.getSession(session.getId(), userId).getStatus();
            if (status == ImportStatus.PARSED || status == ImportStatus.REVIEWING) {
                return imports.confirmImport(session.getId(), userId, null, Map.of(), true);
            }
            assertThat(status).isNotEqualTo(ImportStatus.FAILED);
            Thread.sleep(100);
        }
        throw new AssertionError("Import parsing timed out");
    }

    private String qifAccount(String name, String type, String rows) {
        return "!Account\nN" + name + "\nT" + type + "\n^\n!Type:" + type + "\n" + rows;
    }

    private String bankStatement(String account, String currency, String rows) {
        return "<BANKMSGSRSV1><STMTTRNRS><STMTRS><CURDEF>"
                + currency
                + "</CURDEF>"
                + "<BANKACCTFROM><BANKID>123</BANKID><ACCTID>"
                + account
                + "</ACCTID><ACCTTYPE>CHECKING</ACCTTYPE></BANKACCTFROM>"
                + "<BANKTRANLIST>"
                + rows
                + "</BANKTRANLIST></STMTRS></STMTTRNRS></BANKMSGSRSV1>";
    }

    private String bankRow(
            String type, String amount, String fitid, String other, String reference) {
        return "<STMTTRN><TRNTYPE>"
                + type
                + "</TRNTYPE><DTPOSTED>20260928</DTPOSTED><TRNAMT>"
                + amount
                + "</TRNAMT><FITID>"
                + fitid
                + "</FITID><NAME>"
                + fitid
                + "</NAME>"
                + (other.isEmpty()
                        ? ""
                        : "<BANKACCTTO><BANKID>123</BANKID><ACCTID>"
                                + other
                                + "</ACCTID><ACCTTYPE>CHECKING</ACCTTYPE></BANKACCTTO>")
                + (reference.isEmpty() ? "" : "<SRVRTID>" + reference + "</SRVRTID>")
                + "</STMTTRN>";
    }

    private String investmentStatement(
            String account, String other, String direction, String fitid, String price) {
        return "<INVSTMTMSGSRSV1><INVSTMTTRNRS><INVSTMTRS><CURDEF>EUR</CURDEF>"
                + "<INVACCTFROM><BROKERID>broker</BROKERID><ACCTID>"
                + account
                + "</ACCTID></INVACCTFROM>"
                + "<INVTRANLIST><TRANSFER><INVTRAN><FITID>"
                + fitid
                + "</FITID><DTTRADE>20260928</DTTRADE></INVTRAN>"
                + "<SECID><UNIQUEID>Fund</UNIQUEID><UNIQUEIDTYPE>ISIN</UNIQUEIDTYPE></SECID><UNITS>2</UNITS><UNITPRICE>"
                + price
                + "</UNITPRICE><TFERACTION>"
                + direction
                + "</TFERACTION><INVACCTFROM><BROKERID>broker</BROKERID><ACCTID>"
                + other
                + "</ACCTID></INVACCTFROM></TRANSFER></INVTRANLIST></INVSTMTRS></INVSTMTTRNRS></INVSTMTMSGSRSV1>";
    }

    private String sgml(String xml) {
        return "OFXHEADER:100\nDATA:OFXSGML\nVERSION:102\n\n"
                + xml.replaceAll("(<([A-Z]+)>[^<]+)</\\2>", "$1\n");
    }
}
