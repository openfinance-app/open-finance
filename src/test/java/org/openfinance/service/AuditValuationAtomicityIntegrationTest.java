package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.SpyBean;

class AuditValuationAtomicityIntegrationTest extends AuditApiTestSupport {
    @SpyBean private AssetService assetService;

    @Test
    void backingAssetFailureRollsBackEstimateAndHistory() throws Exception {
        JsonNode property = property(owner);
        long id = property.path("id").asLong();
        long asset = property.path("assetId").asLong();
        int before =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM real_estate_value_history WHERE property_id = ?",
                        Integer.class,
                        id);
        doThrow(new RuntimeException("Simulated backing asset failure"))
                .when(assetService)
                .updatePropertyAsset(eq(asset), eq(owner.id()), any());
        json("PUT", "/real-estate/" + id + "/value", 1500, owner, 500);
        money(json("GET", "/real-estate/" + id, null, owner, 200), "currentValue", "1000");
        money(json("GET", "/assets/" + asset, null, owner, 200), "currentPrice", "1000");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM real_estate_value_history WHERE property_id = ?",
                                Integer.class,
                                id))
                .isEqualTo(before);
    }
}
