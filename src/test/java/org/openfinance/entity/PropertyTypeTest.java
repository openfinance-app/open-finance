package org.openfinance.entity;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for PropertyType enum.
 *
 * <p>Tests enum values, helper methods, and business logic for property classifications.
 *
 * <p>Requirements: REQ-2.16.1 (Property Type Management)
 */
@DisplayName("PropertyType Enum Tests")
class PropertyTypeTest {

    // ========== ENUM VALUES TESTS ==========

    @Test
    @DisplayName("Should have all required property types")
    void shouldHaveAllRequiredPropertyTypes() {
        PropertyType[] types = PropertyType.values();

        assertThat(types).hasSize(6);
        assertThat(types)
                .contains(
                        PropertyType.RESIDENTIAL,
                        PropertyType.COMMERCIAL,
                        PropertyType.LAND,
                        PropertyType.MIXED_USE,
                        PropertyType.INDUSTRIAL,
                        PropertyType.OTHER);
    }

    @Test
    @DisplayName("Should convert enum to string correctly")
    void shouldConvertEnumToString() {
        assertThat(PropertyType.RESIDENTIAL.toString()).isEqualTo("RESIDENTIAL");
        assertThat(PropertyType.COMMERCIAL.toString()).isEqualTo("COMMERCIAL");
        assertThat(PropertyType.LAND.toString()).isEqualTo("LAND");
        assertThat(PropertyType.MIXED_USE.toString()).isEqualTo("MIXED_USE");
        assertThat(PropertyType.INDUSTRIAL.toString()).isEqualTo("INDUSTRIAL");
        assertThat(PropertyType.OTHER.toString()).isEqualTo("OTHER");
    }

    @Test
    @DisplayName("Should parse string to enum correctly")
    void shouldParseStringToEnum() {
        assertThat(PropertyType.valueOf("RESIDENTIAL")).isEqualTo(PropertyType.RESIDENTIAL);
        assertThat(PropertyType.valueOf("COMMERCIAL")).isEqualTo(PropertyType.COMMERCIAL);
        assertThat(PropertyType.valueOf("LAND")).isEqualTo(PropertyType.LAND);
        assertThat(PropertyType.valueOf("MIXED_USE")).isEqualTo(PropertyType.MIXED_USE);
        assertThat(PropertyType.valueOf("INDUSTRIAL")).isEqualTo(PropertyType.INDUSTRIAL);
        assertThat(PropertyType.valueOf("OTHER")).isEqualTo(PropertyType.OTHER);
    }

    @Test
    @DisplayName("Should throw exception for invalid enum value")
    void shouldThrowExceptionForInvalidValue() {
        assertThatThrownBy(() -> PropertyType.valueOf("INVALID"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ========== DISPLAY NAME TESTS ==========

    @Test
    @DisplayName("Should return correct display name for RESIDENTIAL")
    void shouldReturnDisplayNameForResidential() {
        assertThat(PropertyType.RESIDENTIAL.getDisplayName()).isEqualTo("Residential");
    }

    @Test
    @DisplayName("Should return correct display name for COMMERCIAL")
    void shouldReturnDisplayNameForCommercial() {
        assertThat(PropertyType.COMMERCIAL.getDisplayName()).isEqualTo("Commercial");
    }

    @Test
    @DisplayName("Should return correct display name for LAND")
    void shouldReturnDisplayNameForLand() {
        assertThat(PropertyType.LAND.getDisplayName()).isEqualTo("Land");
    }

    @Test
    @DisplayName("Should return correct display name for MIXED_USE")
    void shouldReturnDisplayNameForMixedUse() {
        assertThat(PropertyType.MIXED_USE.getDisplayName()).isEqualTo("Mixed-Use");
    }

    @Test
    @DisplayName("Should return correct display name for INDUSTRIAL")
    void shouldReturnDisplayNameForIndustrial() {
        assertThat(PropertyType.INDUSTRIAL.getDisplayName()).isEqualTo("Industrial");
    }

    @Test
    @DisplayName("Should return correct display name for OTHER")
    void shouldReturnDisplayNameForOther() {
        assertThat(PropertyType.OTHER.getDisplayName()).isEqualTo("Other");
    }

    // ========== INCOME GENERATING TESTS ==========

    @Test
    @DisplayName("Should identify income-generating property types")
    void shouldIdentifyIncomeGeneratingTypes() {
        assertThat(PropertyType.COMMERCIAL.isIncomeGenerating()).isTrue();
        assertThat(PropertyType.MIXED_USE.isIncomeGenerating()).isTrue();
        assertThat(PropertyType.INDUSTRIAL.isIncomeGenerating()).isTrue();
    }

    @Test
    @DisplayName("Should identify non-income-generating property types")
    void shouldIdentifyNonIncomeGeneratingTypes() {
        assertThat(PropertyType.RESIDENTIAL.isIncomeGenerating()).isFalse();
        assertThat(PropertyType.LAND.isIncomeGenerating()).isFalse();
        assertThat(PropertyType.OTHER.isIncomeGenerating()).isFalse();
    }

    // ========== RESIDENTIAL COMPONENT TESTS ==========

    @Test
    @DisplayName("Should identify property types with residential components")
    void shouldIdentifyTypesWithResidentialComponent() {
        assertThat(PropertyType.RESIDENTIAL.hasResidentialComponent()).isTrue();
        assertThat(PropertyType.MIXED_USE.hasResidentialComponent()).isTrue();
    }

    @Test
    @DisplayName("Should identify property types without residential components")
    void shouldIdentifyTypesWithoutResidentialComponent() {
        assertThat(PropertyType.COMMERCIAL.hasResidentialComponent()).isFalse();
        assertThat(PropertyType.LAND.hasResidentialComponent()).isFalse();
        assertThat(PropertyType.INDUSTRIAL.hasResidentialComponent()).isFalse();
        assertThat(PropertyType.OTHER.hasResidentialComponent()).isFalse();
    }

    // ========== BUSINESS LOGIC TESTS ==========

    @Test
    @DisplayName("Should correctly classify residential rental properties")
    void shouldClassifyResidentialRentalProperties() {
        PropertyType type = PropertyType.RESIDENTIAL;

        // Note: RESIDENTIAL is NOT marked as income-generating in the enum (only COMMERCIAL,
        // INDUSTRIAL, MIXED_USE are)
        assertThat(type.isIncomeGenerating()).isFalse();
        assertThat(type.hasResidentialComponent()).isTrue();
        assertThat(type.getDisplayName()).isEqualTo("Residential");
    }

    @Test
    @DisplayName("Should correctly classify commercial properties")
    void shouldClassifyCommercialProperties() {
        PropertyType type = PropertyType.COMMERCIAL;

        assertThat(type.isIncomeGenerating()).isTrue();
        assertThat(type.hasResidentialComponent()).isFalse();
        assertThat(type.getDisplayName()).isEqualTo("Commercial");
    }

    @Test
    @DisplayName("Should correctly classify vacant land")
    void shouldClassifyVacantLand() {
        PropertyType type = PropertyType.LAND;

        assertThat(type.isIncomeGenerating()).isFalse();
        assertThat(type.hasResidentialComponent()).isFalse();
        assertThat(type.getDisplayName()).isEqualTo("Land");
    }

    @Test
    @DisplayName("Should correctly classify mixed-use properties")
    void shouldClassifyMixedUseProperties() {
        PropertyType type = PropertyType.MIXED_USE;

        assertThat(type.isIncomeGenerating()).isTrue();
        assertThat(type.hasResidentialComponent()).isTrue();
        assertThat(type.getDisplayName()).isEqualTo("Mixed-Use");
    }
}
