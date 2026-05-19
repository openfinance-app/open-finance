package org.openfinance.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PerformanceMonitoringAspectTest {

    @Mock private ProceedingJoinPoint joinPoint;

    @Mock private Signature signature;

    @InjectMocks private PerformanceMonitoringAspect aspect;

    @BeforeEach
    void setUp() {
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.toShortString()).thenReturn("TestClass.testMethod()");
    }

    @Test
    @DisplayName("should not throw exception when operation is fast")
    void shouldNotThrowWhenOperationIsFast() throws Throwable {
        // Given
        when(joinPoint.proceed()).thenReturn("success");

        // When
        Object result = aspect.monitorServiceMethod(joinPoint);

        // Then
        assertThat(result).isEqualTo("success");
    }

    @Test
    @DisplayName("should not throw exception when operation is slow")
    void shouldNotThrowWhenOperationIsSlow() throws Throwable {
        // Given
        when(joinPoint.proceed())
                .thenAnswer(
                        invocation -> {
                            Thread.sleep(600); // Exceed WARN_THRESHOLD_MS (500)
                            return "slow success";
                        });

        // When
        Object result = aspect.monitorServiceMethod(joinPoint);

        // Then
        assertThat(result).isEqualTo("slow success");
    }
}
