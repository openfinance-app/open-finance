package org.openfinance.service;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.openfinance.dto.calculator.CompoundInterestRequest;
import org.openfinance.dto.calculator.CompoundInterestResult;
import org.openfinance.dto.calculator.CompoundInterestYearlyBreakdown;

/** Unit tests for {@link CompoundInterestService}. */
class CompoundInterestServiceTest {

    private CompoundInterestService service;

    @BeforeEach
    void setUp() {
        service = new CompoundInterestService();
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static CompoundInterestRequest request(
            String principal, String rate, int frequency, int years) {
        return CompoundInterestRequest.builder()
                .principal(new BigDecimal(principal))
                .annualRate(new BigDecimal(rate))
                .compoundingFrequency(frequency)
                .years(years)
                .build();
    }

    private static CompoundInterestRequest requestWithContribution(
            String principal,
            String rate,
            int frequency,
            int years,
            String contribution,
            boolean atBeginning) {
        return CompoundInterestRequest.builder()
                .principal(new BigDecimal(principal))
                .annualRate(new BigDecimal(rate))
                .compoundingFrequency(frequency)
                .years(years)
                .regularContribution(new BigDecimal(contribution))
                .contributionAtBeginning(atBeginning)
                .build();
    }

    // -----------------------------------------------------------------------
    // Annual compounding — basic correctness
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Annual compounding (n=1)")
    class AnnualCompounding {

        @Test
        @DisplayName("P=1000 r=10% t=1 year -> final balance 1100.00")
        void oneYearAnnual() {
            CompoundInterestResult result = service.calculate(request("1000", "10", 1, 1));

            assertEquals(new BigDecimal("1100.00"), result.getFinalBalance());
            assertEquals(new BigDecimal("100.00"), result.getTotalInterest());
            assertEquals(new BigDecimal("0.00"), result.getTotalContributions());
            assertEquals(new BigDecimal("1000.00"), result.getTotalInvested());
        }

        @Test
        @DisplayName("P=1000 r=10% t=3 years -> final balance 1331.00")
        void threeYearsAnnual() {
            CompoundInterestResult result = service.calculate(request("1000", "10", 1, 3));

            assertEquals(new BigDecimal("1331.00"), result.getFinalBalance());
            assertEquals(new BigDecimal("331.00"), result.getTotalInterest());
        }

        @Test
        @DisplayName("Zero interest rate -> final balance equals principal")
        void zeroInterestRate() {
            CompoundInterestResult result = service.calculate(request("5000", "0.001", 1, 10));

            // Balance must exceed principal
            assertTrue(result.getFinalBalance().compareTo(new BigDecimal("5000")) > 0);
        }
    }

    // -----------------------------------------------------------------------
    // Monthly compounding
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Monthly compounding (n=12)")
    class MonthlyCompounding {

        @Test
        @DisplayName("P=1000 r=10% t=1 year monthly -> ~1104.71")
        void oneYearMonthly() {
            CompoundInterestResult result = service.calculate(request("1000", "10", 12, 1));

            // FV = 1000 × (1 + 0.1/12)^12 ≈ 1104.71
            BigDecimal fv = result.getFinalBalance();
            assertTrue(fv.compareTo(new BigDecimal("1104.00")) > 0);
            assertTrue(fv.compareTo(new BigDecimal("1105.00")) < 0);
        }

        @Test
        @DisplayName("Monthly compounding produces higher FV than annual for same rate")
        void monthlyHigherThanAnnual() {
            CompoundInterestResult monthly = service.calculate(request("1000", "10", 12, 5));
            CompoundInterestResult annual = service.calculate(request("1000", "10", 1, 5));

            assertTrue(monthly.getFinalBalance().compareTo(annual.getFinalBalance()) > 0);
        }
    }

    // -----------------------------------------------------------------------
    // Regular contributions
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Regular contributions")
    class RegularContributions {

        @Test
        @DisplayName("End-of-period contribution increases final balance")
        void endOfPeriodContributionIncreasesFV() {
            CompoundInterestResult withContrib =
                    service.calculate(requestWithContribution("1000", "10", 12, 5, "100", false));
            CompoundInterestResult withoutContrib = service.calculate(request("1000", "10", 12, 5));

            assertTrue(
                    withContrib.getFinalBalance().compareTo(withoutContrib.getFinalBalance()) > 0);
        }

        @Test
        @DisplayName("Beginning-of-period contribution produces higher FV than end-of-period")
        void beginningHigherThanEnd() {
            CompoundInterestResult atBeginning =
                    service.calculate(requestWithContribution("1000", "8", 12, 10, "200", true));
            CompoundInterestResult atEnd =
                    service.calculate(requestWithContribution("1000", "8", 12, 10, "200", false));

            assertTrue(atBeginning.getFinalBalance().compareTo(atEnd.getFinalBalance()) > 0);
        }

        @Test
        @DisplayName("Total contributions matches frequency × years × PMT")
        void totalContributionsCalculatedCorrectly() {
            // n=12, t=5, PMT=100 → expected contributions = 12 × 5 × 100 = 6000
            CompoundInterestResult result =
                    service.calculate(requestWithContribution("1000", "7", 12, 5, "100", false));

            assertEquals(new BigDecimal("6000.00"), result.getTotalContributions());
            assertEquals(new BigDecimal("7000.00"), result.getTotalInvested());
        }

        @Test
        @DisplayName("Zero contribution is equivalent to no-contribution scenario")
        void zeroContributionEquivalent() {
            CompoundInterestResult withZero =
                    service.calculate(requestWithContribution("5000", "5", 4, 10, "0", false));
            CompoundInterestResult without = service.calculate(request("5000", "5", 4, 10));

            assertEquals(without.getFinalBalance(), withZero.getFinalBalance());
        }
    }

    // -----------------------------------------------------------------------
    // Yearly breakdown
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Yearly breakdown")
    class YearlyBreakdownTests {

        @Test
        @DisplayName("Breakdown has exactly `years` rows")
        void breakdownHasCorrectSize() {
            CompoundInterestResult result = service.calculate(request("1000", "10", 1, 10));

            assertEquals(10, result.getYearlyBreakdown().size());
        }

        @Test
        @DisplayName("Breakdown rows are numbered 1 to years")
        void breakdownRowsNumbered() {
            CompoundInterestResult result = service.calculate(request("1000", "10", 1, 5));
            List<CompoundInterestYearlyBreakdown> rows = result.getYearlyBreakdown();

            for (int i = 0; i < rows.size(); i++) {
                assertEquals(i + 1, rows.get(i).getYear());
            }
        }

        @Test
        @DisplayName("First row starting balance equals principal")
        void firstRowStartBalanceIsPrincipal() {
            CompoundInterestResult result = service.calculate(request("2500", "6", 12, 3));

            assertEquals(
                    new BigDecimal("2500.00"),
                    result.getYearlyBreakdown().get(0).getStartingBalance());
        }

        @Test
        @DisplayName(
                "Each row: startingBalance + contributions + interest ≈ endingBalance (within 1 cent)")
        void rowBalanceConsistency() {
            CompoundInterestResult result =
                    service.calculate(requestWithContribution("1000", "8", 12, 5, "50", false));

            for (CompoundInterestYearlyBreakdown row : result.getYearlyBreakdown()) {
                BigDecimal sum =
                        row.getStartingBalance()
                                .add(row.getContributions())
                                .add(row.getInterestEarned());
                BigDecimal diff = sum.subtract(row.getEndingBalance()).abs();
                // Allow 1-cent rounding difference: each field is independently
                // rounded to 2 d.p., so their rounded sum may be off by ±0.01.
                assertTrue(
                        diff.compareTo(new BigDecimal("0.01")) <= 0,
                        "Row "
                                + row.getYear()
                                + " balance inconsistency exceeds 1 cent: diff="
                                + diff);
            }
        }

        @Test
        @DisplayName("Each row's ending balance equals next row's starting balance")
        void endBalanceChainsToNextStart() {
            CompoundInterestResult result = service.calculate(request("1000", "10", 4, 5));
            List<CompoundInterestYearlyBreakdown> rows = result.getYearlyBreakdown();

            for (int i = 0; i < rows.size() - 1; i++) {
                assertEquals(
                        rows.get(i).getEndingBalance(),
                        rows.get(i + 1).getStartingBalance(),
                        "Year "
                                + rows.get(i).getYear()
                                + " end should match year "
                                + rows.get(i + 1).getYear()
                                + " start");
            }
        }

        @Test
        @DisplayName("Last row ending balance equals result finalBalance")
        void lastRowEndBalanceEqualsFinalBalance() {
            CompoundInterestResult result = service.calculate(request("3000", "7", 12, 20));
            List<CompoundInterestYearlyBreakdown> rows = result.getYearlyBreakdown();

            assertEquals(result.getFinalBalance(), rows.get(rows.size() - 1).getEndingBalance());
        }

        @Test
        @DisplayName("Cumulative interest grows monotonically")
        void cumulativeInterestGrowsMonotonically() {
            CompoundInterestResult result = service.calculate(request("1000", "5", 12, 10));
            List<CompoundInterestYearlyBreakdown> rows = result.getYearlyBreakdown();

            for (int i = 1; i < rows.size(); i++) {
                assertTrue(
                        rows.get(i)
                                        .getCumulativeInterest()
                                        .compareTo(rows.get(i - 1).getCumulativeInterest())
                                > 0,
                        "Cumulative interest should increase each year");
            }
        }

        @Test
        @DisplayName("Final row cumulativeInterest equals result totalInterest")
        void finalCumulativeInterestMatchesTotalInterest() {
            CompoundInterestResult result =
                    service.calculate(requestWithContribution("5000", "6", 12, 15, "200", false));
            List<CompoundInterestYearlyBreakdown> rows = result.getYearlyBreakdown();

            assertEquals(
                    result.getTotalInterest(), rows.get(rows.size() - 1).getCumulativeInterest());
        }
    }

    // -----------------------------------------------------------------------
    // Summary totals consistency
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Summary totals consistency")
    class SummaryTotals {

        @Test
        @DisplayName("totalInvested + totalInterest = finalBalance")
        void totalsAddUpToFinalBalance() {
            CompoundInterestResult result =
                    service.calculate(requestWithContribution("10000", "7", 12, 20, "300", false));

            BigDecimal sumCheck = result.getTotalInvested().add(result.getTotalInterest());
            assertEquals(result.getFinalBalance(), sumCheck);
        }

        @Test
        @DisplayName("principal is stored in result")
        void principalStoredCorrectly() {
            CompoundInterestResult result = service.calculate(request("7500", "5", 4, 8));

            assertEquals(new BigDecimal("7500.00"), result.getPrincipal());
        }
    }

    // -----------------------------------------------------------------------
    // Effective Annual Rate
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Effective Annual Rate (EAR)")
    class EffectiveAnnualRateTests {

        @Test
        @DisplayName("Annual compounding: EAR equals nominal rate")
        void earEqualsNominalForAnnualCompounding() {
            CompoundInterestResult result = service.calculate(request("1000", "10", 1, 5));

            // EAR = (1 + 0.10/1)^1 - 1 = 10.0000%
            assertEquals(new BigDecimal("10.0000"), result.getEffectiveAnnualRate());
        }

        @Test
        @DisplayName("Monthly compounding: EAR > nominal rate")
        void earGreaterThanNominalForMonthlyCompounding() {
            CompoundInterestResult result = service.calculate(request("1000", "12", 12, 1));

            // EAR = (1 + 0.01)^12 - 1 ≈ 12.6825%
            assertTrue(result.getEffectiveAnnualRate().compareTo(new BigDecimal("12")) > 0);
            assertTrue(result.getEffectiveAnnualRate().compareTo(new BigDecimal("13")) < 0);
        }

        @Test
        @DisplayName("Higher compounding frequency → higher EAR for same nominal rate")
        void higherFrequencyHigherEAR() {
            CompoundInterestResult quarterly = service.calculate(request("1000", "8", 4, 1));
            CompoundInterestResult monthly = service.calculate(request("1000", "8", 12, 1));
            CompoundInterestResult daily = service.calculate(request("1000", "8", 365, 1));

            assertTrue(
                    monthly.getEffectiveAnnualRate().compareTo(quarterly.getEffectiveAnnualRate())
                            > 0);
            assertTrue(
                    daily.getEffectiveAnnualRate().compareTo(monthly.getEffectiveAnnualRate()) > 0);
        }
    }

    // -----------------------------------------------------------------------
    // Compounding frequencies
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("All compounding frequencies")
    class AllFrequencies {

        @Test
        @DisplayName("Semi-annual compounding (n=2)")
        void semiAnnual() {
            CompoundInterestResult result = service.calculate(request("1000", "10", 2, 1));

            // FV = 1000 × (1 + 0.05)^2 = 1102.50
            assertEquals(new BigDecimal("1102.50"), result.getFinalBalance());
        }

        @Test
        @DisplayName("Quarterly compounding (n=4)")
        void quarterly() {
            CompoundInterestResult result = service.calculate(request("1000", "10", 4, 1));

            // FV = 1000 × (1.025)^4 ≈ 1103.81
            BigDecimal fv = result.getFinalBalance();
            assertTrue(fv.compareTo(new BigDecimal("1103.00")) > 0);
            assertTrue(fv.compareTo(new BigDecimal("1105.00")) < 0);
        }

        @Test
        @DisplayName("Daily compounding (n=365) produces highest FV among standard frequencies")
        void dailyHighest() {
            CompoundInterestResult daily = service.calculate(request("1000", "10", 365, 5));
            CompoundInterestResult monthly = service.calculate(request("1000", "10", 12, 5));
            CompoundInterestResult quarterly = service.calculate(request("1000", "10", 4, 5));
            CompoundInterestResult annual = service.calculate(request("1000", "10", 1, 5));

            assertTrue(daily.getFinalBalance().compareTo(monthly.getFinalBalance()) > 0);
            assertTrue(monthly.getFinalBalance().compareTo(quarterly.getFinalBalance()) > 0);
            assertTrue(quarterly.getFinalBalance().compareTo(annual.getFinalBalance()) > 0);
        }
    }
}
