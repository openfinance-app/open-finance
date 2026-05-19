package org.openfinance.config;

import java.lang.reflect.Method;
import java.util.concurrent.Executor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Configuration for asynchronous task execution in the application.
 *
 * <p>This configuration enables Spring's @Async annotation support and provides a custom thread
 * pool executor optimized for file import operations.
 *
 * <p><strong>Use Cases:</strong>
 *
 * <ul>
 *   <li>File parsing for transaction imports (QIF, OFX, QFX)
 *   <li>Bulk transaction processing
 *   <li>Long-running operations that shouldn't block HTTP requests
 * </ul>
 *
 * <p><strong>Thread Pool Configuration:</strong>
 *
 * <ul>
 *   <li>Core pool size: 2 threads (always available)
 *   <li>Max pool size: 5 threads (peak capacity)
 *   <li>Queue capacity: 25 tasks (pending operations)
 *   <li>Keep-alive: 60 seconds (idle thread timeout)
 * </ul>
 *
 * <p>Requirement REQ-2.5.1.8: Asynchronous import processing
 *
 * <p>Requirement REQ-3.5: Application performance optimization
 *
 * @author Open-Finance Development Team
 * @version 1.0
 * @since 2024-01-15
 * @see org.springframework.scheduling.annotation.Async
 * @see org.openfinance.service.ImportService
 */
@Slf4j
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    /**
     * Creates a thread pool task executor for async operations.
     *
     * <p>This executor is specifically tuned for file import operations which are I/O intensive and
     * may involve parsing large files. The configuration balances resource usage with
     * responsiveness:
     *
     * <ul>
     *   <li><strong>Core Pool Size (2):</strong> Minimum threads always ready to handle imports
     *   <li><strong>Max Pool Size (5):</strong> Maximum concurrent imports to prevent resource
     *       exhaustion
     *   <li><strong>Queue Capacity (25):</strong> Buffer for pending imports during peak usage
     *   <li><strong>Keep Alive (60s):</strong> Idle threads are terminated after 1 minute
     * </ul>
     *
     * <p><strong>Rejection Policy:</strong> CallerRunsPolicy - If all threads are busy and queue is
     * full, the task runs synchronously in the caller's thread (fallback behavior).
     *
     * <p><strong>Thread Naming:</strong> Threads are named "import-executor-{n}" for easy
     * identification in logs and monitoring tools.
     *
     * <p><strong>Example Usage:</strong>
     *
     * <pre>{@code
     * @Async("taskExecutor")
     * public void parseFileAsync(ImportSession session) {
     *     // Long-running parsing operation runs in background thread
     *     // HTTP request returns immediately with session ID
     * }
     * }</pre>
     *
     * <p><strong>Monitoring:</strong>
     *
     * <ul>
     *   <li>Check thread names in logs: "import-executor-1", "import-executor-2", etc.
     *   <li>Monitor queue size via JMX or Spring Boot Actuator
     *   <li>Watch for "Task rejected" warnings (indicates overload)
     * </ul>
     *
     * <p>Requirement REQ-2.5.1.8: Asynchronous import with configurable thread pool
     *
     * @return configured ThreadPoolTaskExecutor for async operations
     */
    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // Core pool size: minimum threads always available
        executor.setCorePoolSize(2);

        // Max pool size: maximum concurrent imports
        executor.setMaxPoolSize(5);

        // Queue capacity: pending imports buffer
        executor.setQueueCapacity(25);

        // Thread name prefix for identification in logs
        executor.setThreadNamePrefix("import-executor-");

        // Keep alive time: idle thread timeout (60 seconds)
        executor.setKeepAliveSeconds(60);

        // Allow core threads to timeout when idle
        executor.setAllowCoreThreadTimeOut(true);

        // Wait for tasks to complete on shutdown (graceful shutdown)
        executor.setWaitForTasksToCompleteOnShutdown(true);

        // Maximum wait time for shutdown (30 seconds)
        executor.setAwaitTerminationSeconds(30);

        // Initialize the executor
        executor.initialize();

        return executor;
    }

    /**
     * Handles uncaught exceptions from @Async methods.
     *
     * <p>This is a safety net for errors that escape method-level error handling. If an exception
     * reaches this handler, it indicates:
     *
     * <ul>
     *   <li>A bug in error handling (forgot to catch Exception)
     *   <li>Critical system error (OutOfMemoryError, ThreadDeath, etc.)
     *   <li>Framework-level error
     * </ul>
     *
     * <p><strong>Why This Matters:</strong>
     *
     * <p>Without this handler, uncaught async exceptions are silently logged by Spring and ignored.
     * For import operations, this would leave ImportSession stuck in PARSING status indefinitely,
     * with no indication to the user that parsing failed.
     *
     * <p><strong>Handling Strategy:</strong>
     *
     * <ol>
     *   <li>Log the exception with full stack trace for debugging
     *   <li>Log method name and parameters for context
     *   <li>Do NOT attempt to update database (no repositories available here)
     *   <li>Rely on method-level error handling for session status updates
     * </ol>
     *
     * <p><strong>Best Practice:</strong> This handler should rarely trigger in production. If it
     * does, investigate immediately as it indicates a bug in error handling.
     *
     * <p>Requirement REQ-2.5.1.8: Robust async error handling
     *
     * <p>Requirement REQ-3.4: Application reliability and error tracking
     *
     * @return exception handler for async methods
     */
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return new AsyncUncaughtExceptionHandler() {
            @Override
            public void handleUncaughtException(Throwable ex, Method method, Object... params) {
                log.error(
                        "!!! UNCAUGHT ASYNC EXCEPTION !!! in method '{}' with parameters {}: {}",
                        method.getName(),
                        params,
                        ex.getMessage(),
                        ex);

                // Log additional context for import operations
                if ("parseFileAsync".equals(method.getName()) && params.length > 0) {
                    log.error(
                            "Import session {} failed with uncaught exception - session may be stuck in PARSING status",
                            params[0]);
                }

                // Note: Cannot inject repository here to update session status
                // Method-level error handling (try-catch in parseFileAsync) should prevent this
                // If this handler triggers, it indicates a bug in error handling
            }
        };
    }
}
