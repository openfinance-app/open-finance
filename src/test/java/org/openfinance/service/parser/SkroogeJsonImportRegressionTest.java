package org.openfinance.service.parser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openfinance.dto.ImportedTransaction;
import org.openfinance.dto.SkroogeImportMetadata;
import org.openfinance.dto.SkroogeImportParseResult;

/**
 * End-to-end regression test that parses the real anonymized Skrooge export shipped under {@code
 * docs/sample/json/export_anonymized.json} and asserts that:
 *
 * <ul>
 *   <li>the total number of imported (non-synthetic) transactions matches Skrooge, and
 *   <li>each imported account reconciles to the expected native-currency balance (opening balance +
 *       the signed sum of its transactions), including the intentional native currency (XOF vs
 *       EUR).
 * </ul>
 *
 * <p>The expected balances were verified against Skrooge's own {@code v_account_amount} view: every
 * plain-cash and foreign-currency account reconciles exactly, while the five share/crypto accounts
 * (Account_010/011/012/013/028) carry the intentional historical-cost residual described in the
 * import notes (their balance is a running sum of realized cash flows rather than mark-to-market).
 */
@DisplayName("Skrooge JSON import regression (real sample file)")
class SkroogeJsonImportRegressionTest {

    private static final Path SAMPLE_FILE =
            Path.of("docs", "sample", "json", "export_anonymized.json");

    /** Total number of non-synthetic operations imported from the sample export. */
    private static final int EXPECTED_TRANSACTION_COUNT = 2827;

    /** Tolerance for native-currency balance comparisons. */
    private static final BigDecimal BALANCE_TOLERANCE = new BigDecimal("0.01");

    private SkroogeJsonParser parser;

    @BeforeEach
    void setUp() {
        parser = new SkroogeJsonParser(new ObjectMapper());
    }

    @Test
    @DisplayName("Should import expected transaction count and per-account native balances")
    void shouldReconcileAccountsAndTransactions() throws IOException {
        assumeTrue(
                Files.exists(SAMPLE_FILE),
                "Sample Skrooge export not found at " + SAMPLE_FILE.toAbsolutePath());

        SkroogeImportParseResult result;
        try (InputStream in = Files.newInputStream(SAMPLE_FILE)) {
            result = parser.parseFile(in, "export_anonymized.json");
        }

        // 1. Transaction count matches Skrooge (34 synthetic opening-balance rows are excluded).
        assertThat(result.getTransactions()).hasSize(EXPECTED_TRANSACTION_COUNT);

        // 2. Build each account's native currency and computed balance:
        //    balance = openingBalance + signed sum of the account's transactions.
        Map<Long, SkroogeImportMetadata.SkroogeAccount> accountsBySource = new HashMap<>();
        Map<String, BigDecimal> balanceByName = new HashMap<>();
        Map<String, String> currencyByName = new HashMap<>();
        for (SkroogeImportMetadata.SkroogeAccount account :
                result.getSkroogeMetadata().getAccounts()) {
            accountsBySource.put(account.getSourceId(), account);
            balanceByName.put(
                    account.getName(),
                    account.getOpeningBalance() != null
                            ? account.getOpeningBalance()
                            : BigDecimal.ZERO);
            currencyByName.put(account.getName(), account.getCurrency());
        }

        for (ImportedTransaction tx : result.getTransactions()) {
            SkroogeImportMetadata.SkroogeAccount account =
                    accountsBySource.get(tx.getSourceAccountId());
            if (account == null || tx.getAmount() == null) {
                continue;
            }
            balanceByName.merge(account.getName(), tx.getAmount(), (a, b) -> a.add(b));
        }

        // 3. Assert the native currency and reconciled balance of every account.
        Map<String, ExpectedAccount> expected = expectedAccounts();
        assertThat(balanceByName.keySet())
                .as("imported account names")
                .containsExactlyInAnyOrderElementsOf(expected.keySet());

        for (Map.Entry<String, ExpectedAccount> entry : expected.entrySet()) {
            String name = entry.getKey();
            ExpectedAccount exp = entry.getValue();

            assertThat(currencyByName.get(name))
                    .as("native currency of %s", name)
                    .isEqualTo(exp.currency());

            assertThat(balanceByName.get(name).setScale(2, RoundingMode.HALF_UP))
                    .as("native balance of %s", name)
                    .isCloseTo(exp.balance(), within(BALANCE_TOLERANCE));
        }
    }

    /**
     * Expected native-currency balance and currency per account, verified against Skrooge's {@code
     * v_account_amount} view (foreign-currency accounts reconcile exactly at Skrooge's peg; the
     * five crypto accounts carry the documented historical-cost residual).
     */
    private Map<String, ExpectedAccount> expectedAccounts() {
        Map<String, ExpectedAccount> expected = new LinkedHashMap<>();
        expected.put("Account_001", eur("6322.19"));
        expected.put("Account_002", eur("0.47"));
        expected.put("Account_003", eur("0.00"));
        expected.put("Account_004", xof("11000012.69"));
        expected.put("Account_005", eur("744.95"));
        expected.put("Account_006", xof("5780.43"));
        expected.put("Account_007", eur("130.00"));
        expected.put("Account_008", eur("116.62"));
        expected.put("Account_009", eur("321.26"));
        // Crypto (Dogecoin) — intentional historical-cost residual.
        expected.put("Account_010", eur("60.78"));
        // Crypto (Bitcoin Cash) — intentional historical-cost residual.
        expected.put("Account_011", eur("-28.88"));
        // Crypto (Cardano) — intentional historical-cost residual.
        expected.put("Account_012", eur("1.03"));
        // Crypto (Solana) — intentional historical-cost residual.
        expected.put("Account_013", eur("58.04"));
        expected.put("Account_014", eur("0.00"));
        expected.put("Account_015", eur("23445.16"));
        expected.put("Account_016", eur("9000.00"));
        expected.put("Account_017", eur("1057.30"));
        expected.put("Account_018", eur("0.00"));
        expected.put("Account_019", eur("55.50"));
        expected.put("Account_020", eur("382.11"));
        expected.put("Account_021", eur("2743.50"));
        expected.put("Account_022", eur("3500.00"));
        expected.put("Account_023", eur("-136250.00"));
        expected.put("Account_024", eur("149725.00"));
        expected.put("Account_025", eur("1655.38"));
        expected.put("Account_026", eur("6.73"));
        expected.put("Account_027", eur("12564.25"));
        // Crypto (Bitcoin) — intentional historical-cost residual.
        expected.put("Account_028", eur("321.90"));
        expected.put("Account_029", xof("10006858.91"));
        expected.put("Account_030", eur("2146.75"));
        expected.put("Account_031", xof("24741.04"));
        expected.put("Account_032", xof("500.00"));
        expected.put("Account_033", eur("1072.91"));
        expected.put("Account_034", eur("8222.45"));
        return expected;
    }

    private static ExpectedAccount eur(String balance) {
        return new ExpectedAccount(new BigDecimal(balance), "EUR");
    }

    private static ExpectedAccount xof(String balance) {
        return new ExpectedAccount(new BigDecimal(balance), "XOF");
    }

    private record ExpectedAccount(BigDecimal balance, String currency) {}
}
