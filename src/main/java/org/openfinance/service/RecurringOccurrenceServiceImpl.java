package org.openfinance.service;

import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.openfinance.dto.TransactionRequest;
import org.openfinance.entity.EntityType;
import org.openfinance.entity.OperationType;
import org.openfinance.entity.RecurringTransaction;
import org.openfinance.entity.TransactionType;
import org.openfinance.repository.RecurringTransactionRepository;
import org.openfinance.service.history.ReversibleOperation;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RecurringOccurrenceServiceImpl implements RecurringOccurrenceService {
    private final RecurringTransactionRepository recurringRepository;
    private final org.openfinance.repository.AccountRepository accountRepository;
    private final TransactionService transactionService;
    private final JdbcTemplate jdbc;

    @Override
    @Transactional
    @ReversibleOperation(
            entity = EntityType.RECURRING_TRANSACTION,
            operation = OperationType.UPDATE,
            userArgument = 1,
            idArgument = 0,
            requiresNew = true)
    public boolean post(Long recurringId, Long userId, LocalDate expectedDate) {
        // Acquire the writer/row lock before reading, including on SQLite WAL databases.
        if (jdbc.update(
                        "UPDATE recurring_transactions SET updated_at = updated_at WHERE id = ? AND user_id = ?",
                        recurringId,
                        userId)
                != 1) return false;
        RecurringTransaction template =
                recurringRepository.findByIdAndUserId(recurringId, userId).orElseThrow();
        if (!Boolean.TRUE.equals(template.getIsActive())
                || !expectedDate.equals(template.getNextOccurrence())) return false;
        if (expectedDate.isAfter(LocalDate.now())) return false;
        if (!activeAccount(template.getAccountId(), userId)
                || (template.getToAccountId() != null
                        && !activeAccount(template.getToAccountId(), userId))) {
            template.setIsActive(false);
            recurringRepository.save(template);
            return false;
        }
        TransactionRequest request =
                TransactionRequest.builder()
                        .accountId(template.getAccountId())
                        .toAccountId(template.getToAccountId())
                        .type(template.getType())
                        .amount(template.getAmount())
                        .currency(template.getCurrency())
                        .categoryId(template.getCategoryId())
                        .date(expectedDate)
                        .description(template.getDescription())
                        .notes(template.getNotes())
                        .isReconciled(false)
                        .build();
        if (template.getType() == TransactionType.TRANSFER) {
            transactionService.createTransfer(userId, request);
        } else {
            transactionService.createTransaction(userId, request);
        }
        LocalDate next = template.calculateNextOccurrence();
        template.setNextOccurrence(next);
        if (template.getEndDate() != null && next.isAfter(template.getEndDate()))
            template.setIsActive(false);
        recurringRepository.save(template);
        return true;
    }

    private boolean activeAccount(Long accountId, Long userId) {
        return accountRepository
                .findByIdAndUserId(accountId, userId)
                .map(account -> Boolean.TRUE.equals(account.getIsActive()))
                .orElse(false);
    }
}
