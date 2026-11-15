package org.openfinance.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.openfinance.entity.AcquisitionType;
import org.openfinance.entity.AssetCondition;
import org.openfinance.entity.AssetType;
import org.openfinance.entity.LiabilityType;

@DisplayName("Request policies reject impossible possessions and preserve partial-update intent")
class AcquisitionPolicyBoundaryTest {
    private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
    private static final Validator VALIDATOR = FACTORY.getValidator();

    @AfterAll
    static void closeValidator() {
        FACTORY.close();
    }

    private static BigDecimal decimal(String value) {
        return value == null ? null : new BigDecimal(value);
    }

    private static LocalDate date(Integer days) {
        return days == null ? null : LocalDate.now().plusDays(days);
    }

    @ParameterizedTest
    @CsvSource(
            value = {
                "VEHICLE,0,false",
                "VEHICLE,0.000000009,false",
                "VEHICLE,0.00000001,true",
                "VEHICLE,1,true",
                "VEHICLE,NULL,true",
                "STOCK,0,true",
                "NULL,0,true"
            },
            nullValues = "NULL")
    void physicalUnitsHaveAnInclusiveMinimumWhileEmptyInvestmentPositionsAreAllowed(
            AssetType type, String quantity, boolean valid) {
        AssetRequest request =
                AssetRequest.builder().type(type).quantity(decimal(quantity)).build();
        assertThat(VALIDATOR.validateProperty(request, "physicalQuantityValid").isEmpty())
                .isEqualTo(valid);
        if (quantity == null)
            assertThat(VALIDATOR.validateProperty(request, "quantity")).isNotEmpty();
    }

    @ParameterizedTest
    @CsvSource(
            value = {
                "PLANNED,0,true",
                "PLANNED,0.0001,false",
                "PLANNED,-1,false",
                "PLANNED,NULL,true",
                "PURCHASE,1,true",
                "GIFT,1,true",
                "NULL,1,true"
            },
            nullValues = "NULL")
    void plannedPossessionsCannotContributePresentValue(
            AcquisitionType acquisition, String value, boolean valid) {
        AssetRequest asset =
                AssetRequest.builder()
                        .acquisitionType(acquisition)
                        .currentPrice(decimal(value))
                        .build();
        RealEstatePropertyRequest property =
                RealEstatePropertyRequest.builder()
                        .acquisitionType(acquisition)
                        .currentValue(decimal(value))
                        .build();
        assertThat(VALIDATOR.validateProperty(asset, "plannedValueValid").isEmpty())
                .isEqualTo(valid);
        assertThat(VALIDATOR.validateProperty(property, "plannedValueValid").isEmpty())
                .isEqualTo(valid);
        if (value == null) {
            assertThat(VALIDATOR.validateProperty(asset, "currentPrice")).isNotEmpty();
            assertThat(VALIDATOR.validateProperty(property, "currentValue")).isNotEmpty();
        }
    }

    @ParameterizedTest
    @CsvSource(
            value = {
                "PURCHASE,-1,true",
                "PURCHASE,0,true",
                "PURCHASE,1,false",
                "GIFT,1,false",
                "PLANNED,1,true",
                "NULL,1,false",
                "PURCHASE,NULL,true"
            },
            nullValues = "NULL")
    void onlyPlannedAcquisitionsMayBeFutureDated(
            AcquisitionType acquisition, Integer days, boolean valid) {
        AssetRequest asset =
                AssetRequest.builder()
                        .acquisitionType(acquisition)
                        .purchaseDate(date(days))
                        .build();
        RealEstatePropertyRequest property =
                RealEstatePropertyRequest.builder()
                        .acquisitionType(acquisition)
                        .purchaseDate(date(days))
                        .build();
        assertThat(VALIDATOR.validateProperty(asset, "acquisitionDateValid").isEmpty())
                .isEqualTo(valid);
        assertThat(VALIDATOR.validateProperty(property, "acquisitionDateValid").isEmpty())
                .isEqualTo(valid);
        if (days == null) {
            assertThat(VALIDATOR.validateProperty(asset, "purchaseDate")).isNotEmpty();
            assertThat(VALIDATOR.validateProperty(property, "purchaseDate")).isNotEmpty();
        }
    }

    @ParameterizedTest
    @CsvSource(
            value = {
                "PURCHASE,0,false",
                "PURCHASE,0.0001,true",
                "PURCHASE,-1,false",
                "PURCHASE,NULL,true",
                "GIFT,0,true",
                "PLANNED,0,true",
                "NULL,0,false"
            },
            nullValues = "NULL")
    void aPurchasedPropertyRequiresAPositiveAcquisitionPrice(
            AcquisitionType acquisition, String price, boolean valid) {
        RealEstatePropertyRequest request =
                RealEstatePropertyRequest.builder()
                        .acquisitionType(acquisition)
                        .purchasePrice(decimal(price))
                        .build();
        assertThat(VALIDATOR.validateProperty(request, "acquisitionPriceValid").isEmpty())
                .isEqualTo(valid);
    }

