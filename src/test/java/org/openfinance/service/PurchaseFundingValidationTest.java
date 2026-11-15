package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openfinance.dto.AssetPurchaseRequest;
import org.openfinance.dto.AssetRequest;
import org.openfinance.dto.AssetResponse;
import org.openfinance.dto.LiabilityRequest;
import org.openfinance.dto.PropertyPurchaseRequest;
import org.openfinance.dto.RealEstatePropertyRequest;
import org.openfinance.dto.RealEstatePropertyResponse;
import org.openfinance.entity.Account;
import org.openfinance.entity.AcquisitionType;
import org.openfinance.entity.AssetType;
import org.openfinance.entity.LiabilityType;
import org.openfinance.exception.InvalidTransactionException;
import org.openfinance.repository.AccountRepository;
import org.openfinance.repository.PropertyPurchaseReceiptRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("Purchases reject inconsistent funding before creating property, debt or payments")
class PurchaseFundingValidationTest {
    @Mock private AssetService assets;
    @Mock private RealEstateService properties;
    @Mock private AccountRepository accounts;
    @Mock private TransactionService transactions;
    @Mock private PurchasePaymentService payments;
    @Mock private OwnershipReceipts assetReceipts;
    @Mock private PropertyPurchaseReceiptRepository propertyReceipts;
    @Mock private LiabilityService liabilities;
    @Mock private PurchaseLoanService funding;
    private AssetPurchaseServiceImpl assetPurchases;
    private PropertyPurchaseServiceImpl propertyPurchases;
    private static final LocalDate DATE = LocalDate.of(2024, 1, 10);

