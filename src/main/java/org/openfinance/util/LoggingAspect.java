package org.openfinance.util;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

/**
 * Aspect for logging method execution in service and controller layers. Automatically logs method
 * entry, exit, and exceptions.
 *
 * <p>Usage: Annotate methods with @Logged or apply to entire classes.
 *
 * @see Logged
 */
@Slf4j
@Aspect
@Component
public class LoggingAspect {

    /**
     * Execution time (in milliseconds) above which a method call is logged at WARN as slow. Kept
     * consistent with {@code PerformanceMonitoringAspect.WARN_THRESHOLD_MS} and the Hibernate
     * slow-query threshold so a single notion of "slow" applies across the application.
     */
    static final long SLOW_EXECUTION_THRESHOLD_MS = 500;

    /**
     * Normalized (lower-case, alphanumeric-only) markers of credential-bearing arguments. Arguments
     * are normalized the same way before matching, so {@code api_key}, {@code api-key}, {@code
     * apiKey} and {@code mot de passe} are all caught. Errs on redacting too much: a false positive
     * hides a debug value, a false negative leaks a credential into logs.
     */
    private static final List<String> SENSITIVE_ARG_TOKENS =
            List.of(
                    "password",
                    "passcode",
                    "pwd",
                    "motdepasse",
                    "secret",
                    "token",
                    "apikey",
                    "credential",
                    "authorization",
                    "bearer",
                    "privatekey",
                    "encryptionkey",
                    "encryptionsession",
                    "xencryptionsession");

    /** Raw JWT values carry no keyword to match on; detect their three-segment shape instead. */
    private static final Pattern JWT_SHAPE =
            Pattern.compile("eyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+");

    /** Pointcut for all methods in service layer */
    @Pointcut("within(org.openfinance.service..*)")
    public void serviceMethods() {}

    /** Pointcut for all methods in controller layer */
    @Pointcut("within(org.openfinance.controller..*)")
    public void controllerMethods() {}

    /** Pointcut for methods annotated with @Logged */
    @Pointcut("@annotation(org.openfinance.util.Logged)")
    public void loggedMethods() {}

    /** Around advice for service methods */
    @Around("serviceMethods() && !loggedMethods()")
    public Object logServiceMethods(ProceedingJoinPoint joinPoint) throws Throwable {
        return logMethodExecution(joinPoint, "SERVICE");
    }

    /** Around advice for controller methods */
    @Around("controllerMethods() && !loggedMethods()")
    public Object logControllerMethods(ProceedingJoinPoint joinPoint) throws Throwable {
        return logMethodExecution(joinPoint, "CONTROLLER");
    }

    /** Around advice for explicitly logged methods */
    @Around("loggedMethods()")
    public Object logAnnotatedMethods(ProceedingJoinPoint joinPoint) throws Throwable {
        return logMethodExecution(joinPoint, "LOGGED");
    }

    /**
     * Logs method execution with timing information
     *
     * @param joinPoint the join point
     * @param layer the layer (SERVICE, CONTROLLER, etc.)
     * @return the method result
     * @throws Throwable if method throws exception
     */
    private Object logMethodExecution(ProceedingJoinPoint joinPoint, String layer)
            throws Throwable {
        String className = joinPoint.getSignature().getDeclaringTypeName();
        String methodName = joinPoint.getSignature().getName();
        Object[] args = joinPoint.getArgs();

        long startTime = System.currentTimeMillis();

        // Log method entry
        if (log.isDebugEnabled()) {
            log.debug(
                    "[{}] Entering: {}.{}() with arguments: {}",
                    layer,
                    className,
                    methodName,
                    sanitizeArguments(args, joinPoint.getSignature()));
        }

        try {
            // Execute the method
            Object result = joinPoint.proceed();

            long executionTime = System.currentTimeMillis() - startTime;

            // Log method exit with timing
            if (log.isDebugEnabled()) {
                log.debug(
                        "[{}] Exiting: {}.{}() - Execution time: {} ms",
                        layer,
                        className,
                        methodName,
                        executionTime);
            }

            // Log slow methods
            if (isSlowExecution(executionTime)) {
                log.warn(
                        "[{}] SLOW: {}.{}() took {} ms",
                        layer,
                        className,
                        methodName,
                        executionTime);
            }

            return result;

        } catch (Throwable ex) {
            long executionTime = System.currentTimeMillis() - startTime;

            // Log exception
            log.error(
                    "[{}] Exception in {}.{}() after {} ms: {}",
                    layer,
                    className,
                    methodName,
                    executionTime,
                    ex.getMessage(),
                    ex);

            throw ex;
        }
    }

    /**
     * Whether a method's execution time should be reported as slow. Package-private for testing.
     *
     * @param executionTimeMs measured execution time in milliseconds
     * @return true if the execution should be logged at WARN as slow
     */
    boolean isSlowExecution(long executionTimeMs) {
        return executionTimeMs > SLOW_EXECUTION_THRESHOLD_MS;
    }

    /**
     * Sanitizes arguments to prevent logging sensitive data. Package-private for testing.
     *
     * @param args the method arguments
     * @return sanitized string representation
     */
    String sanitizeArguments(Object[] args) {
        if (args == null || args.length == 0) {
            return "[]";
        }

        return Arrays.stream(args).map(this::sanitizeArgument).toList().toString();
    }

    private String sanitizeArguments(Object[] args, Signature signature) {
        if (args == null || !(signature instanceof MethodSignature method)) {
            return sanitizeArguments(args);
        }
        String[] names = method.getParameterNames();
        if (names == null) return sanitizeArguments(args);

        // Raw passwords and opaque session tokens need not contain a recognizable keyword.
        // Use parameter metadata too, without changing the arguments the method receives.
        Object[] sanitized = args.clone();
        for (int i = 0; i < Math.min(names.length, sanitized.length); i++) {
            if (isSensitive(names[i])) sanitized[i] = "[REDACTED]";
        }
        return sanitizeArguments(sanitized);
    }

    private String sanitizeArgument(Object argument) {
        if (argument == null) return "null";
        try {
            String value = argument.toString();
            if (isSensitive(value) || JWT_SHAPE.matcher(value).find()) return "[REDACTED]";
            return value.length() > 100 ? value.substring(0, 100) + "..." : value;
        } catch (RuntimeException exception) {
            // Diagnostic rendering (e.g. a detached JPA proxy) must not prevent execution.
            return "[UNAVAILABLE]";
        }
    }

    private boolean isSensitive(String value) {
        String normalized = value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        return SENSITIVE_ARG_TOKENS.stream().anyMatch(normalized::contains);
    }
}
