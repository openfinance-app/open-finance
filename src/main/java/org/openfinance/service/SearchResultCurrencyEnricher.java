package org.openfinance.service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.openfinance.dto.SearchResultDto;
import org.openfinance.dto.SearchResultDto.SearchResultType;
import org.openfinance.entity.Transaction;
import org.openfinance.repository.TransactionRepository;
import org.springframework.stereotype.Component;

/** Adds presentation values after search paging, without changing native amounts or filtering. */
@Component
@RequiredArgsConstructor
public class SearchResultCurrencyEnricher {
    private final TransactionRepository transactionRepository;
    private final CurrencyConversionHelper currencyConversionHelper;

    public void populate(List<SearchResultDto> results, Long userId) {
        List<Long> ids =
                results.stream()
                        .filter(result -> result.getResultType() == SearchResultType.TRANSACTION)
                        .map(SearchResultDto::getId)
                        .toList();
        Map<Long, Transaction> transactions =
                (ids.isEmpty() ? List.<Transaction>of() : transactionRepository.findAllById(ids))
                        .stream()
                                .filter(
                                        transaction ->
                                                Objects.equals(transaction.getUserId(), userId))
                                .collect(Collectors.toMap(Transaction::getId, Function.identity()));
        List<String> transferIds =
                transactions.values().stream()
                        .map(Transaction::getTransferId)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList();
        Map<String, List<Transaction>> transfers =
                (transferIds.isEmpty()
                                ? List.<Transaction>of()
                                : transactionRepository.findTransferLegs(userId, transferIds))
                        .stream()
                                .filter(
                                        transaction ->
                                                Objects.equals(transaction.getUserId(), userId))
                                .collect(Collectors.groupingBy(Transaction::getTransferId));
        for (SearchResultDto result : results) {
            populate(result, userId, transactions, transfers);
        }
    }

    private void populate(
            SearchResultDto result,
            Long userId,
            Map<Long, Transaction> transactions,
            Map<String, List<Transaction>> transfers) {
        if (result.getAmount() == null || result.getCurrency() == null) return;
        CurrencyConversionHelper.ConversionResult conversion;
        if (result.getResultType() == SearchResultType.TRANSACTION) {
            Transaction transaction = transactions.get(result.getId());
            if (transaction == null) return;
            result.setOriginalAmount(transaction.getOriginalAmount());
            result.setOriginalCurrency(transaction.getOriginalCurrency());
            transfers.getOrDefault(transaction.getTransferId(), List.of()).stream()
                    .filter(leg -> !Objects.equals(leg.getId(), transaction.getId()))
                    .filter(leg -> leg.getType() != transaction.getType())
                    .findFirst()
                    .ifPresent(
                            leg -> {
                                result.setTransferAmount(leg.getAmount());
                                result.setTransferCurrency(leg.getCurrency());
                            });
            conversion =
                    currencyConversionHelper.convertTransaction(
                            transaction, result.getTransferAmount(), result.getTransferCurrency());
        } else {
            // Search dates on holdings are acquisition dates, not valuation dates.
            conversion =
                    currencyConversionHelper.convert(
                            userId,
                            result.getCurrency(),
                            result.getAmount(),
                            false,
                            null,
                            "search");
        }
        result.setAmountInBaseCurrency(conversion.amountInBaseCurrency());
        result.setBaseCurrency(conversion.baseCurrency());
        result.setExchangeRate(conversion.exchangeRate());
        result.setIsConverted(conversion.converted());
    }
}
