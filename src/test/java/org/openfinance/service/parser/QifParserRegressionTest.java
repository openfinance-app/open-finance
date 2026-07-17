package org.openfinance.service.parser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openfinance.dto.ImportedTransaction;

/**
 * End-to-end regression test that parses the real anonymized Skrooge QIF export shipped under
 * {@code docs/sample/qif/my_export_anonymized.qif} and asserts that:
 *
 * <ul>
 *   <li>the total number of parsed transactions matches the expected count, and
 *   <li>each imported account reconciles to the expected home-currency balance (sum of the signed
 *       amounts produced by the parser for that account's block).
 * </ul>
 *
 * <p>QIF has no concept of currency, so all values are collapsed to the home currency (EUR) on
 * import: foreign-unit lines ({@code Y}/{@code Q}/{@code I}) are valued as {@code Q × I}. Opening
 * balance records (leading no-payee records written by Skrooge) are kept and counted as regular
 * transactions, consistent with how home-currency openings are handled.
 *
 * <p>Expected balances were verified against Skrooge's own home-currency balance ({@code
 * operationbalance.f_balance}) for each account. 32 of 34 accounts reconcile to the cent at the
 * parser level; the remaining two (Account_029 and Account_031) carry a known residual because they
 * exchange CFA funds with each other: both transfer legs are exported with unsigned {@code Q}
 * values so both stay positive, inflating the parser-level sum for each account by the combined Q×I
 * of the wrongly-signed legs. Account_006 and Account_032 are not affected — their CFA↔CFA
 * transfers net correctly at parser level. See docs/wiki/import-export.md § "Multi-currency
 * accounts & QIF limitations" for the full explanation.
 */
@DisplayName("QIF parser regression (real sample file)")
class QifParserRegressionTest {

    private static final Path SAMPLE_FILE =
            Path.of("docs", "sample", "qif", "my_export_anonymized.qif");

    /**
     * Total transactions produced by the parser from the sample export. Includes opening-balance
     * records (counted as regular transactions after the Cause-2 fix) and excludes nothing except
     * records whose amounts genuinely cannot be parsed (e.g. {@code Q0}/{@code Inan} investment
     * account markers, which are kept in the list but carry validation errors).
     */
    private static final int EXPECTED_TRANSACTION_COUNT = 2861;

    /**
     * Tight tolerance for the 30 clean EUR accounts — they reconcile to the cent against Skrooge.
     */
    private static final BigDecimal EUR_TOLERANCE = new BigDecimal("0.01");

    /**
     * Wider tolerance for the two CFA accounts (029, 031) whose parser-level balance is affected by
     * the QIF unsigned-Q transfer-direction limitation. The residual can be hundreds of EUR.
     */
    private static final BigDecimal CFA_TOLERANCE = new BigDecimal("1.00");

    private QifParser parser;

    @BeforeEach
    void setUp() {
        parser = new QifParser();
    }

    @Test
    @DisplayName("Should produce expected transaction count and per-account home-currency balance")
    void shouldReconcileAccountsAndTransactions() throws IOException {
        assumeTrue(
                Files.exists(SAMPLE_FILE),
                "Sample QIF export not found at " + SAMPLE_FILE.toAbsolutePath());

        List<ImportedTransaction> transactions;
        try (InputStream in = Files.newInputStream(SAMPLE_FILE)) {
            transactions = parser.parseFile(in, "my_export_anonymized.qif");
        }

        // 1. Total transaction count (including error rows, including opening-balance records).
        assertThat(transactions).hasSize(EXPECTED_TRANSACTION_COUNT);

        // 2. Build per-account balance by summing the signed amounts the parser produces.
        //    Error rows (null amount or hasErrors) are excluded, mirroring ImportService behaviour.
        Map<String, BigDecimal> balanceByName =
                transactions.stream()
                        .filter(tx -> !tx.hasErrors() && tx.getAmount() != null)
                        .filter(tx -> tx.getAccountName() != null && !tx.getAccountName().isBlank())
                        .collect(
                                Collectors.groupingBy(
                                        ImportedTransaction::getAccountName,
                                        Collectors.reducing(
                                                BigDecimal.ZERO,
                                                ImportedTransaction::getAmount,
                                                BigDecimal::add)));

        // 3. Assert expected account names are all present.
        assertThat(balanceByName.keySet())
                .as("imported account names")
                .containsExactlyInAnyOrderElementsOf(expectedAccounts().keySet());

        // 4. Assert per-account balance, using appropriate tolerance per account group.
        Map<String, ExpectedAccount> expected = expectedAccounts();
        expected.forEach(
                (name, exp) -> {
                    BigDecimal actual =
                            balanceByName
                                    .getOrDefault(name, BigDecimal.ZERO)
                                    .setScale(2, RoundingMode.HALF_UP);
                    assertThat(actual)
                            .as("parser-level home-currency balance of %s", name)
                            .isCloseTo(exp.balance(), within(exp.tolerance()));
                });
    }

