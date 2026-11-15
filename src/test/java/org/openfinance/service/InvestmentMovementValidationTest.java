package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openfinance.dto.HistoricalPrice;
import org.openfinance.dto.InvestmentMovementRequest;
import org.openfinance.dto.InvestmentMovementRequest.Operation;
import org.openfinance.dto.InvestmentMovementRequest.Pricing;
import org.openfinance.dto.InvestmentQuote;
import org.openfinance.entity.AcquisitionType;
import org.openfinance.entity.Asset;
import org.openfinance.entity.AssetType;
import org.openfinance.entity.MovementType;
import org.openfinance.entity.Transaction;
import org.openfinance.entity.TransactionType;
import org.openfinance.exception.InvalidTransactionException;
import org.openfinance.repository.AssetRepository;
import org.openfinance.repository.TransactionRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("Investment instructions retain dated prices and reject conflicting accounting")
class InvestmentMovementValidationTest {
    private static final LocalDate DAY = LocalDate.of(2024, 6, 10);
    @Mock private AssetRepository assets;
    @Mock private TransactionRepository transactions;
    @Mock private MarketDataService market;
    @Mock private ExchangeRateService exchange;
    @InjectMocks private InvestmentMovementServiceImpl service;
    private Asset holding;

    @BeforeEach
    void holding() {
        holding =
                Asset.builder()
                        .id(7L)
                        .userId(1L)
                        .accountId(2L)
                        .name("Holding")
                        .symbol("ABC")
                        .type(AssetType.STOCK)
                        .currency("EUR")
                        .quantity(BigDecimal.TEN)
                        .purchasePrice(new BigDecimal("2"))
                        .currentPrice(new BigDecimal("3"))
                        .purchaseDate(DAY.minusMonths(1))
                        .importOpeningQuantity(BigDecimal.TEN)
                        .importOpeningCost(new BigDecimal("20"))
                        .build();
        lenient().when(assets.findByIdAndUserId(7L, 1L)).thenReturn(Optional.of(holding));
    }

