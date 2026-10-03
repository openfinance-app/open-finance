package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openfinance.dto.RealEstateSimulationRequest;
import org.openfinance.entity.RealEstateSimulation;
import org.openfinance.exception.InvalidSimulationException;
import org.openfinance.exception.ResourceNotFoundException;
import org.openfinance.repository.RealEstateSimulationRepository;

@ExtendWith(MockitoExtension.class)
class RealEstateSimulationServiceTest {
    @Mock RealEstateSimulationRepository repository;
    private final ObjectMapper mapper = new ObjectMapper();
    private RealEstateSimulationService service;

    @BeforeEach
    void setup() {
        service =
                new RealEstateSimulationService(
                        repository, new RealEstateSimulationValidator(mapper));
    }

    @Test
    void rejectsMalformedIncompleteMismatchedAndNonFiniteDataBeforeSaving() throws Exception {
        String valid = fixture("buy-rent");
        for (String invalid :
                List.of(
                        "{",
                        "null",
                        "[]",
                        "{}",
                        "{} {}",
                        "{\"purchase\":{},\"rental\":{}}",
                        fixture("rental-investment"),
                        valid.replace("300000", "\"300000\""),
                        valid.replace("300000", "1e999"))) {
            assertThatThrownBy(() -> service.createSimulation(1L, request("buy_rent", invalid)))
                    .as(invalid)
                    .isInstanceOf(InvalidSimulationException.class);
        }
        verify(repository, never()).save(any());
    }

    @Test
    void acceptsBothInputShapesIncludingLegacySavesWithoutCurrencyOrTaxContext() throws Exception {
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        for (String type : List.of("buy_rent", "rental_investment")) {
            String data = fixture(type.equals("buy_rent") ? "buy-rent" : "rental-investment");
            assertThat(service.createSimulation(1L, request(type, data)).getData()).isEqualTo(data);
        }
        ObjectNode rental = (ObjectNode) mapper.readTree(fixture("rental-investment"));
        rental.put("currency", "EUR");
        rental.putObject("tax")
                .put("incomeYear", 2026)
                .putNull("otherHouseholdIncome")
                .put("otherFurnishedReceipts", 0)
                .put("otherUnfurnishedRent", 0);
        assertThat(
                        service.createSimulation(
                                        1L, request("rental_investment", rental.toString()))
                                .getData())
                .isEqualTo(rental.toString());
    }

    @Test
    void invalidUpdateLeavesTheExistingSimulationIntactAndChecksOwnershipFirst() throws Exception {
        RealEstateSimulation existing =
                RealEstateSimulation.builder()
                        .id(10L)
                        .userId(1L)
                        .name("Original")
                        .simulationType("buy_rent")
                        .data(fixture("buy-rent"))
                        .build();
        when(repository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(existing));
        assertThatThrownBy(() -> service.updateSimulation(10L, 1L, request("buy_rent", "{")))
                .isInstanceOf(InvalidSimulationException.class);
        assertThat(existing.getName()).isEqualTo("Original");
        assertThat(existing.getData()).isEqualTo(fixture("buy-rent"));
        assertThatThrownBy(() -> service.updateSimulation(10L, 2L, request("buy_rent", "{")))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(repository, never()).save(any());
    }

    private RealEstateSimulationRequest request(String type, String data) {
        return RealEstateSimulationRequest.builder()
                .name("Saved inputs")
                .simulationType(type)
                .data(data)
                .build();
    }

    private String fixture(String name) throws Exception {
        return Files.readString(
                Path.of("src/test/resources/fixtures/" + name + "-simulation.json"));
    }
}
