package org.openfinance.service.history;

import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.openfinance.entity.OperationType;
import org.openfinance.service.OperationHistoryService;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Acquires the database write gate before any snapshot read, including on SQLite WAL. */
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 200)
@RequiredArgsConstructor
public class ReversibleOperationAspect {
    private final PlatformTransactionManager transactionManager;
    private final HistoryStateStore states;
    private final OperationHistoryService history;
    private final org.openfinance.config.EncryptionProperties encryptionProperties;

    @Around("@annotation(operation)")
    public Object capture(ProceedingJoinPoint call, ReversibleOperation operation)
            throws Throwable {
        if (HistoryActionContext.active() || history.isRecordingSuppressed()) return call.proceed();
        Long userId = (Long) call.getArgs()[operation.userArgument()];
        if (userId == null)
            return call.proceed(); // Let the domain validation explain the invalid request.
        try {
            TransactionTemplate transaction = new TransactionTemplate(transactionManager);
            transaction.setIsolationLevel(states.captureIsolation());
            if (operation.requiresNew())
                transaction.setPropagationBehavior(
                        org.springframework.transaction.TransactionDefinition
                                .PROPAGATION_REQUIRES_NEW);
            return transaction.execute(
                    status -> {
                        // Preserve the annotated transaction boundary for background callers
                        // even when their encryption key is unavailable for complete capture.
                        if (encryptionProperties.isEnabled()
                                && org.openfinance.security.EncryptionContext.getKey() == null) {
                            try {
                                return call.proceed();
                            } catch (Throwable failure) {
                                throw new ActionFailure(failure);
                            }
                        }
                        states.lock(userId);
                        Long rootId =
                                operation.idArgument() >= 0
                                                && call.getArgs()[operation.idArgument()]
                                                        instanceof Long argument
                                        ? argument
                                        : null;
                        java.util.Set<Long> attachments =
                                operation.operation() == OperationType.DELETE
                                        ? states.deletionAttachments(
                                                operation.entity(), rootId, userId)
                                        : java.util.Set.of();
                        HistoryStateStore.Snapshot before = states.snapshot(userId, attachments);
                        try (HistoryActionContext action =
                                HistoryActionContext.open(operation.entity())) {
                            Object response;
                            try {
                                response = call.proceed();
                            } catch (Throwable failure) {
                                throw new ActionFailure(failure);
                            }
                            HistoryChangeSet changes =
                                    states.difference(before, states.snapshot(userId, attachments));
                            Long id = action.id();
                            if (id == null
                                    && operation.idArgument() >= 0
                                    && call.getArgs()[operation.idArgument()]
                                            instanceof Long argument) id = argument;
                            history.recordAction(
                                    userId,
                                    operation.entity(),
                                    operation.operation(),
                                    id,
                                    action.label(),
                                    changes);
                            return response;
                        }
                    });
        } catch (ActionFailure failure) {
            throw failure.getCause();
        }
    }

    @Around("@annotation(operation)")
    public Object serialize(ProceedingJoinPoint call, SerializedFinancialWrite operation)
            throws Throwable {
        if (HistoryActionContext.active()) return call.proceed();
        Long userId = (Long) call.getArgs()[operation.userArgument()];
        if (userId == null) return call.proceed();
        boolean suppressed = history.isRecordingSuppressed();
        try {
            return new TransactionTemplate(transactionManager)
                    .execute(
                            status -> {
                                states.lock(userId);
                                if (operation.suppressHistory()) history.suppressRecording();
                                try {
                                    return call.proceed();
                                } catch (Throwable failure) {
                                    throw new ActionFailure(failure);
                                } finally {
                                    if (!suppressed && operation.suppressHistory())
                                        history.resumeRecording();
                                }
                            });
        } catch (ActionFailure failure) {
            throw failure.getCause();
        }
    }

    private static final class ActionFailure extends RuntimeException {
        private ActionFailure(Throwable cause) {
            super(cause);
        }
    }
}
