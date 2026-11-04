package org.openfinance.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.openfinance.exception.GlobalExceptionHandler.ErrorResponse;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;

@DisplayName("Domain failures retain their HTTP status and safe localized response")
class ErrorResponseContractTest {
    private final ResourceBundleMessageSource messages = messages();
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler(messages);
    private final ServletWebRequest request =
            new ServletWebRequest(new MockHttpServletRequest("POST", "/api/v1/test"));

    @AfterEach
    void resetLocale() {
        LocaleContextHolder.resetLocaleContext();
    }

    @ParameterizedTest
    @MethodSource("locales")
    void serviceAndConcurrencyFailuresUseTheRequestLocale(Locale locale) {
        LocaleContextHolder.setLocale(locale);
        assertResponse(
                handler.handleAIProviderFailure(request),
                503,
                messages.getMessage("ai.provider.unavailable", null, locale));
        assertResponse(
                handler.handleConcurrentUpdate(request),
                409,
                messages.getMessage("error.concurrentUpdate", null, locale));
    }

    static Stream<Locale> locales() {
        return Stream.of(Locale.ENGLISH, Locale.FRENCH);
    }

    @Test
    void validationErrorsIdentifyTheInvalidFieldWithoutExposingACause() {
        CalculationValidationException failure =
                new CalculationValidationException("Invalid duration", "years");
        ResponseEntity<ErrorResponse> response =
                handler.handleCalculationValidationException(failure, request);
        assertResponse(response, 400, "Invalid duration");
        assertThat(response.getBody().getValidationErrors())
                .containsEntry("years", "Invalid duration");
    }

    @Test
    void fieldlessValidationStillReturnsAUsableError() {
        ResponseEntity<ErrorResponse> response =
                handler.handleCalculationValidationException(
                        new CalculationValidationException("Invalid calculation"), request);
        assertResponse(response, 400, "Invalid calculation");
        assertThat(response.getBody().getValidationErrors()).isEmpty();
    }

    @Test
    void mathematicalFailuresAreClientErrors() {
        assertResponse(
                handler.handleCalculationOverflowException(
                        new CalculationOverflowException("Result too large", "projection"),
                        request),
                400,
                "Result too large");
        assertResponse(
                handler.handleCalculationException(
                        new CalculationException("Cannot calculate"), request),
                400,
                "Cannot calculate");
    }

    @Test
    void duplicateSimulationAndPayeeFailuresAreConflicts() {
        assertResponse(
                handler.handleDuplicateSimulation(
                        new DuplicateSimulationException("Simulation already exists"), request),
                409,
                "Simulation already exists");
        assertResponse(
                handler.handleDuplicatePayee(new DuplicatePayeeException("Acme"), request),
                409,
                messages.getMessage(
                        "error.payee.duplicate",
                        new Object[] {"Acme"},
                        LocaleContextHolder.getLocale()));
    }

    private void assertResponse(
            ResponseEntity<ErrorResponse> response, int status, String message) {
        assertThat(response.getStatusCode().value()).isEqualTo(status);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(status);
        assertThat(response.getBody().getMessage()).isEqualTo(message);
        assertThat(response.getBody().getPath()).isEqualTo("/api/v1/test");
        assertThat(response.getBody().getTimestamp()).isNotNull();
    }

    private static ResourceBundleMessageSource messages() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasename("i18n/messages");
        source.setDefaultEncoding("UTF-8");
        return source;
    }
}