    @ParameterizedTest
    @CsvSource({
        "ADD,INCOME,2,false",
        "BUY,EXPENSE,2,true",
        "REMOVE,EXPENSE,-2,false",
        "SELL,INCOME,-2,true"
    })
    void operationsSetTheCorrectUnitDirectionAndCashEffect(
            Operation operation, TransactionType type, String units, boolean cash) {
        Transaction tx = transaction();
        tx.setType(type);
        service.apply(tx, instruction(operation), true, null);
        assertThat(tx.getInvestmentQuantity()).isEqualByComparingTo(units);
        assertThat(tx.isInvestmentCash()).isEqualTo(cash);
        assertThat(tx.getInvestmentValue()).isEqualByComparingTo("10");
        assertThat(tx.getInvestmentPrice()).isEqualByComparingTo("5");
        assertThat(tx.getInvestmentPriceDate()).isEqualTo(DAY);
        assertThat(tx.getInvestmentPriceSource()).isEqualTo("RECORDED");
        verifyNoInteractions(market, exchange);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"0", "-1", "0.0000000000000000001", "100000000000000000000000000"})
    void quantityMustBePositiveAndFitTheMonetaryPrecisionContract(String quantity) {
        InvestmentMovementRequest request =
                instruction(Operation.ADD).toBuilder()
                        .quantity(quantity == null ? null : new BigDecimal(quantity))
                        .build();
        assertThatThrownBy(() -> service.apply(transaction(), request, true, null))
                .isInstanceOf(InvalidTransactionException.class)
                .hasMessageContaining("quantity");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "wrong account",
                "no operation",
                "no pricing",
                "wrong direction",
                "transfer buy",
                "liability",
                "property",
                "movement"
            })
    void conflictingTransactionContextsCannotBecomeInvestmentMovements(String conflict) {
        Transaction tx = transaction();
        InvestmentMovementRequest.InvestmentMovementRequestBuilder request =
                instruction(Operation.ADD).toBuilder();
        switch (conflict) {
            case "wrong account" -> tx.setAccountId(99L);
            case "no operation" -> request.operation(null);
            case "no pricing" -> request.pricing(null);
            case "wrong direction" -> tx.setType(TransactionType.EXPENSE);
            case "transfer buy" -> {
                tx.setTransferId("pair");
                tx.setType(TransactionType.EXPENSE);
                request.operation(Operation.BUY);
            }
            case "liability" -> tx.setLiabilityId(9L);
            case "property" -> tx.setRealEstateId(9L);
            case "movement" -> tx.setMovementType(MovementType.FEE);
            default -> throw new AssertionError(conflict);
        }
        assertThatThrownBy(() -> service.apply(tx, request.build(), true, null))
                .isInstanceOf(InvalidTransactionException.class);
        verifyNoInteractions(market, exchange);
        verify(assets, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing id", "unowned", "physical", "real estate", "planned"})
    void onlyOwnedActiveFinancialHoldingsCanReceiveInstructions(String invalid) {
        Long id = 7L;
        switch (invalid) {
            case "missing id" -> id = null;
            case "unowned" -> when(assets.findByIdAndUserId(7L, 1L)).thenReturn(Optional.empty());
            case "physical" -> holding.setType(AssetType.VEHICLE);
            case "real estate" -> holding.setType(AssetType.REAL_ESTATE);
            case "planned" -> holding.setAcquisitionType(AcquisitionType.PLANNED);
            default -> throw new AssertionError(invalid);
        }
        InvestmentMovementRequest request =
                instruction(Operation.ADD).toBuilder().assetId(id).build();
        assertThatThrownBy(() -> service.apply(transaction(), request, true, null))
                .isInstanceOf(InvalidTransactionException.class);
        verifyNoInteractions(market, exchange, transactions);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "amount absent",
                "zero amount",
                "negative amount",
                "currency absent",
                "date absent"
            })
    void quotesRequireACompletePositiveValuation(String missing) {
        BigDecimal amount =
                switch (missing) {
                    case "amount absent" -> null;
                    case "zero amount" -> BigDecimal.ZERO;
                    case "negative amount" -> BigDecimal.ONE.negate();
                    default -> BigDecimal.TEN;
                };
        assertThatThrownBy(
                        () ->
                                service.quote(
                                        7L,
                                        1L,
                                        amount,
                                        missing.equals("currency absent") ? null : "EUR",
                                        missing.equals("date absent") ? null : DAY,
                                        null))
                .isInstanceOf(InvalidTransactionException.class)
                .hasMessageContaining("positive amount");
        verifyNoInteractions(market, exchange);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"0", "-1"})
    void unavailableOrInvalidCurrencyConversionsCannotCreateUnits(String converted) {
        when(exchange.convert(BigDecimal.TEN, "USD", "EUR", DAY))
                .thenReturn(converted == null ? null : new BigDecimal(converted));
        assertThatThrownBy(() -> service.quote(7L, 1L, BigDecimal.TEN, "USD", DAY, null))
                .isInstanceOf(InvalidTransactionException.class)
                .hasMessageContaining("conversion");
        verifyNoInteractions(market);
    }

    @ParameterizedTest
    @ValueSource(strings = {"date", "currency", "null price", "zero price", "negative price"})
    void historicalQuotesMustMatchBothDateAndDenomination(String invalid) {
        HistoricalPrice price = price();
        switch (invalid) {
            case "date" -> price.setDate(DAY.minusDays(1));
            case "currency" -> price.setCurrency("USD");
            case "null price" -> price.setClose(null);
            case "zero price" -> price.setClose(BigDecimal.ZERO);
            case "negative price" -> price.setClose(BigDecimal.ONE.negate());
            default -> throw new AssertionError(invalid);
        }
        when(market.getHistoricalPrices("ABC", DAY, DAY, "EUR")).thenReturn(List.of(price));
        assertThatThrownBy(() -> service.quote(7L, 1L, BigDecimal.TEN, "EUR", DAY, null))
                .isInstanceOf(InvalidTransactionException.class)
                .hasMessageContaining("historical price");
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing symbol", "blank symbol", "future"})
    void missingMarketIdentifiersOrFutureDatesDoNotTriggerMarketRequests(String invalid) {
        if (invalid.equals("missing symbol")) holding.setSymbol(null);
        if (invalid.equals("blank symbol")) holding.setSymbol(" ");
        LocalDate date = invalid.equals("future") ? LocalDate.now().plusDays(1) : DAY;
        assertThatThrownBy(() -> service.quote(7L, 1L, BigDecimal.TEN, "EUR", date, null))
                .isInstanceOf(InvalidTransactionException.class)
                .hasMessageContaining("historical price");
        verifyNoInteractions(market);
    }

    @Test
    void unitRoundingCannotTurnATinyPurchaseIntoAZeroQuantityMovement() {
        HistoricalPrice price = price();
        price.setClose(new BigDecimal("1000000"));
        when(market.getHistoricalPrices("ABC", DAY, DAY, "EUR")).thenReturn(List.of(price));
        assertThatThrownBy(
                        () ->
                                service.quote(
                                        7L,
                                        1L,
                                        new BigDecimal("0.000000000000000001"),
                                        "EUR",
                                        DAY,
                                        null))
                .isInstanceOf(InvalidTransactionException.class)
                .hasMessageContaining("too small");
    }

    @Test
    void recordedOriginalCurrencyIsUsedInsteadOfConvertingTheBookedAmountTwice() {
        Transaction tx = transaction();
        tx.setAmount(new BigDecimal("11"));
        tx.setCurrency("USD");
        tx.setOriginalAmount(BigDecimal.TEN);
        tx.setOriginalCurrency("EUR");
        service.apply(tx, instruction(Operation.ADD), true, null);
        assertThat(tx.getInvestmentValue()).isEqualByComparingTo("10");
        assertThat(tx.getInvestmentPrice()).isEqualByComparingTo("5");
        verifyNoInteractions(exchange, market);
    }

    @Test
    void aChangedAmountCanReuseItsDatedExecutionPriceWithoutRefetching() {
        Transaction previous = transaction();
        previous.setAssetId(7L);
        previous.setInvestmentQuantity(BigDecimal.ONE);
        previous.setInvestmentValue(BigDecimal.TEN);
        previous.setInvestmentPrice(new BigDecimal("2"));
        previous.setInvestmentPriceDate(DAY);
        previous.setInvestmentPriceSource("HISTORICAL_CLOSE");
        when(transactions.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(previous));
        InvestmentQuote quote = service.quote(7L, 1L, new BigDecimal("12"), "EUR", DAY, 1L);
        assertThat(quote.getValue()).isEqualByComparingTo("12");
        assertThat(quote.getQuantity()).isEqualByComparingTo("6");
        verifyNoInteractions(market, exchange);
    }

    @Test
    void removableQuantityAccountsForLaterSalesBeforeAllowingAnEarlierSale() {
        Transaction earlier = movement(1L, DAY.minusDays(1), "5");
        Transaction sale = movement(2L, DAY.plusDays(1), "-7");
        Transaction later = movement(3L, DAY.plusDays(2), "3");
        when(transactions.findByAssetIdAndUserId(7L, 1L)).thenReturn(List.of(later, sale, earlier));
        when(market.getHistoricalPrices("ABC", DAY, DAY, "EUR")).thenReturn(List.of(price()));
        InvestmentQuote quote = service.quote(7L, 1L, BigDecimal.TEN, "EUR", DAY, null);
        assertThat(quote.getAvailableQuantity()).isEqualByComparingTo("8");
        assertThat(quote.getQuantity()).isEqualByComparingTo("5");
    }

    @ParameterizedTest
    @ValueSource(strings = {"none", "plain transaction", "other investment", "same movement"})
    void anOpeningPositionIsInitializedOnlyBeforeTheFirstInvestmentMovement(String existing) {
        holding.setImportOpeningQuantity(null);
        holding.setImportOpeningCost(null);
        Transaction prior = transaction();
        if (!existing.equals("plain transaction")) prior.setInvestmentQuantity(BigDecimal.ONE);
        if (!existing.equals("same movement")) prior.setId(99L);
        when(transactions.findByAssetIdAndUserId(7L, 1L))
                .thenReturn(existing.equals("none") ? List.of() : List.of(prior));
        service.apply(transaction(), instruction(Operation.ADD), true, null);
        if (existing.equals("other investment")) {
            assertThat(holding.getImportOpeningQuantity()).isNull();
            verify(assets, never()).save(any());
        } else {
            assertThat(holding.getImportOpeningQuantity()).isEqualByComparingTo("10");
            assertThat(holding.getImportOpeningCost()).isEqualByComparingTo("20");
            verify(assets).save(holding);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"account", "direction", "quantity", "remove instruction"})
    void convertedPropertiesRejectEditsThatWouldRewriteTheirOriginalInvestment(String changed) {
        holding.setType(AssetType.REAL_ESTATE);
        Transaction previous = movement(1L, DAY, "2");
        Transaction tx = transaction();
        InvestmentMovementRequest request = instruction(Operation.ADD);
        switch (changed) {
            case "account" -> tx.setAccountId(99L);
            case "direction" -> tx.setType(TransactionType.EXPENSE);
            case "quantity" -> request = request.toBuilder().quantity(new BigDecimal("3")).build();
            case "remove instruction" -> request = null;
            default -> throw new AssertionError(changed);
        }
        InvestmentMovementRequest submitted = request;
        assertThatThrownBy(() -> service.apply(tx, submitted, true, previous))
                .isInstanceOf(InvalidTransactionException.class)
                .hasMessageContaining("managed as a property");
        verifyNoInteractions(market, exchange);
    }

    private InvestmentMovementRequest instruction(Operation operation) {
        return InvestmentMovementRequest.builder()
                .assetId(7L)
                .operation(operation)
                .pricing(Pricing.QUANTITY)
                .quantity(new BigDecimal("2"))
                .build();
    }

    private Transaction transaction() {
        return Transaction.builder()
                .id(1L)
                .userId(1L)
                .accountId(2L)
                .type(TransactionType.INCOME)
                .amount(BigDecimal.TEN)
                .currency("EUR")
                .date(DAY)
                .build();
    }

    private Transaction movement(Long id, LocalDate date, String quantity) {
        Transaction tx = transaction();
        tx.setId(id);
        tx.setAssetId(7L);
        tx.setDate(date);
        tx.setInvestmentQuantity(new BigDecimal(quantity));
        tx.setInvestmentPriceSource("RECORDED");
        return tx;
    }

    private HistoricalPrice price() {
        return HistoricalPrice.builder()
                .date(DAY)
                .currency("EUR")
                .close(new BigDecimal("2"))
                .build();
    }
}
