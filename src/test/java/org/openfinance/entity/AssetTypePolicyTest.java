package org.openfinance.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("Asset categories select the correct pricing and depreciation policies")
class AssetTypePolicyTest {
    @ParameterizedTest
    @CsvSource({
        "STOCK,Stock,true,true,false,false,",
        "ETF,ETF,true,true,false,false,",
        "MUTUAL_FUND,Mutual Fund,true,false,false,false,",
        "BOND,Bond,true,false,false,false,",
        "CRYPTO,Cryptocurrency,false,true,false,false,",
        "COMMODITY,Commodity,false,false,false,false,",
        "REAL_ESTATE,Real Estate,false,false,false,false,",
        "VEHICLE,Vehicle,false,false,true,true,10",
        "JEWELRY,Jewelry,false,false,true,false,",
        "COLLECTIBLE,Collectible,false,false,true,false,",
        "ELECTRONICS,Electronics,false,false,true,true,5",
        "FURNITURE,Furniture,false,false,true,true,10",
        "OTHER,Other,false,false,false,false,"
    })
    void classifiesPricingAndUsefulLife(
            AssetType type,
            String label,
            boolean security,
            boolean realTime,
            boolean physical,
            boolean depreciates,
            Integer life) {
        assertThat(type.getDisplayName()).isEqualTo(label);
        assertThat(type.isSecurity()).isEqualTo(security);
        assertThat(type.hasRealTimeData()).isEqualTo(realTime);
        assertThat(type.isPhysical()).isEqualTo(physical);
        assertThat(type.isDepreciating()).isEqualTo(depreciates);
        assertThat(type.getDefaultUsefulLifeYears()).isEqualTo(life);
    }
}