    @BeforeEach
    void setUp() {
        assetPurchases =
                new AssetPurchaseServiceImpl(
                        assets,
                        accounts,
                        transactions,
                        payments,
                        assetReceipts,
                        liabilities,
                        funding);
        propertyPurchases =
                new PropertyPurchaseServiceImpl(
                        propertyReceipts,
                        properties,
                        liabilities,
                        transactions,
                        accounts,
                        new ObjectMapper().findAndRegisterModules(),
                        payments,
                        funding);
        lenient().when(assetReceipts.previous(any(), any(), any())).thenReturn(null);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "CREDIT_CARD",
                "FUNDED_BALANCE",
                "WRONG_PRINCIPAL",
                "WRONG_CURRENCY",
                "WRONG_DATE",
                "PREVIOUSLY_FUNDED",
                "PROPERTY_ALREADY_LINKED",
                "ACCOUNT_ALREADY_LINKED"
            })
    void newLoansMustMatchThePurchaseAndStartUndrawnOnBothSurfaces(String problem) {
        LiabilityRequest loan = loan();
        switch (problem) {
            case "CREDIT_CARD" -> loan.setType(LiabilityType.CREDIT_CARD);
            case "FUNDED_BALANCE" -> loan.setCurrentBalance(new BigDecimal("0.01"));
            case "WRONG_PRINCIPAL" -> loan.setPrincipal(new BigDecimal("100.01"));
            case "WRONG_CURRENCY" -> loan.setCurrency("EUR");
            case "WRONG_DATE" -> loan.setStartDate(DATE.minusDays(1));
            case "PREVIOUSLY_FUNDED" -> loan.setPreviouslyFunded(true);
            case "PROPERTY_ALREADY_LINKED" -> loan.setRealEstateId(99L);
            case "ACCOUNT_ALREADY_LINKED" -> loan.setRepresentedByAccountId(99L);
            default -> throw new AssertionError(problem);
        }
        AssetPurchaseRequest asset = assetPurchase();
        asset.setLoanAmount(new BigDecimal("100"));
        asset.setNewLoan(loan);
        PropertyPurchaseRequest property = propertyPurchase();
        property.setDownPayment(BigDecimal.ZERO);
        property.setLoanAmount(new BigDecimal("100"));
        property.setNewMortgage(loan);
        assertThatThrownBy(() -> assetPurchases.purchase(1L, asset))
                .isInstanceOf(InvalidTransactionException.class)
                .hasMessageContaining("start undrawn");
        assertThatThrownBy(() -> propertyPurchases.purchase(1L, property))
                .isInstanceOf(InvalidTransactionException.class)
                .hasMessageContaining("start undrawn");
        verifyNoInteractions(assets, properties, liabilities, transactions, payments, funding);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "MISSING_SOURCE",
                "TWO_SOURCES",
                "LOAN_WITHOUT_AMOUNT",
                "RECEIPT_WITHOUT_LOAN"
            })
    void fundingSelectsExactlyOneLoanSourceWhenAndOnlyWhenBorrowing(String problem) {
        AssetPurchaseRequest asset = assetPurchase();
        PropertyPurchaseRequest property = propertyPurchase();
        if (problem.equals("MISSING_SOURCE") || problem.equals("TWO_SOURCES")) {
            asset.setLoanAmount(new BigDecimal("100"));
            property.setLoanAmount(new BigDecimal("100"));
            property.setDownPayment(BigDecimal.ZERO);
        }
        if (problem.equals("TWO_SOURCES") || problem.equals("LOAN_WITHOUT_AMOUNT")) {
            asset.setExistingLoanId(9L);
            property.setExistingMortgageId(9L);
        }
        if (problem.equals("TWO_SOURCES")) {
            asset.setNewLoan(loan());
            property.setNewMortgage(loan());
        }
        if (problem.equals("RECEIPT_WITHOUT_LOAN")) {
            asset.setExistingLoanReceiptId(9L);
            property.setExistingLoanReceiptId(9L);
        }
        assertThatThrownBy(() -> assetPurchases.purchase(1L, asset))
                .isInstanceOf(InvalidTransactionException.class);
        assertThatThrownBy(() -> propertyPurchases.purchase(1L, property))
                .isInstanceOf(InvalidTransactionException.class);
        verifyNoInteractions(assets, properties, liabilities, transactions, payments, funding);
    }

    @ParameterizedTest
    @ValueSource(strings = {"MISSING", "INACTIVE", "UNKNOWN_ACTIVE", "WRONG_CURRENCY"})
    void cashPaymentRequiresAnAccessibleActiveAccountInThePurchaseCurrency(String problem) {
        Account account =
                Account.builder().id(2L).userId(1L).isActive(true).currency("USD").build();
        if (problem.equals("INACTIVE")) account.setIsActive(false);
        if (problem.equals("UNKNOWN_ACTIVE")) account.setIsActive(null);
        if (problem.equals("WRONG_CURRENCY")) account.setCurrency("EUR");
        when(accounts.findByIdAndUserId(2L, 1L))
                .thenReturn(problem.equals("MISSING") ? Optional.empty() : Optional.of(account));
        assertThatThrownBy(() -> assetPurchases.purchase(1L, assetPurchase()))
                .isInstanceOf(InvalidTransactionException.class);
        assertThatThrownBy(() -> propertyPurchases.purchase(1L, propertyPurchase()))
                .isInstanceOf(InvalidTransactionException.class);
        verifyNoInteractions(assets, properties, liabilities, transactions, payments, funding);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "FINANCIAL_ASSET",
                "ZERO_PRICE",
                "FUTURE_DATE",
                "GIFT",
                "PLANNED",
                "EXCESS_LOAN",
                "NO_CASH_TO_LINK"
            })
    void physicalPurchaseCannotCreateAnInvalidAcquisition(String problem) {
        AssetPurchaseRequest request = assetPurchase();
        switch (problem) {
            case "FINANCIAL_ASSET" -> request.getAsset().setType(AssetType.STOCK);
            case "ZERO_PRICE" -> request.getAsset().setPurchasePrice(BigDecimal.ZERO);
            case "FUTURE_DATE" -> request.getAsset().setPurchaseDate(LocalDate.now().plusDays(1));
            case "GIFT" -> request.getAsset().setAcquisitionType(AcquisitionType.GIFT);
            case "PLANNED" -> request.getAsset().setAcquisitionType(AcquisitionType.PLANNED);
            case "EXCESS_LOAN" -> {
                request.setLoanAmount(new BigDecimal("101"));
                request.setExistingLoanId(9L);
            }
            case "NO_CASH_TO_LINK" -> {
                request.setLoanAmount(new BigDecimal("100"));
                request.setExistingLoanId(9L);
                request.setExistingPaymentId(8L);
            }
            default -> throw new AssertionError(problem);
        }
        assertThatThrownBy(() -> assetPurchases.purchase(1L, request))
                .isInstanceOf(InvalidTransactionException.class);
        verifyNoInteractions(assets, liabilities, transactions, payments, funding);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "UNDERFUNDED",
                "OVERFUNDED",
                "INACTIVE",
                "GIFT",
                "PLANNED",
                "MISSING_ACCOUNT"
            })
    void propertyMustBeActiveAndExactlyFunded(String problem) {
        PropertyPurchaseRequest request = propertyPurchase();
        switch (problem) {
            case "UNDERFUNDED" -> request.setDownPayment(new BigDecimal("99.99"));
            case "OVERFUNDED" -> request.setDownPayment(new BigDecimal("100.01"));
            case "INACTIVE" -> request.getProperty().setActive(false);
            case "GIFT" -> request.getProperty().setAcquisitionType(AcquisitionType.GIFT);
            case "PLANNED" -> request.getProperty().setAcquisitionType(AcquisitionType.PLANNED);
            case "MISSING_ACCOUNT" -> request.setAccountId(null);
            default -> throw new AssertionError(problem);
        }
        assertThatThrownBy(() -> propertyPurchases.purchase(1L, request))
                .isInstanceOf(InvalidTransactionException.class);
        verifyNoInteractions(properties, liabilities, transactions, payments, funding);
    }

    @Test
    void cashOnlyPurchasesPreserveExactAmountsAndExplicitDescriptions() {
        Account account =
                Account.builder().id(2L).userId(1L).isActive(true).currency("USD").build();
        when(accounts.findByIdAndUserId(2L, 1L)).thenReturn(Optional.of(account));
        when(assets.createAsset(eq(1L), any())).thenReturn(AssetResponse.builder().id(8L).build());
        AssetPurchaseRequest request = assetPurchase();
        request.getAsset().setQuantity(new BigDecimal("3"));
        request.getAsset().setPurchasePrice(new BigDecimal("0.000000000000000001"));
        request.setPaymentDescription("Deposit and purchase");
        assertThat(assetPurchases.purchase(1L, request)).isEqualTo(8L);
        org.mockito.ArgumentCaptor<org.openfinance.dto.TransactionRequest> captured =
                org.mockito.ArgumentCaptor.forClass(org.openfinance.dto.TransactionRequest.class);
        verify(transactions).createTransaction(eq(1L), captured.capture());
        assertThat(captured.getValue().getAmount()).isEqualByComparingTo("0.000000000000000003");
        assertThat(captured.getValue().getDescription()).isEqualTo("Deposit and purchase");
        assertThat(captured.getValue().getAssetId()).isEqualTo(8L);
        verifyNoInteractions(liabilities, funding, payments);
    }

    @ParameterizedTest
    @ValueSource(strings = {"OWNED", "INACTIVE"})
    void cannotCompleteAPropertyThatIsNotAnActivePlan(String problem) {
        PropertyPurchaseRequest request = propertyPurchase();
        request.setPlannedPropertyId(8L);
        when(accounts.findByIdAndUserId(2L, 1L))
                .thenReturn(Optional.of(Account.builder().isActive(true).currency("USD").build()));
        when(properties.getPropertyById(8L, 1L))
                .thenReturn(
                        RealEstatePropertyResponse.builder()
                                .id(8L)
                                .isActive(!problem.equals("INACTIVE"))
                                .acquisitionType(
                                        problem.equals("OWNED")
                                                ? AcquisitionType.PURCHASE
                                                : AcquisitionType.PLANNED)
                                .build());
        assertThatThrownBy(() -> propertyPurchases.purchase(1L, request))
                .isInstanceOf(InvalidTransactionException.class);
        verify(properties, never()).completePurchasePlan(any(), any(), any());
        verifyNoInteractions(transactions, funding, payments);
    }

    private static AssetPurchaseRequest assetPurchase() {
        AssetRequest asset = new AssetRequest();
        asset.setType(AssetType.VEHICLE);
        asset.setName("Car");
        asset.setQuantity(BigDecimal.ONE);
        asset.setPurchasePrice(new BigDecimal("100"));
        asset.setCurrentPrice(new BigDecimal("100"));
        asset.setCurrency("USD");
        asset.setPurchaseDate(DATE);
        AssetPurchaseRequest request = new AssetPurchaseRequest();
        request.setOperationId(UUID.randomUUID());
        request.setAsset(asset);
        request.setAccountId(2L);
        return request;
    }

    private static PropertyPurchaseRequest propertyPurchase() {
        RealEstatePropertyRequest property = new RealEstatePropertyRequest();
        property.setName("Home");
        property.setPurchasePrice(new BigDecimal("100"));
        property.setCurrency("USD");
        property.setPurchaseDate(DATE);
        property.setActive(true);
        PropertyPurchaseRequest request = new PropertyPurchaseRequest();
        request.setOperationId(UUID.randomUUID());
        request.setProperty(property);
        request.setLoanAmount(BigDecimal.ZERO);
        request.setDownPayment(new BigDecimal("100"));
        request.setRoute(PropertyPurchaseRequest.Route.DIRECT);
        request.setAccountId(2L);
        return request;
    }

    private static LiabilityRequest loan() {
        LiabilityRequest loan = new LiabilityRequest();
        loan.setName("Purchase loan");
        loan.setType(LiabilityType.MORTGAGE);
        loan.setPrincipal(new BigDecimal("100"));
        loan.setCurrentBalance(BigDecimal.ZERO);
        loan.setCurrency("USD");
        loan.setStartDate(DATE);
        return loan;
    }
}
