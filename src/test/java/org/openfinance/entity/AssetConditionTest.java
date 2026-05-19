package org.openfinance.entity;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for AssetCondition enum.
 *
 * <p>Tests enum values, display names, value retention factors, descriptions, and business logic
 * for physical asset condition assessment.
 *
 * <p>Requirements: Physical Asset Tracking - Asset condition affects depreciation and current
 * market value estimation.
 */
@DisplayName("AssetCondition Enum Tests")
class AssetConditionTest {

    // ========== ENUM VALUES TESTS ==========

    @Test
    @DisplayName("Should have all 5 required condition types")
    void shouldHaveAllRequiredConditionTypes() {
        AssetCondition[] conditions = AssetCondition.values();

        assertThat(conditions).hasSize(5);
        assertThat(conditions)
                .contains(
                        AssetCondition.NEW,
                        AssetCondition.EXCELLENT,
                        AssetCondition.GOOD,
                        AssetCondition.FAIR,
                        AssetCondition.POOR);
    }

    @Test
    @DisplayName("Should convert enum to string correctly")
    void shouldConvertEnumToString() {
        assertThat(AssetCondition.NEW.toString()).isEqualTo("NEW");
        assertThat(AssetCondition.EXCELLENT.toString()).isEqualTo("EXCELLENT");
        assertThat(AssetCondition.GOOD.toString()).isEqualTo("GOOD");
        assertThat(AssetCondition.FAIR.toString()).isEqualTo("FAIR");
        assertThat(AssetCondition.POOR.toString()).isEqualTo("POOR");
    }

    @Test
    @DisplayName("Should parse string to enum correctly")
    void shouldParseStringToEnum() {
        assertThat(AssetCondition.valueOf("NEW")).isEqualTo(AssetCondition.NEW);
        assertThat(AssetCondition.valueOf("EXCELLENT")).isEqualTo(AssetCondition.EXCELLENT);
        assertThat(AssetCondition.valueOf("GOOD")).isEqualTo(AssetCondition.GOOD);
        assertThat(AssetCondition.valueOf("FAIR")).isEqualTo(AssetCondition.FAIR);
        assertThat(AssetCondition.valueOf("POOR")).isEqualTo(AssetCondition.POOR);
    }

