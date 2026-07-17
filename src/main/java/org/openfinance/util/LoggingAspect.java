package org.openfinance.util;

import java.util.Arrays;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
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
                    sanitizeArguments(args));
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
            if (executionTime > 1000) {
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
     * Sanitizes arguments to prevent logging sensitive data
     *
     * @param args the method arguments
     * @return sanitized string representation
     */
    private String sanitizeArguments(Object[] args) {
        if (args == null || args.length == 0) {
            return "[]";
        }

        return Arrays.stream(args)
                .map(
                        arg -> {
                            if (arg == null) {
                                return "null";
                            }

                            String argString = arg.toString();

                            // Mask sensitive data in argument strings
                            if (argString.toLowerCase().contains("password")) {
                                return "[REDACTED]";
                            }

                            // Truncate long strings
                            if (argString.length() > 100) {
                                return argString.substring(0, 100) + "...";
                            }

                            return argString;
                        })
                .toList()
                .toString();
    }
}
