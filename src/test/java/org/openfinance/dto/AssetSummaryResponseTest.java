package org.openfinance.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AssetSummaryResponseTest {

    @Test
    @DisplayName("should build AssetSummaryResponse correctly")
    void shouldBuildAssetSummaryResponse() {
        // Given
        Long id = 1L;
        String name = "Apple Inc.";
        String symbol = "AAPL";
        String type = "STOCK";
        String currency = "USD";
        BigDecimal quantity = new BigDecimal("10.0");
        BigDecimal currentPrice = new BigDecimal("150.0");
        BigDecimal totalValue = new BigDecimal("1500.0");

        // When
        AssetSummaryResponse response =
                AssetSummaryResponse.builder()
                        .id(id)
                        .name(name)
                        .symbol(symbol)
                        .type(type)
                        .currency(currency)
                        .quantity(quantity)
                        .currentPrice(currentPrice)
                        .totalValue(totalValue)
                        .build();

        // Then
        assertThat(response.getId()).isEqualTo(id);
        assertThat(response.getName()).isEqualTo(name);
        assertThat(response.getSymbol()).isEqualTo(symbol);
        assertThat(response.getType()).isEqualTo(type);
        assertThat(response.getCurrency()).isEqualTo(currency);
        assertThat(response.getQuantity()).isEqualTo(quantity);
        assertThat(response.getCurrentPrice()).isEqualTo(currentPrice);
        assertThat(response.getTotalValue()).isEqualTo(totalValue);
    }
}
