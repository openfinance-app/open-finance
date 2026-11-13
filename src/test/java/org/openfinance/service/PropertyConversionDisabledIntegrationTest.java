package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = "application.import.skrooge-json-enabled=false")
class PropertyConversionDisabledIntegrationTest extends AuditApiTestSupport {
    @Test
    void disablesConversionEndpointsWithoutDisablingPropertyManagement() throws Exception {
        assertThat(
                        json("GET", "/config/import", null, owner, 200)
                                .path("skroogeJsonEnabled")
                                .asBoolean())
                .isFalse();
        json("GET", "/real-estate/conversion/candidates", null, owner, 404);
        json(
                "POST",
                "/real-estate/conversion",
                data(
                        "operationId",
                        UUID.randomUUID().toString(),
                        "assetId",
                        1,
                        "sourceRevision",
                        "a".repeat(64),
                        "name",
                        "Imported home",
                        "address",
                        "10 Synthetic Street",
                        "propertyType",
                        "RESIDENTIAL"),
                owner,
                404);
        assertThat(json("GET", "/real-estate", null, owner, 200)).isEmpty();
    }
}
