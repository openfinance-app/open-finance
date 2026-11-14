package org.openfinance.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openfinance.dto.NotificationResponse;
import org.openfinance.service.ExchangeRateService;
import org.openfinance.service.NotificationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

/** Delegation contract of the two smallest controllers (no dedicated suites existed). */
@DisplayName("Health and Notification controllers")
class HealthNotificationControllerTest {

    @Test
    @DisplayName("Health reports UP with application identity")
    void healthReportsUp() {
        ResponseEntity<Map<String, Object>> response = healthController().health();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("status")).isEqualTo("UP");
        assertThat(response.getBody().get("application")).isEqualTo("open-finance");
        assertThat(response.getBody().get("version")).isEqualTo("0.5.17-beta");
        assertThat(response.getBody()).containsKeys("timestamp", "java");
    }

    @Test
    @DisplayName("Info nests application, runtime and memory sections")
    void infoSections() {
        ResponseEntity<Map<String, Object>> response = healthController().info();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body).containsKeys("application", "runtime", "memory", "timestamp");
        @SuppressWarnings("unchecked")
        Map<String, Object> application = (Map<String, Object>) body.get("application");
        assertThat(application)
                .containsEntry("name", "open-finance")
                .containsEntry("version", "0.5.17-beta");
        @SuppressWarnings("unchecked")
        Map<String, Object> memory = (Map<String, Object>) body.get("memory");
        assertThat(memory)
                .containsKeys("totalMemory", "freeMemory", "maxMemory", "availableProcessors");
    }

    @Test
    @DisplayName("Notifications are fetched for the authenticated user only")
    void notificationsAreUserScoped() {
        NotificationService service = mock(NotificationService.class);
        List<NotificationResponse> expected = List.of(mock(NotificationResponse.class));
        when(service.getNotifications(42L)).thenReturn(expected);
        NotificationController controller =
                new NotificationController(service, mock(ExchangeRateService.class));

        ResponseEntity<List<NotificationResponse>> response = controller.getNotifications(42L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(expected);
        verify(service).getNotifications(42L);
    }

    @Test
    @DisplayName("Count endpoint agrees with the list endpoint, including zero")
    void notificationCount() {
        NotificationService service = mock(NotificationService.class);
        when(service.getNotifications(7L))
                .thenReturn(
                        List.of(
                                mock(NotificationResponse.class),
                                mock(NotificationResponse.class),
                                mock(NotificationResponse.class)));
        when(service.getNotifications(8L)).thenReturn(List.of());
        NotificationController controller =
                new NotificationController(service, mock(ExchangeRateService.class));

        assertThat(controller.getNotificationCount(7L).getBody()).isEqualTo(3);
        assertThat(controller.getNotificationCount(8L).getBody()).isEqualTo(0);
    }

    @Test
    @DisplayName("Exchange-rate action delegates and reports how many rates were stored")
    void exchangeRateAction() {
        ExchangeRateService exchangeRates = mock(ExchangeRateService.class);
        when(exchangeRates.updateExchangeRates()).thenReturn(17);
        NotificationController controller =
                new NotificationController(mock(NotificationService.class), exchangeRates);

        ResponseEntity<Map<String, Object>> response = controller.triggerExchangeRateUpdate();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .containsEntry("updatedCount", 17)
                .containsEntry("message", "Exchange rates updated successfully");
        verify(exchangeRates).updateExchangeRates();
    }

    private HealthController healthController() {
        HealthController controller = new HealthController();
        ReflectionTestUtils.setField(controller, "applicationName", "open-finance");
        ReflectionTestUtils.setField(controller, "applicationVersion", "0.5.17-beta");
        return controller;
    }
}
