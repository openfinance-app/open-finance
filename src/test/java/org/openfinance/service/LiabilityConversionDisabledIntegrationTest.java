package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = "application.import.skrooge-json-enabled=false")
class LiabilityConversionDisabledIntegrationTest extends AuditApiTestSupport {
    @Test
    void disablesConversionWithoutDisablingManagedLiabilities() throws Exception {
        json("GET", "/liabilities/conversion/candidates", null, owner, 404);
        json("GET", "/liabilities/conversion/1/payments", null, owner, 404);
        json(
                "POST",
                "/liabilities/conversion",
                data(
                        "operationId",
                        UUID.randomUUID(),
                        "accountId",
                        1,
                        "sourceRevision",
                        "a".repeat(64),
                        "fundedPrincipal",
                        1000,
                        "liability",
                        data(
                                "name",
                                "Loan",
                                "type",
                                "LOAN",
                                "currency",
                                "EUR",
                                "principal",
                                1000,
                                "currentBalance",
                                1000,
                                "startDate",
                                START)),
                owner,
                404);
        assertThat(loan(START)).isPositive();
    }
}