    @Test
    @DisplayName("Should throw exception for invalid enum value")
    void shouldThrowExceptionForInvalidValue() {
        assertThatThrownBy(() -> AssetCondition.valueOf("INVALID"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Should throw exception for null enum value")
    void shouldThrowExceptionForNullValue() {
        assertThatThrownBy(() -> AssetCondition.valueOf(null))
                .isInstanceOf(NullPointerException.class);
    }

    // ========== DISPLAY NAME TESTS ==========

    @Test
    @DisplayName("Should return correct display name for NEW")
    void shouldReturnDisplayNameForNew() {
        assertThat(AssetCondition.NEW.getDisplayName()).isEqualTo("New");
    }

    @Test
    @DisplayName("Should return correct display name for EXCELLENT")
    void shouldReturnDisplayNameForExcellent() {
        assertThat(AssetCondition.EXCELLENT.getDisplayName()).isEqualTo("Excellent");
    }

    @Test
    @DisplayName("Should return correct display name for GOOD")
    void shouldReturnDisplayNameForGood() {
        assertThat(AssetCondition.GOOD.getDisplayName()).isEqualTo("Good");
    }

    @Test
    @DisplayName("Should return correct display name for FAIR")
    void shouldReturnDisplayNameForFair() {
        assertThat(AssetCondition.FAIR.getDisplayName()).isEqualTo("Fair");
    }

    @Test
    @DisplayName("Should return correct display name for POOR")
    void shouldReturnDisplayNameForPoor() {
        assertThat(AssetCondition.POOR.getDisplayName()).isEqualTo("Poor");
    }

    @Test
    @DisplayName("Should return non-null display names for all conditions")
    void shouldReturnNonNullDisplayNames() {
        for (AssetCondition condition : AssetCondition.values()) {
            assertThat(condition.getDisplayName()).isNotNull().isNotEmpty();
        }
    }

    // ========== VALUE RETENTION FACTOR TESTS ==========

    @Test
    @DisplayName("Should return 0.95 retention factor for NEW condition")
    void shouldReturnCorrectRetentionFactorForNew() {
        assertThat(AssetCondition.NEW.getValueRetentionFactor()).isEqualTo(0.95);
    }

    @Test
    @DisplayName("Should return 0.82 retention factor for EXCELLENT condition")
    void shouldReturnCorrectRetentionFactorForExcellent() {
        assertThat(AssetCondition.EXCELLENT.getValueRetentionFactor()).isEqualTo(0.82);
    }

    @Test
    @DisplayName("Should return 0.62 retention factor for GOOD condition")
    void shouldReturnCorrectRetentionFactorForGood() {
        assertThat(AssetCondition.GOOD.getValueRetentionFactor()).isEqualTo(0.62);
    }

    @Test
    @DisplayName("Should return 0.37 retention factor for FAIR condition")
    void shouldReturnCorrectRetentionFactorForFair() {
        assertThat(AssetCondition.FAIR.getValueRetentionFactor()).isEqualTo(0.37);
    }

    @Test
    @DisplayName("Should return 0.12 retention factor for POOR condition")
    void shouldReturnCorrectRetentionFactorForPoor() {
        assertThat(AssetCondition.POOR.getValueRetentionFactor()).isEqualTo(0.12);
    }

    @Test
    @DisplayName("Should return retention factors in descending order")
    void shouldReturnRetentionFactorsInDescendingOrder() {
        double newFactor = AssetCondition.NEW.getValueRetentionFactor();
        double excellentFactor = AssetCondition.EXCELLENT.getValueRetentionFactor();
        double goodFactor = AssetCondition.GOOD.getValueRetentionFactor();
        double fairFactor = AssetCondition.FAIR.getValueRetentionFactor();
        double poorFactor = AssetCondition.POOR.getValueRetentionFactor();

        assertThat(newFactor).isGreaterThan(excellentFactor);
        assertThat(excellentFactor).isGreaterThan(goodFactor);
        assertThat(goodFactor).isGreaterThan(fairFactor);
        assertThat(fairFactor).isGreaterThan(poorFactor);
    }

    @Test
    @DisplayName("Should return retention factors between 0 and 1")
    void shouldReturnRetentionFactorsBetweenZeroAndOne() {
        for (AssetCondition condition : AssetCondition.values()) {
            double factor = condition.getValueRetentionFactor();
            assertThat(factor).isGreaterThanOrEqualTo(0.0).isLessThanOrEqualTo(1.0);
        }
    }

    // ========== DESCRIPTION TESTS ==========

    @Test
    @DisplayName("Should return non-null description for NEW condition")
    void shouldReturnDescriptionForNew() {
        assertThat(AssetCondition.NEW.getDescription())
                .isNotNull()
                .isNotEmpty()
                .contains("Brand new");
    }

    @Test
    @DisplayName("Should return non-null description for EXCELLENT condition")
    void shouldReturnDescriptionForExcellent() {
        assertThat(AssetCondition.EXCELLENT.getDescription())
                .isNotNull()
                .isNotEmpty()
                .contains("Like new");
    }

    @Test
    @DisplayName("Should return non-null description for GOOD condition")
    void shouldReturnDescriptionForGood() {
        assertThat(AssetCondition.GOOD.getDescription())
                .isNotNull()
                .isNotEmpty()
                .contains("Normal wear");
    }

    @Test
    @DisplayName("Should return non-null description for FAIR condition")
    void shouldReturnDescriptionForFair() {
        assertThat(AssetCondition.FAIR.getDescription())
                .isNotNull()
                .isNotEmpty()
                .contains("Noticeable wear");
    }

    @Test
    @DisplayName("Should return non-null description for POOR condition")
    void shouldReturnDescriptionForPoor() {
        assertThat(AssetCondition.POOR.getDescription())
                .isNotNull()
                .isNotEmpty()
                .contains("Heavy wear");
    }

    @Test
    @DisplayName("Should return non-null descriptions for all conditions")
    void shouldReturnNonNullDescriptionsForAllConditions() {
        for (AssetCondition condition : AssetCondition.values()) {
            assertThat(condition.getDescription())
                    .isNotNull()
                    .isNotEmpty()
                    .hasSizeGreaterThan(10); // Ensure meaningful description
        }
    }

    @Test
    @DisplayName("Should return unique descriptions for each condition")
    void shouldReturnUniqueDescriptionsForEachCondition() {
        String newDesc = AssetCondition.NEW.getDescription();
        String excellentDesc = AssetCondition.EXCELLENT.getDescription();
        String goodDesc = AssetCondition.GOOD.getDescription();
        String fairDesc = AssetCondition.FAIR.getDescription();
        String poorDesc = AssetCondition.POOR.getDescription();

        // All descriptions should be different
        assertThat(newDesc).isNotEqualTo(excellentDesc);
        assertThat(newDesc).isNotEqualTo(goodDesc);
        assertThat(excellentDesc).isNotEqualTo(goodDesc);
        assertThat(goodDesc).isNotEqualTo(fairDesc);
        assertThat(fairDesc).isNotEqualTo(poorDesc);
    }
}
