package org.openfinance.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link PrincipalLegs}, the single source of the principal-leg computation shared
 * by {@code TransactionService}, {@code NetWorthService} and {@code LiabilityTrancheService}.
 */
@DisplayName("PrincipalLegs — principal leg of a movement (total − categorized splits)")
class PrincipalLegsTest {

    @Test
    @DisplayName("normal case: total minus categorized sum")
    void ofReturnsTotalMinusCategorizedSum() {
        assertThat(PrincipalLegs.of(new BigDecimal("1200"), new BigDecimal("400")))
                .isEqualByComparingTo("800");
    }

    @Test
    @DisplayName("zero categorized sum returns the full total")
    void ofWithZeroCategorizedReturnsTotal() {
        assertThat(PrincipalLegs.of(new BigDecimal("1200"), BigDecimal.ZERO))
                .isEqualByComparingTo("1200");
    }

    @Test
    @DisplayName("fully categorized movement floors at zero")
    void ofFloorsAtZero() {
        assertThat(PrincipalLegs.of(new BigDecimal("300"), new BigDecimal("300")))
                .isEqualByComparingTo("0");
        assertThat(PrincipalLegs.of(new BigDecimal("100"), new BigDecimal("300")))
                .isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("null total resolves to zero")
    void ofWithNullTotalResolvesToZero() {
        assertThat(PrincipalLegs.of(null, new BigDecimal("400"))).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("null categorized sum resolves to zero")
    void ofWithNullCategorizedResolvesToZero() {
        assertThat(PrincipalLegs.of(new BigDecimal("1200"), null)).isEqualByComparingTo("1200");
    }

    @Test
    @DisplayName("both null arguments resolve to zero")
    void ofWithBothNullResolvesToZero() {
        assertThat(PrincipalLegs.of(null, null)).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("null conversion rate is rejected")
    void ofConvertedWithNullRateThrows() {
        assertThatThrownBy(() -> PrincipalLegs.ofConverted(new BigDecimal("1200"), null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Conversion rate");
    }

    @Test
    @DisplayName("zero conversion rate is rejected")
    void ofConvertedWithZeroRateThrows() {
        assertThatThrownBy(
                        () ->
                                PrincipalLegs.ofConverted(
                                        new BigDecimal("1200"), null, BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Conversion rate");
    }

    @Test
    @DisplayName("negative conversion rate is rejected")
    void ofConvertedWithNegativeRateThrows() {
        assertThatThrownBy(
                        () ->
                                PrincipalLegs.ofConverted(
                                        new BigDecimal("1200"), null, new BigDecimal("-0.5")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Conversion rate");
    }

    @Test
    @DisplayName("positive conversion rate converts the categorized sum exactly")
    void ofConvertedConvertsCategorizedSum() {
        assertThat(
                        PrincipalLegs.ofConverted(
                                new BigDecimal("1200"), new BigDecimal("500"), new BigDecimal("2")))
                .isEqualByComparingTo("950");
    }

    @Test
    void convertedCryptoPrincipalRetainsSubCentPrecision() {
        assertThat(
                        PrincipalLegs.ofConverted(
                                new BigDecimal("0.00001234"),
                                new BigDecimal("0.10"),
                                new BigDecimal("50000")))
                .isEqualByComparingTo("0.00001034");
    }
}
