package org.openfinance.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

@ExtendWith(MockitoExtension.class)
@DisplayName("Logging advice preserves execution and protects credential arguments")
class LoggingAspectExecutionTest {

    @Mock private ProceedingJoinPoint joinPoint;
    @Mock private MethodSignature signature;

    private final LoggingAspect aspect = new LoggingAspect();
    private final Logger logger = (Logger) LoggerFactory.getLogger(LoggingAspect.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private Level previousLevel;

    @BeforeEach
    void captureLogs() {
        previousLevel = logger.getLevel();
        logger.setLevel(Level.DEBUG);
        appender.start();
        logger.addAppender(appender);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getDeclaringTypeName()).thenReturn("org.openfinance.service.BackupService");
        when(signature.getName()).thenReturn("restoreBackup");
        lenient().when(signature.getParameterNames()).thenReturn(new String[0]);
    }

    @AfterEach
    void restoreLogging() {
        logger.detachAppender(appender);
        appender.stop();
        logger.setLevel(previousLevel);
    }

    @ParameterizedTest
    @ValueSource(strings = {"SERVICE", "CONTROLLER", "LOGGED"})
    @DisplayName("Every advice invokes the method once and returns its original result")
    void preservesResultAndIdentifiesLayer(String layer) throws Throwable {
        Object result = new Object();
        when(joinPoint.getArgs()).thenReturn(new Object[] {42L});
        when(joinPoint.proceed()).thenReturn(result);

        Object actual =
                switch (layer) {
                    case "SERVICE" -> aspect.logServiceMethods(joinPoint);
                    case "CONTROLLER" -> aspect.logControllerMethods(joinPoint);
                    default -> aspect.logAnnotatedMethods(joinPoint);
                };

        assertThat(actual).isSameAs(result);
        verify(joinPoint).proceed();
        assertThat(appender.list)
                .extracting(ILoggingEvent::getFormattedMessage)
                .anySatisfy(
                        message ->
                                assertThat(message).contains("[" + layer + "] Entering:", "[42]"))
                .anySatisfy(message -> assertThat(message).contains("[" + layer + "] Exiting:"));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {"masterPassword", "password", "apiKey", "encryptionKey", "sessionToken"})
    @DisplayName("Sensitive parameter names protect opaque values without credential keywords")
    void redactsOpaqueCredentialByParameterName(String parameterName) throws Throwable {
        when(signature.getParameterNames()).thenReturn(new String[] {"userId", parameterName});
        Object[] arguments = {42L, "Q8!a4j6Vz"};
        when(joinPoint.getArgs()).thenReturn(arguments);

        aspect.logServiceMethods(joinPoint);

        verify(joinPoint).proceed();
        assertThat(arguments).containsExactly(42L, "Q8!a4j6Vz");
        assertThat(appender.list)
                .extracting(ILoggingEvent::getFormattedMessage)
                .anySatisfy(message -> assertThat(message).contains("[42, [REDACTED]]"))
                .allSatisfy(message -> assertThat(message).doesNotContain("Q8!a4j6Vz"));
    }

    @Test
    @DisplayName("Non-sensitive parameters remain useful in debug diagnostics")
    void ordinaryNamedArgumentsRemainVisible() throws Throwable {
        when(signature.getParameterNames()).thenReturn(new String[] {"categoryName", "userId"});
        when(joinPoint.getArgs()).thenReturn(new Object[] {"groceries", 42L});

        aspect.logControllerMethods(joinPoint);

        assertThat(appender.list)
                .extracting(ILoggingEvent::getFormattedMessage)
                .anySatisfy(message -> assertThat(message).contains("[groceries, 42]"));
    }

    @Test
    @DisplayName("DEBUG logging must not fail a call because an argument cannot render itself")
    void failingArgumentRenderingDoesNotPreventExecution() throws Throwable {
        Object argument = unprintableArgument();
        when(joinPoint.getArgs()).thenReturn(new Object[] {argument});
        when(joinPoint.proceed()).thenReturn("completed");

        assertThat(aspect.logControllerMethods(joinPoint)).isEqualTo("completed");

        verify(joinPoint).proceed();
        assertThat(appender.list)
                .extracting(ILoggingEvent::getFormattedMessage)
                .anySatisfy(message -> assertThat(message).contains("[UNAVAILABLE]"));
    }

    @Test
    @DisplayName("Disabled DEBUG logging never evaluates argument values")
    void disabledDebugDoesNotRenderArguments() throws Throwable {
        logger.setLevel(Level.WARN);
        when(joinPoint.getArgs()).thenReturn(new Object[] {unprintableArgument()});
        when(joinPoint.proceed()).thenReturn("completed");

        assertThat(aspect.logAnnotatedMethods(joinPoint)).isEqualTo("completed");

        verify(joinPoint).proceed();
        assertThat(appender.list).noneMatch(event -> event.getLevel().equals(Level.DEBUG));
    }

    @Test
    @DisplayName("Method failures are logged and rethrown without wrapping or retrying")
    void preservesFailure() throws Throwable {
        IllegalStateException failure = new IllegalStateException("backup unavailable");
        when(joinPoint.getArgs()).thenReturn(new Object[0]);
        when(joinPoint.proceed()).thenThrow(failure);

        assertThatThrownBy(() -> aspect.logServiceMethods(joinPoint)).isSameAs(failure);

        verify(joinPoint).proceed();
        assertThat(appender.list)
                .filteredOn(event -> event.getLevel().equals(Level.ERROR))
                .singleElement()
                .satisfies(
                        event -> {
                            assertThat(event.getFormattedMessage())
                                    .contains("BackupService.restoreBackup", "backup unavailable");
                            assertThat(event.getThrowableProxy().getClassName())
                                    .isEqualTo(IllegalStateException.class.getName());
                        });
    }

    @Test
    @DisplayName("Slow calls warn even when DEBUG logging is disabled")
    void slowExecutionIsReported() throws Throwable {
        logger.setLevel(Level.WARN);
        when(joinPoint.getArgs()).thenReturn(new Object[0]);
        when(joinPoint.proceed())
                .thenAnswer(
                        ignored -> {
                            Thread.sleep(LoggingAspect.SLOW_EXECUTION_THRESHOLD_MS + 20);
                            return "completed";
                        });

        assertThat(aspect.logServiceMethods(joinPoint)).isEqualTo("completed");

        verify(joinPoint).proceed();
        assertThat(appender.list)
                .filteredOn(event -> event.getLevel().equals(Level.WARN))
                .singleElement()
                .satisfies(
                        event ->
                                assertThat(event.getFormattedMessage())
                                        .contains(
                                                "[SERVICE] SLOW:", "BackupService.restoreBackup"));
    }

    private Object unprintableArgument() {
        return new Object() {
            @Override
            public String toString() {
                throw new IllegalStateException("uninitialized argument");
            }
        };
    }
}
