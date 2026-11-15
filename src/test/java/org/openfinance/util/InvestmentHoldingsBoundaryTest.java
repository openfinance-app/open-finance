package org.openfinance.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.openfinance.entity.AcquisitionType;
import org.openfinance.entity.Asset;
import org.openfinance.entity.Transaction;
import org.openfinance.exception.InvalidTransactionException;

@DisplayName("Investment positions preserve ownership, gift basis and dated moving-average cost")
class InvestmentHoldingsBoundaryTest {
    private static final LocalDate START = LocalDate.of(2025, 1, 1);

    private Asset asset() {
        return Asset.builder()
                .id(1L)
                .name("Holding")
                .currency("EUR")
                .purchaseDate(START)
                .importOpeningQuantity(new BigDecimal("10"))
                .importOpeningCost(new BigDecimal("100"))
                .acquisitionType(AcquisitionType.PURCHASE)
                .build();
    }

    private Transaction movement(long id, String quantity, String amount) {
        return Transaction.builder()
                .id(id)
                .assetId(1L)
                .date(START)
                .currency("EUR")
                .investmentQuantity(new BigDecimal(quantity))
                .amount(new BigDecimal(amount))
                .investmentCash(true)
                .isDeleted(false)
                .build();
    }

    @ParameterizedTest
    @ValueSource(strings = {"other-holding", "ordinary-cash", "deleted", "future"})
    void irrelevantMovementsCannotChangeThePosition(String reason) {
        Transaction transaction = movement(1, "5", "70");
        switch (reason) {
            case "other-holding" -> transaction.setAssetId(2L);
            case "ordinary-cash" -> transaction.setInvestmentQuantity(null);
            case "deleted" -> transaction.setIsDeleted(true);
            case "future" -> transaction.setDate(START.plusDays(1));
            default -> throw new AssertionError(reason);
        }
        InvestmentHoldings.Position position =
                InvestmentHoldings.at(asset(), List.of(transaction), START);
        assertThat(position.quantity()).isEqualByComparingTo("10");
        assertThat(position.cost()).isEqualByComparingTo("100");
        assertThat(position.realizedGain()).isZero();
    }

    @Test
    void datesBeforeAcquisitionHaveNoPosition() {
        InvestmentHoldings.Position position =
                InvestmentHoldings.at(asset(), List.of(movement(1, "5", "70")), START.minusDays(1));
        assertThat(position.quantity()).isZero();
        assertThat(position.cost()).isZero();
        assertThat(position.realizedGain()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"explicit-value", "native-amount", "original-amount"})
    void acquisitionCostUsesTheHoldingsCurrencyExactlyOnce(String source) {
        Transaction purchase = movement(1, "5", "70");
        if (source.equals("explicit-value")) {
            purchase.setInvestmentValue(new BigDecimal("60.001"));
            purchase.setAmount(new BigDecimal("999"));
        } else if (source.equals("original-amount")) {
            purchase.setCurrency("USD");
            purchase.setOriginalCurrency("EUR");
            purchase.setOriginalAmount(new BigDecimal("60.001"));
        } else purchase.setAmount(new BigDecimal("60.001"));
        InvestmentHoldings.Position position =
                InvestmentHoldings.at(asset(), List.of(purchase), START);
        assertThat(position.quantity()).isEqualByComparingTo("15");
        assertThat(position.cost()).isEqualByComparingTo("160.001");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {"no-original-currency", "other-original-currency", "no-original-amount"})
    void foreignCashWithoutAHoldingCurrencyValueCannotInventACostBasis(String reason) {
        Transaction purchase = movement(1, "5", "70");
        purchase.setCurrency("USD");
        purchase.setOriginalCurrency(
                reason.equals("no-original-currency")
                        ? null
                        : reason.equals("other-original-currency") ? "GBP" : "EUR");
        if (!reason.equals("no-original-amount")) purchase.setOriginalAmount(new BigDecimal("60"));
        assertThatThrownBy(() -> InvestmentHoldings.at(asset(), List.of(purchase), START))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("currency does not match");
    }

    @ParameterizedTest
    @CsvSource({"true,32", "false,0"})
    void disposalReleasesGiftValueProportionallyAndOnlyCashSalesRealizeAGain(
            boolean cash, String expectedGain) {
        Asset gift = asset();
        gift.setAcquisitionType(AcquisitionType.GIFT);
        gift.setReceivedValue(new BigDecimal("20"));
        Transaction sale = movement(1, "-4", "80");
        sale.setInvestmentCash(cash);
        InvestmentHoldings.Position position = InvestmentHoldings.at(gift, List.of(sale), START);
        assertThat(position.quantity()).isEqualByComparingTo("6");
        assertThat(position.cost()).isEqualByComparingTo("60");
        assertThat(position.remainingReceivedValue()).isEqualByComparingTo("12");
        assertThat(position.realizedGain()).isEqualByComparingTo(expectedGain);
    }

    @Test
    void anUnknownGiftValueKeepsTheRealizedGainUnknown() {
        Asset gift = asset();
        gift.setAcquisitionType(AcquisitionType.GIFT);
        InvestmentHoldings.Position position =
                InvestmentHoldings.at(gift, List.of(movement(1, "-4", "80")), START);
        assertThat(position.remainingReceivedValue()).isNull();
        assertThat(position.realizedGain()).isNull();
        assertThat(position.cost()).isEqualByComparingTo("60");
    }

    @Test
    void aLaterSameDayPurchaseDoesNotChangeTheEarlierSalesReleasedCost() {
        Transaction sale = movement(1, "-5", "100");
        Transaction purchase = movement(2, "5", "200");
        InvestmentHoldings.Position position =
                InvestmentHoldings.at(asset(), List.of(purchase, sale), START);
        assertThat(position.realizedGain()).isEqualByComparingTo("50");
        assertThat(position.cost()).isEqualByComparingTo("250");
        assertThat(position.quantity()).isEqualByComparingTo("10");
    }

    @Test
    void aLaterPurchaseCannotCoverAnEarlierOversale() {
        Transaction sale = movement(1, "-11", "110");
        Transaction purchase = movement(2, "5", "200");
        assertThatThrownBy(() -> InvestmentHoldings.at(asset(), List.of(purchase, sale), START))
                .isInstanceOf(InvalidTransactionException.class)
                .hasMessageContaining("only 10 units");
    }

    @ParameterizedTest
    @ValueSource(strings = {"-10", "-4"})
    void updatingTheCurrentPositionClearsCostAfterACompleteDisposal(String sold) {
        Asset asset = asset();
        InvestmentHoldings.update(asset, List.of(movement(1, sold, "120")));
        assertThat(asset.getQuantity()).isEqualByComparingTo(sold.equals("-10") ? "0" : "6");
        assertThat(asset.getPurchasePrice()).isEqualByComparingTo(sold.equals("-10") ? "0" : "10");
    }

    @Test
    void noImportedOpeningPositionStartsAtZero() {
        Asset asset = asset();
        asset.setImportOpeningQuantity(null);
        asset.setImportOpeningCost(null);
        InvestmentHoldings.Position position =
                InvestmentHoldings.at(asset, List.of(movement(1, "5", "70")), START);
        assertThat(position.quantity()).isEqualByComparingTo("5");
        assertThat(position.cost()).isEqualByComparingTo("70");
    }
}
