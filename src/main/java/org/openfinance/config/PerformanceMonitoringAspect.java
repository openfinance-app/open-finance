package org.openfinance.config;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;

/**
 * AOP aspect that measures and logs execution time for service and controller methods.
 *
 * <p>All public methods in {@code org.openfinance.service} and {@code org.openfinance.controller}
 * packages are instrumented. Calls that exceed the configured threshold are logged at WARN level so
 * they surface in the standard application log and can be correlated with Hibernate's slow-query
 * log.
 *
 * <p>Thresholds:
 *
 * <ul>
 *   <li>{@link #WARN_THRESHOLD_MS} — 500 ms: logged as WARN
 *   <li>{@link #DEBUG_THRESHOLD_MS} — 100 ms: logged as DEBUG (useful during development; silent in
 *       production when DEBUG is off)
 * </ul>
 *
 * <p>The aspect does <em>not</em> swallow exceptions — any exception propagated by the target
 * method is re-thrown after timing information is recorded.
 *
 * @author Open Finance Team
 * @version 1.0
 * @since 2026-03-19
 */
@Slf4j
@Aspect
@Component
public class PerformanceMonitoringAspect {

    /** Execution time above which a WARN log entry is emitted (milliseconds). */
    private static final long WARN_THRESHOLD_MS = 500L;

    /** Execution time above which a DEBUG log entry is emitted (milliseconds). */
    private static final long DEBUG_THRESHOLD_MS = 100L;

    // -------------------------------------------------------------------------
    // Pointcuts
    // -------------------------------------------------------------------------

    /** Matches all public methods in the service layer. */
    @Pointcut("execution(public * org.openfinance.service..*(..))")
    public void serviceMethods() {}

    /** Matches all public methods in the controller layer. */
    @Pointcut("execution(public * org.openfinance.controller..*(..))")
    public void controllerMethods() {}

    // -------------------------------------------------------------------------
    // Advice
    // -------------------------------------------------------------------------

    /**
     * Measures the wall-clock time of each service method invocation and logs slow calls.
     *
     * @param joinPoint the intercepted join point
     * @return the value returned by the target method
     * @throws Throwable any exception raised by the target method
     */
    @Around("serviceMethods()")
    public Object monitorServiceMethod(ProceedingJoinPoint joinPoint) throws Throwable {
        return monitor(joinPoint, "SERVICE");
    }

    /**
     * Measures the wall-clock time of each controller method invocation and logs slow calls.
     *
     * @param joinPoint the intercepted join point
     * @return the value returned by the target method
     * @throws Throwable any exception raised by the target method
     */
    @Around("controllerMethods()")
    public Object monitorControllerMethod(ProceedingJoinPoint joinPoint) throws Throwable {
        return monitor(joinPoint, "CONTROLLER");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Core timing logic shared by all advice methods.
     *
     * @param joinPoint the intercepted join point
     * @param layer a short label used in log messages (e.g. "SERVICE")
     * @return the return value of the target method
     * @throws Throwable any exception raised by the target method
     */
    private Object monitor(ProceedingJoinPoint joinPoint, String layer) throws Throwable {
        long start = System.currentTimeMillis();
        try {
            return joinPoint.proceed();
        } finally {
            long elapsed = System.currentTimeMillis() - start;
            logElapsed(layer, joinPoint, elapsed);
        }
    }

    /**
     * Emits a log entry at the appropriate level based on the elapsed duration.
     *
     * @param layer the architectural layer label
     * @param joinPoint the join point whose signature is used in the log message
     * @param elapsedMs measured wall-clock duration in milliseconds
     */
    private void logElapsed(String layer, ProceedingJoinPoint joinPoint, long elapsedMs) {
        String signature = joinPoint.getSignature().toShortString();
        if (elapsedMs >= WARN_THRESHOLD_MS) {
            log.warn("[PERF] [{}] SLOW ({} ms) — {}", layer, elapsedMs, signature);
        } else if (elapsedMs >= DEBUG_THRESHOLD_MS) {
            log.debug("[PERF] [{}] {} ms — {}", layer, elapsedMs, signature);
        }
    }
}