    /**
     * Expected home-currency (EUR) balance per account.
     *
     * <p>Values are the signed sums that the parser produces for each account's QIF block: {@code
     * sum(tx.getAmount())} for all non-error transactions whose {@code accountName} matches. For
     * the 32 accounts without same-currency transfer ambiguity this equals Skrooge's {@code
     * operationbalance.f_balance} to the cent. For Account_029 and Account_031, the parser produces
     * a different value than Skrooge because same-currency (CFA↔CFA) transfer legs between those
     * two accounts cannot have their direction recovered from unsigned QIF {@code Q} fields; both
     * legs stay positive, so one account receives an extra positive amount that should be negative.
     * This is the documented QIF limitation (see docs/wiki/import-export.md).
     *
     * <p>Note: the parser-level balance differs from the ImportService account balance for the
     * affected accounts because ImportService deduplicates transfer pairs (processes only the first
     * leg it encounters), which changes which amounts are actually persisted. The regression test
     * targets the parser level; see QifImportFlowE2ETest for the full-pipeline assertion.
     */
    private Map<String, ExpectedAccount> expectedAccounts() {
        Map<String, ExpectedAccount> e = new LinkedHashMap<>();
        // ── 32 accounts that reconcile exactly to Skrooge's f_balance ────────────────────────
        e.put("Account_001", eur("6322.19")); // Checking EUR
        e.put("Account_002", eur("0.47")); // USDT investment (Q×I → EUR)
        e.put("Account_003", eur("0.00")); // Retirement savings
        e.put("Account_004", eur("16769.42")); // TCALAVI share (Q×I → EUR)
        e.put("Account_005", eur("744.95")); // EUR investment
        e.put("Account_006", eur("8.81")); // CFA wallet (opening +58.21; both transfer legs
        // net correctly at parser level — only ImportService dedup shifts the balance)
        e.put("Account_007", eur("130.00")); // EUR investment
        e.put("Account_008", eur("116.62")); // EUR cash
        e.put("Account_009", eur("321.26")); // EUR investment
        e.put("Account_010", eur("-0.40")); // DOGE (Q×I → EUR, historical-cost residual)
        e.put("Account_011", eur("0.00")); // BCH (Q×I → EUR, buys cancel out)
        e.put("Account_012", eur("0.00")); // ADA (Q×I → EUR, buys cancel out)
        e.put("Account_013", eur("0.00")); // SOL (Q×I → EUR)
        e.put("Account_014", eur("0.00")); // EUR savings (Hello+)
        e.put("Account_015", eur("23445.16")); // EUR savings (Livret A)
        e.put("Account_016", eur("9000.00")); // EUR savings (LDDS)
        e.put("Account_017", eur("1057.30")); // EUR investment
        e.put("Account_018", eur("0.00")); // EUR investment
        e.put("Account_019", eur("55.50")); // EUR credit card
        e.put("Account_020", eur("382.11")); // EUR investment
        e.put("Account_021", eur("2743.50")); // EUR investment
        e.put("Account_022", eur("3500.00")); // EUR formation account
        e.put("Account_023", eur("-136250.00")); // EUR loan
        e.put("Account_024", eur("149725.00")); // B504 share (Q×I → EUR)
        e.put("Account_025", eur("1655.38")); // EUR investment (Bourso)
        e.put("Account_026", eur("6.73")); // EUR checking (Bourso)
        e.put("Account_027", eur("12564.25")); // EUR savings (Bourso PEA)
        e.put("Account_028", eur("8.73")); // BTC (Q×I → EUR)
        e.put("Account_030", eur("2146.75")); // EUR investment
        e.put("Account_032", eur("0.76")); // CFA CCard (opening zero; no CFA↔CFA transfers)
        e.put("Account_033", eur("1072.91")); // EUR investment (USD leg → EUR via Q×I)
        e.put("Account_034", eur("8222.45")); // EUR payroll savings
        // ── 2 accounts affected by the QIF unsigned-Q transfer-direction limitation ──────────
        // Account_029 and Account_031 exchange CFA funds with each other. Both transfer legs
        // are unsigned (Q > 0), so both stay positive at parser level; one should be negative.
        // The net residual is the combined Q×I of the wrongly-signed legs (~304 EUR for 029,
        // ~3001 EUR for 031). The ImportService further diverges via transfer deduplication
        // (processes only the first leg). Net worth remains correct (legs cancel across accounts).
        e.put("Account_029", cfa("15560.26")); // Skrooge f_balance: 15255.37
        e.put("Account_031", cfa("3039.24")); // Skrooge f_balance:    37.72
        return e;
    }

    private static ExpectedAccount eur(String balance) {
        return new ExpectedAccount(new BigDecimal(balance), EUR_TOLERANCE);
    }

    private static ExpectedAccount cfa(String balance) {
        return new ExpectedAccount(new BigDecimal(balance), CFA_TOLERANCE);
    }

    private record ExpectedAccount(BigDecimal balance, BigDecimal tolerance) {}
}
