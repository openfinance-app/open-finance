package org.openfinance.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openfinance.config.ImportProperties;
import org.openfinance.dto.ImportParseResult;
import org.openfinance.dto.ImportedTransaction;
import org.openfinance.dto.SkroogeImportParseResult;
import org.openfinance.entity.Account;
import org.openfinance.entity.ImportSession;
import org.openfinance.entity.ImportSession.ImportStatus;
import org.openfinance.entity.User;
import org.openfinance.entity.UserSettings;
import org.openfinance.repository.AccountRepository;
import org.openfinance.repository.UserRepository;
import org.openfinance.repository.UserSettingsRepository;
import org.openfinance.service.parser.CsvParser;
import org.openfinance.service.parser.ImportParseContext;
import org.openfinance.service.parser.OfxParser;
import org.openfinance.service.parser.QifParser;
import org.openfinance.service.parser.SkroogeJsonParser;

/** Parses one source without persisting a separate session or changing import lifecycle state. */
@RequiredArgsConstructor
@Slf4j
final class ImportFileParser {
    private final FileStorageService fileStorageService;
    private final QifParser qifParser;
    private final OfxParser ofxParser;
    private final CsvParser csvParser;
    private final SkroogeJsonParser skroogeJsonParser;
    private final UserRepository userRepository;
    private final UserSettingsRepository userSettingsRepository;
    private final AccountRepository accountRepository;
    private final DefaultCurrencyProvider defaultCurrencyProvider;
    private final ImportProperties importProperties;
    private final ImportedTransferService transferImportService;
    private final ObjectMapper objectMapper;

    void parse(ImportSession session) throws IOException {
        ParsedFile parsed;
        try (InputStream input =
                fileStorageService.getFileContent(session.getUploadId(), session.getUserId())) {
            parsed =
                    readFile(
                            session,
                            input,
                            buildParseContext(session.getUserId()),
                            defaultCurrency(session.getUserId()));
        }
        List<ImportedTransaction> transactions = parsed.transactions();
        transferImportService.resolveOfxTransfers(session, transactions);
        matchAccount(session, transactions);
        session.setTotalTransactions(transactions.size());
        session.setErrorCount(
                (int) transactions.stream().filter(ImportedTransaction::hasErrors).count());
        session.setStatus(ImportStatus.PARSED);
        session.setMetadata(
                serializeTransactions(
                        transactions, parsed.ledgerBalance(), parsed.currency(), parsed.extra()));
    }

    private ParsedFile readFile(
            ImportSession session, InputStream input, ImportParseContext context, String currency)
            throws IOException {
        String name = session.getFileName();
        return switch (session.getFileFormat().toUpperCase(Locale.ROOT)) {
            case "QIF" -> statement(
                    qifParser.parseFile(input, name, context), null, Map.of(), currency);
            case "CSV" -> statement(
                    csvParser.parseFile(input, name, context), null, Map.of(), currency);
            case "OFX", "QFX" -> {
                ImportParseResult result = ofxParser.parseFileToResult(input, name, context);
                yield statement(
                        result.getTransactions(),
                        result.getLedgerBalance(),
                        result.getLedgerBalances(),
                        result.getCurrency() != null ? result.getCurrency() : currency);
            }
            case "JSON" -> {
                if (!importProperties.isSkroogeJsonEnabled()) {
                    throw new IllegalArgumentException("Skrooge JSON import is currently disabled");
                }
                SkroogeImportParseResult result = skroogeJsonParser.parseFile(input, name);
                yield new ParsedFile(
                        result.getTransactions(),
                        null,
                        result.getCurrency() != null ? result.getCurrency() : currency,
                        Map.of("skroogeMetadata", result.getSkroogeMetadata()));
            }
            default -> throw new IllegalArgumentException(
                    "Unsupported file format: " + session.getFileFormat());
        };
    }

    private ParsedFile statement(
            List<ImportedTransaction> rows,
            BigDecimal balance,
            Map<String, BigDecimal> balances,
            String currency) {
        return new ParsedFile(
                rows,
                balance,
                currency,
                Map.of(
                        "ledgerBalances", balances == null ? Map.of() : balances,
                        "statementNetAmounts", ImportStatementPositions.netAmounts(rows),
                        "statementOpeningDates", ImportStatementPositions.openingDates(rows)));
    }

    private String defaultCurrency(Long userId) {
        try {
            User user = userRepository.findById(userId).orElse(null);
            if (user != null && user.getBaseCurrency() != null) return user.getBaseCurrency();
        } catch (Exception exception) {
            log.warn("Could not fetch user base currency", exception);
        }
        return defaultCurrencyProvider.getDefaultCurrency();
    }

    private void matchAccount(ImportSession session, List<ImportedTransaction> transactions) {
        if (session.getAccountId() != null || transactions.isEmpty()) return;
        String detected =
                transactions.stream()
                        .map(ImportedTransaction::getAccountName)
                        .filter(name -> name != null && !name.isBlank())
                        .map(String::trim)
                        .findFirst()
                        .orElse(null);
        if (detected == null) {
            session.setSuggestedAccountName(session.getFileName().replaceFirst("\\.[^.]+$", ""));
            return;
        }
        session.setSuggestedAccountName(formatAccountSuggestion(detected));
        String normalized = normalizeAccount(detected);
        for (Account account : accountRepository.findByUserId(session.getUserId())) {
            if (account.getDeletedDate() != null || !Boolean.TRUE.equals(account.getIsActive()))
                continue;
            if (normalized.equals(normalizeAccount(account.getName()))
                    || normalized.equals(normalizeAccount(account.getAccountNumber()))) {
                session.setAccountId(account.getId());
                return;
            }
        }
    }

    private String normalizeAccount(String name) {
        return name == null ? "" : name.replaceAll("[^a-zA-Z0-9]", "").toLowerCase(Locale.ROOT);
    }

    private record ParsedFile(
            List<ImportedTransaction> transactions,
            BigDecimal ledgerBalance,
            String currency,
            Map<String, Object> extra) {}

    /**
     * Build the parsing context (date ordering, message locale) from the user's settings. Falls
     * back to locale-derived defaults when the user has no settings or they cannot be loaded.
     */
    private ImportParseContext buildParseContext(Long userId) {
        try {
            UserSettings settings = userSettingsRepository.findByUserId(userId).orElse(null);
            if (settings != null) {
                return ImportParseContext.from(settings);
            }
        } catch (Exception e) {
            log.warn("Could not load settings for user {}; using parse defaults", userId, e);
        }
        return ImportParseContext.defaults();
    }

    static String formatAccountSuggestion(String rawId) {
        if (rawId == null || rawId.isBlank()) return rawId;
        // If it's a long numeric string (looks like a bank account number), abbreviate
        if (rawId.replaceAll("[^0-9]", "").length() >= 9
                && rawId.replaceAll("[^0-9]", "").equals(rawId.replaceAll("\\s", ""))) {
            String last4 = rawId.substring(Math.max(0, rawId.length() - 4));
            return "Account \u2022\u2022\u2022" + last4;
        }
        return rawId;
    }

    private String serializeTransactions(
            List<ImportedTransaction> transactions,
            BigDecimal ledgerBalance,
            String fileCurrency,
            Map<String, Object> extraMetadata) {
        Map<String, Object> metadata = new HashMap<>(extraMetadata);
        metadata.put("transactions", transactions);
        metadata.put("count", transactions.size());
        metadata.put("ledgerBalance", ledgerBalance);
        metadata.put("fileCurrency", fileCurrency);
        metadata.put("timestamp", Instant.now().toString());
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not store parsed transactions", exception);
        }
    }
}
