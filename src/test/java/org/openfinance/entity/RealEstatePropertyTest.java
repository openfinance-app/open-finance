package org.openfinance.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Derived-money helpers on {@link RealEstateProperty}: decimal parsing of the (decrypted) string
 * fields, appreciation/yield math, and the gift/planned acquisition-value rules. These run on every
 * property row shown in the UI, so null/zero handling must never throw.
 */
@DisplayName("RealEstateProperty derived values")
class RealEstatePropertyTest {

    private RealEstateProperty property() {
        return RealEstateProperty.builder()
                .userId(1L)
                .name("Flat")
                .address("1 Street")
                .propertyType(PropertyType.RESIDENTIAL)
                .acquisitionType(AcquisitionType.PURCHASE)
                .purchasePrice("200000")
                .purchaseDate(LocalDate.of(2020, 1, 1))
                .currentValue("250000")
                .currency("EUR")
                .rentalIncome("1000")
                .build();
    }

    @Test
    @DisplayName("Decimal getters parse decrypted strings and tolerate blank values")
    void decimalGettersParseAndTolerateBlank() {
        RealEstateProperty p = property();
        assertThat(p.getPurchasePriceDecimal()).isEqualByComparingTo("200000");
        assertThat(p.getCurrentValueDecimal()).isEqualByComparingTo("250000");
        assertThat(p.getRentalIncomeDecimal()).isEqualByComparingTo("1000");

        p.setPurchasePrice("  ");
        p.setCurrentValue(null);
        p.setRentalIncome("");
        assertThat(p.getPurchasePriceDecimal()).isNull();
        assertThat(p.getCurrentValueDecimal()).isNull();
        assertThat(p.getRentalIncomeDecimal()).isNull();
    }

    @Test
    @DisplayName("Decimal getters reject non-numeric decrypted values loudly")
    void decimalGettersRejectGarbage() {
        RealEstateProperty p = property();
        p.setCurrentValue("not-a-number");
        assertThatThrownBy(p::getCurrentValueDecimal).isInstanceOf(NumberFormatException.class);
    }

    @Test
    @DisplayName("Appreciation is current minus purchase, rounded to cents")
    void appreciationMath() {
        RealEstateProperty p = property();
        assertThat(p.getAppreciation()).isEqualByComparingTo("50000.00");
        assertThat(p.getAppreciationPercentage()).isEqualByComparingTo("25.00");

        p.setCurrentValue("150000.005");
        assertThat(p.getAppreciation()).isEqualByComparingTo("-50000.00");
        assertThat(p.getAppreciation().scale()).isEqualTo(2);
    }

    @Test
    @DisplayName(
            "Appreciation percentage is null (not ArithmeticException) for a zero purchase price")
    void appreciationPercentageZeroPurchase() {
        RealEstateProperty p = property();
        p.setPurchasePrice("0");
        assertThat(p.getAppreciation()).isEqualByComparingTo("250000.00");
        assertThat(p.getAppreciationPercentage()).isNull();
    }

    @Test
    @DisplayName("Missing current value yields null appreciation instead of throwing")
    void appreciationMissingValues() {
        RealEstateProperty p = property();
        p.setCurrentValue(null);
        assertThat(p.getAppreciation()).isNull();
        assertThat(p.getAppreciationPercentage()).isNull();
    }

    @Test
    @DisplayName("Rental yield annualises monthly rent over current value")
    void rentalYieldMath() {
        // 1000 * 12 / 250000 = 4.8%
        assertThat(property().getRentalYield()).isEqualByComparingTo("4.80");
    }

    @Test
    @DisplayName("Rental yield is null for zero current value or missing rent")
    void rentalYieldGuards() {
        RealEstateProperty p = property();
        p.setCurrentValue("0");
        assertThat(p.getRentalYield()).isNull();

        p.setCurrentValue("250000");
        p.setRentalIncome(null);
        assertThat(p.getRentalYield()).isNull();
        assertThat(p.isRentalProperty()).isFalse();
    }

    @Test
    @DisplayName("isRentalProperty requires strictly positive rent")
    void rentalPropertyDetection() {
        RealEstateProperty p = property();
        assertThat(p.isRentalProperty()).isTrue();
        p.setRentalIncome("0");
        assertThat(p.isRentalProperty()).isFalse();
        p.setRentalIncome("-50");
        assertThat(p.isRentalProperty()).isFalse();
    }

    @Test
    @DisplayName("Planned properties have no acquisition value")
    void plannedHasNoAcquisitionValue() {
        RealEstateProperty p = property();
        p.setAcquisitionType(AcquisitionType.PLANNED);
        assertThat(p.getAcquisitionValue()).isNull();
        assertThat(p.getAppreciation()).isNull();
    }

    @Test
    @DisplayName("Gift acquisition value combines received value and acquisition cost")
    void giftAcquisitionValue() {
        RealEstateProperty p = property();
        p.setAcquisitionType(AcquisitionType.GIFT);
        p.setReceivedValue(new BigDecimal("180000"));
        p.setPurchasePrice("5000"); // fees paid on top of the gift
        assertThat(p.getAcquisitionValue()).isEqualByComparingTo("185000");
    }

    @Test
    @DisplayName("Gift without received value has no acquisition value")
    void giftWithoutReceivedValue() {
        RealEstateProperty p = property();
        p.setAcquisitionType(AcquisitionType.GIFT);
        p.setReceivedValue(null);
        assertThat(p.getAcquisitionValue()).isNull();
    }

    @Test
    @DisplayName("Gift with received value but no purchase price does not throw NPE")
    void giftWithoutPurchasePrice() {
        RealEstateProperty p = property();
        p.setAcquisitionType(AcquisitionType.GIFT);
        p.setReceivedValue(new BigDecimal("180000"));
        p.setPurchasePrice(null);
        assertThatCode(p::getAcquisitionValue).doesNotThrowAnyException();
        assertThat(p.getAcquisitionValue()).isEqualByComparingTo("180000");
        assertThat(p.getAppreciation()).isEqualByComparingTo("70000.00");
    }

    @Test
    @DisplayName("hasMortgage reflects the mortgage link")
    void mortgageDetection() {
        RealEstateProperty p = property();
        assertThat(p.hasMortgage()).isFalse();
        p.setMortgageId(42L);
        assertThat(p.hasMortgage()).isTrue();
    }

    @Test
    @DisplayName("Purchase date validation allows planned/future only for PLANNED")
    void acquisitionDateValidation() {
        RealEstateProperty p = property();
        p.setPurchaseDate(LocalDate.now().plusDays(1));
        assertThat(p.isAcquisitionDateValid()).isFalse();

        p.setAcquisitionType(AcquisitionType.PLANNED);
        assertThat(p.isAcquisitionDateValid()).isTrue();

        p.setAcquisitionType(AcquisitionType.PURCHASE);
        p.setPurchaseDate(null);
        assertThat(p.isAcquisitionDateValid()).isTrue();
        p.setPurchaseDate(LocalDate.now());
        assertThat(p.isAcquisitionDateValid()).isTrue();
    }
}