    @ParameterizedTest
    @EnumSource(
            value = AcquisitionType.class,
            names = {"GIFT", "PLANNED"})
    void zeroCostExceptionsStillRejectNegativePropertyPrices(AcquisitionType acquisition) {
        RealEstatePropertyRequest request =
                RealEstatePropertyRequest.builder()
                        .acquisitionType(acquisition)
                        .purchasePrice(new BigDecimal("-0.01"))
                        .build();
        assertThat(VALIDATOR.validateProperty(request, "purchasePrice")).isNotEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"omitted", "cleared", "provided"})
    void nullablePhysicalDetailsDistinguishOmittedValuesFromExplicitClears(String mode) {
        AssetRequest request = AssetRequest.builder().build();
        if (mode.equals("cleared")) {
            request.setCondition(null);
            request.setWarrantyExpiration(null);
            request.setUsefulLifeYears(null);
        } else if (mode.equals("provided")) {
            request =
                    AssetRequest.builder()
                            .condition(AssetCondition.GOOD)
                            .warrantyExpiration(LocalDate.now())
                            .usefulLifeYears(5)
                            .build();
        }
        assertThat(request.isConditionPresent()).isEqualTo(!mode.equals("omitted"));
        assertThat(request.isWarrantyExpirationPresent()).isEqualTo(!mode.equals("omitted"));
        assertThat(request.isUsefulLifeYearsPresent()).isEqualTo(!mode.equals("omitted"));
    }

    @ParameterizedTest
    @CsvSource(
            value = {
                "LOAN,0,false",
                "LOAN,-0.01,false",
                "LOAN,0.01,true",
                "LOAN,NULL,true",
                "CREDIT_CARD,0,true",
                "NULL,0,false"
            },
            nullValues = "NULL")
    void termDebtMustHavePositivePrincipalButRevolvingCreditMayStartAtZero(
            LiabilityType type, String principal, boolean valid) {
        LiabilityRequest request =
                LiabilityRequest.builder().type(type).principal(decimal(principal)).build();
        assertThat(VALIDATOR.validateProperty(request, "termPrincipalValid").isEmpty())
                .isEqualTo(valid);
        if (principal == null)
            assertThat(VALIDATOR.validateProperty(request, "principal")).isNotEmpty();
    }

    @ParameterizedTest
    @CsvSource(
            value = {"NULL,0,true", "0,NULL,true", "0,0,true", "-1,0,true", "0,-1,false"},
            nullValues = "NULL")
    void loanEndDatesAreInclusiveAndCannotPrecedeTheStart(
            Integer start, Integer end, boolean valid) {
        LiabilityRequest request =
                LiabilityRequest.builder().startDate(date(start)).endDate(date(end)).build();
        assertThat(VALIDATOR.validateProperty(request, "dateOrderValid").isEmpty())
                .isEqualTo(valid);
    }

    @ParameterizedTest
    @CsvSource(
            value = {
                "0,10,NULL,true",
                "NULL,NULL,0,true",
                "0,NULL,0,true",
                "NULL,10,10,true",
                "0,10,0,true",
                "0,10,10,true",
                "0,10,5,true",
                "0,10,-1,false",
                "0,10,11,false"
            },
            nullValues = "NULL")
    void amortizationMustStartWithinTheLoanTerm(
            Integer start, Integer end, Integer amortization, boolean valid) {
        LiabilityRequest request =
                LiabilityRequest.builder()
                        .startDate(date(start))
                        .endDate(date(end))
                        .amortizationStartDate(date(amortization))
                        .build();
        assertThat(VALIDATOR.validateProperty(request, "amortizationDateValid").isEmpty())
                .isEqualTo(valid);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "system",
                "children",
                "transaction",
                "split",
                "recurring",
                "payee",
                "budgets-only",
                "unused"
            })
    void onlyFinancialReferencesBlockDeletingAUserCategory(String dependency) {
        CategoryDeletionImpact impact =
                new CategoryDeletionImpact(
                        dependency.equals("system"),
                        dependency.equals("children"),
                        dependency.equals("transaction") ? 1 : 0,
                        dependency.equals("split") ? 1 : 0,
                        dependency.equals("recurring") ? 1 : 0,
                        dependency.equals("payee") ? 1 : 0,
                        dependency.equals("budgets-only") ? 2 : 0);
        assertThat(impact.isDeletable())
                .isEqualTo(dependency.equals("budgets-only") || dependency.equals("unused"));
    }

    @Test
    void importReviewCopiesCollectionsOnBothSidesOfTheRequestBoundary() {
        ImportReviewRequest request = new ImportReviewRequest();
        ImportedTransaction row = ImportedTransaction.builder().memo("Reviewed").build();
        List<ImportedTransaction> rows = new ArrayList<>(List.of(row));
        Map<String, Long> mappings = new HashMap<>(Map.of("Food", 3L));
        request.setTransactions(rows);
        request.setCategoryMappings(mappings);
        rows.clear();
        mappings.clear();
        request.getTransactions().clear();
        request.getCategoryMappings().clear();
        assertThat(request.getTransactions()).containsExactly(row);
        assertThat(request.getCategoryMappings()).containsExactlyEntriesOf(Map.of("Food", 3L));
        assertThat(request.isSkipDuplicates()).isTrue();
    }

    @Test
    void aNullReviewPayloadRemainsInvalidInsteadOfBecomingAnEmptyConfirmation() {
        ImportReviewRequest request = new ImportReviewRequest();
        request.setTransactions(null);
        request.setCategoryMappings(null);
        assertThat(request.getTransactions()).isNull();
        assertThat(request.getCategoryMappings()).isNull();
        assertThat(VALIDATOR.validate(request))
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactlyInAnyOrder("transactions", "categoryMappings");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"new-Password-Do-Not-Log!"})
    void profileLoggingNeverExposesEitherPassword(String password) {
        UpdateProfileRequest request =
                UpdateProfileRequest.builder()
                        .email("person@example.com")
                        .currentPassword("current-Do-Not-Log!")
                        .newPassword(password)
                        .build();
        assertThat(request.toString())
                .doesNotContain("current-Do-Not-Log!", "new-Password-Do-Not-Log!")
                .contains(
                        "person@example.com",
                        "hasNewPassword=" + (password != null && !password.isEmpty()));
    }
}
