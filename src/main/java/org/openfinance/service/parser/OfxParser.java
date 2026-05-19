package org.openfinance.service.parser;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import lombok.extern.slf4j.Slf4j;
import org.openfinance.dto.ImportParseResult;
import org.openfinance.dto.ImportedTransaction;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

/**
 * Parser for Open Financial Exchange (OFX) and Quicken Financial Exchange (QFX) files.
 *
 * <p>OFX is a standard format for exchanging financial data between institutions and customers. QFX
 * is Quicken's proprietary variant, which is essentially OFX with minor differences.
 *
 * <p>Format Variants: - OFX 1.x (SGML): Text-based format with SGML-like tags (no closing tags) -
 * OFX 2.x (XML): Standard XML format with proper closing tags - QFX: Quicken variant, similar to
 * OFX 1.x
 *
 * <p>Transaction Fields parsed from STMTTRN / CCSTMTTRN: - TRNTYPE : Transaction type (DEBIT,
 * CREDIT, INT, DIV, FEE, …) — appended to memo - DTPOSTED : Date posted (YYYYMMDDHHMMSS) - DTUSER :
 * User-initiated date — used as fallback when DTPOSTED is absent - TRNAMT : Transaction amount
 * (negative for debits) - FITID : Financial Institution Transaction ID - CHECKNUM : Check number
 * (preferred over FITID when present) - REFNUM : Reference number (used when CHECKNUM absent and
 * preferred over FITID) - NAME : Payee/description - MEMO : Additional memo/notes - CURRENCY /
 * CURSYM : Currency code
 *
 * <p>Investment transactions (INVBUY, INVSELL, REINVEST, INCOME, INVEXPENSE, etc.) are parsed from
 * INVSTMTTRNRS / INVTRANLIST using minimal field extraction: - DTSETTLE or DTTRADE as date - TOTAL
 * as amount (falls back to UNITS × UNITPRICE) - SECID/UNIQUEID as reference - security name from
 * MEMO or INVTRAN/MEMO
 *
 * <p>Charset handling: OFX 1.x files may declare CHARSET:1252 or similar in their headers. When
 * detected, the file is re-read using the appropriate charset. UTF-8 is used as the default.
 *
 * <p>Requirements: - REQ-2.5.1.1: File Format Support (OFX/QFX parsing) - REQ-2.5.1.3: Import
 * Validation (error reporting)
 *
 * @author Open Finance Team
 * @since Sprint 7 - Transaction Import
 */
@Slf4j
@Component
public class OfxParser {

    /** Date/time formats used in OFX files. */
    private static final DateTimeFormatter[] DATE_FORMATS = {
        DateTimeFormatter.ofPattern("yyyyMMddHHmmss"), // 20240115120000
        DateTimeFormatter.ofPattern("yyyyMMdd"), // 20240115
        DateTimeFormatter.ofPattern("yyyyMMddHHmmss.SSS"), // with milliseconds
        DateTimeFormatter.ofPattern("yyyy-MM-dd") // ISO (XML variant)
    };

    /** Detects OFX SGML 1.x header. */
    private static final Pattern SGML_HEADER_PATTERN =
            Pattern.compile("OFXHEADER:\\s*\\d+", Pattern.CASE_INSENSITIVE);

    /** Extracts charset declaration from OFX headers (e.g., CHARSET:1252). */
    private static final Pattern CHARSET_PATTERN =
            Pattern.compile("CHARSET:\\s*(\\S+)", Pattern.CASE_INSENSITIVE);

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Parse OFX/QFX file and extract transactions.
     *
     * @param inputStream Input stream of OFX/QFX file content
     * @param fileName Original file name for error reporting
     * @return List of imported transactions with validation errors
     * @throws IOException if file reading fails
     */
    public ImportParseResult parseFileToResult(InputStream inputStream, String fileName)
            throws IOException {
        log.info("Starting OFX/QFX file parsing: {}", fileName);

        // Read with UTF-8 first so we can inspect headers for charset
        byte[] rawBytes = inputStream.readAllBytes();
        String probe = new String(rawBytes, StandardCharsets.UTF_8);

        // Detect and re-decode using declared charset if necessary
        Charset charset = detectCharset(probe);
        String fileContent =
                charset.equals(StandardCharsets.UTF_8) ? probe : new String(rawBytes, charset);

        boolean isSGML = detectSGMLFormat(fileContent);

        if (isSGML) {
            log.debug("Detected OFX SGML format (OFX 1.x)");
            return parseSGML(fileContent, fileName);
        } else {
            log.debug("Detected OFX XML format (OFX 2.x)");
            return parseXML(fileContent, fileName);
        }
    }

    // -------------------------------------------------------------------------
    // Format detection & charset
    // -------------------------------------------------------------------------

    private boolean detectSGMLFormat(String content) {
        return SGML_HEADER_PATTERN.matcher(content).find();
    }

    /**
     * Detect charset from OFX SGML header block (lines before &lt;OFX&gt;). Falls back to UTF-8 if
     * nothing is declared or charset is unrecognised.
     */
    private Charset detectCharset(String probe) {
        Matcher m = CHARSET_PATTERN.matcher(probe);
        if (m.find()) {
            String declared = m.group(1).trim();
            // OFX uses numeric Windows code-page identifiers such as "1252"
            // as well as "USASCII" and "UTF-8"
            try {
                if (declared.matches("\\d+")) {
                    return Charset.forName("windows-" + declared);
                }
                return Charset.forName(declared);
            } catch (Exception e) {
                log.debug("Unrecognised OFX charset '{}', defaulting to UTF-8", declared);
            }
        }
        return StandardCharsets.UTF_8;
    }

    // -------------------------------------------------------------------------
    // SGML parsing
    // -------------------------------------------------------------------------

    private ImportParseResult parseSGML(String content, String fileName) throws IOException {
        log.debug("Converting SGML to XML for parsing");

        // If content contains an XML declaration, it is actually OFX 2.x XML wrapped in
        // an SGML-style header — delegate to the XML path directly.
        if (content.contains("<?xml")) {
            log.debug("XML declaration found inside SGML header — delegating to XML parser");
            return parseXML(content, fileName);
        }

        int ofxStart = content.indexOf("<OFX>");
        if (ofxStart == -1) {
            ofxStart = content.indexOf("<ofx>");
        }
        if (ofxStart == -1) {
            // Some SGML files omit the OFXHEADER block but still use SGML tags
            log.warn("No <OFX> tag found in content - attempting XML parse as fallback");
            return parseXML(content, fileName);
        }

        String ofxContent = content.substring(ofxStart);
        String xmlContent = convertSGMLToXML(ofxContent);
        return parseXMLContent(xmlContent, fileName);
    }

    /**
     * Convert OFX SGML (1.x) to well-formed XML by adding missing closing tags.
     *
     * <p>OFX SGML uses two kinds of elements:
     *
     * <ul>
     *   <li><b>Leaf elements</b>: {@code <TAG>value} — the value follows immediately on the same
     *       line, no closing tag is present. These must become {@code <TAG>value</TAG>}.
     *   <li><b>Container/aggregate elements</b>: {@code <TAG>} on a line by themselves, followed by
     *       nested content and a corresponding {@code </TAG>} closing tag. These already have
     *       closing tags and must be left as-is.
     * </ul>
     *
     * Strategy: tokenise the input line-by-line, tracking which open tags are aggregates (have
     * explicit closing tags in the source). For every {@code <TAG>value} line that does NOT already
     * have a matching close tag anywhere in the remaining source, append a closing tag immediately
     * after the value.
     */
    private String convertSGMLToXML(String sgml) {
        // Pre-compute the set of tag names that have explicit closing tags in the
        // source.
        // These are aggregate/container tags — we must NOT add extra closing tags for
        // them.
        java.util.Set<String> aggregateTags = new java.util.HashSet<>();
        java.util.regex.Pattern closeTagPattern =
                java.util.regex.Pattern.compile("</([A-Za-z0-9_.:-]+)>");
        java.util.regex.Matcher closeMatcher = closeTagPattern.matcher(sgml);
        while (closeMatcher.find()) {
            aggregateTags.add(closeMatcher.group(1).toUpperCase());
        }

        // Pattern: open tag followed immediately by a non-tag value on the same token.
        // Captures: group(1)=tag-name, group(2)=value (may be empty for pure container
        // tags).
        // Pattern: open tag followed immediately by a non-tag value on the same token.
        // Captures: group(1)=tag-name, group(2)=value (may be empty for pure container
        // tags).
        // Added \\s* to handle tags with extra spaces like <TAG >.
        // Regex to match <TAG>VALUE. Note that [^<\r\n]* stops at the next tag or
        // newline.
        java.util.regex.Pattern tokenPattern =
                java.util.regex.Pattern.compile("<([A-Za-z0-9_.:-]+)\\s*>([^<\r\n]*)");
        java.util.regex.Matcher tokenMatcher = tokenPattern.matcher(sgml);
        StringBuffer sb = new StringBuffer();

        while (tokenMatcher.find()) {
            String tag = tokenMatcher.group(1);
            String value =
                    tokenMatcher.group(2); // Don't trim yet to preserve potential inter-tag spacing
            String tagUpper = tag.toUpperCase();

            if (aggregateTags.contains(tagUpper)) {
                // Aggregate tag — already has a closing tag in the source; leave untouched.
                tokenMatcher.appendReplacement(
                        sb, java.util.regex.Matcher.quoteReplacement(tokenMatcher.group(0)));
            } else {
                // Leaf tag — add closing tag immediately after the value.
                // Trim the value here for the XML content but keep the match structure.
                String trimmedValue = value.trim();
                // Escape bare ampersands that are not already valid XML entities
                trimmedValue = trimmedValue.replaceAll("&(?!amp;|lt;|gt;|quot;|apos;|#)", "&amp;");
                tokenMatcher.appendReplacement(
                        sb,
                        "<"
                                + tag
                                + ">"
                                + java.util.regex.Matcher.quoteReplacement(trimmedValue)
                                + "</"
                                + tag
                                + ">");
            }
        }
        tokenMatcher.appendTail(sb);
        String converted = sb.toString();
        log.debug("SGML to XML conversion complete. Result length: {}", converted.length());
        return converted;
    }

    // -------------------------------------------------------------------------
    // XML parsing
    // -------------------------------------------------------------------------

    private ImportParseResult parseXML(String content, String fileName) throws IOException {
        int xmlStart = content.indexOf("<?xml");
        if (xmlStart == -1) {
            xmlStart = content.indexOf("<OFX");
            if (xmlStart == -1) {
                xmlStart = content.indexOf("<ofx");
            }
        }
        if (xmlStart > 0) {
            content = content.substring(xmlStart);
        }
        return parseXMLContent(content, fileName);
    }

    private ImportParseResult parseXMLContent(String xmlContent, String fileName)
            throws IOException {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);

            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new InputSource(new StringReader(xmlContent)));

            List<ImportedTransaction> transactions = new ArrayList<>();

            // Ledger balance
            BigDecimal ledgerBalance = null;
            NodeList ledgerBalList = doc.getElementsByTagName("LEDGERBAL");
            if (ledgerBalList.getLength() > 0) {
                Element ledgerBal = (Element) ledgerBalList.item(0);
                String balAmt = getElementText(ledgerBal, "BALAMT");
                if (balAmt != null && !balAmt.isEmpty()) {
                    ledgerBalance = parseAmount(balAmt);
                }
            }

            int index = 0;

            String fileCurrency = null;

            NodeList stmtRsList = doc.getElementsByTagName("STMTRS");
            for (int i = 0; i < stmtRsList.getLength(); i++) {
                Element stmtRs = (Element) stmtRsList.item(i);
                String accountId = extractAccountId(stmtRs, "BANKACCTFROM");
                String statementCurrency = extractStatementCurrency(stmtRs);
                if (fileCurrency == null
                        && statementCurrency != null
                        && !statementCurrency.isEmpty()) {
                    fileCurrency = statementCurrency;
                }
                NodeList stmtTrnList = stmtRs.getElementsByTagName("STMTTRN");
                for (int j = 0; j < stmtTrnList.getLength(); j++) {
                    Element el = (Element) stmtTrnList.item(j);
                    ImportedTransaction txn =
                            parseTransaction(el, fileName, ++index, accountId, statementCurrency);
                    if (txn != null) {
                        transactions.add(txn);
                    }
                }
            }

            NodeList ccStmtRsList = doc.getElementsByTagName("CCSTMTRS");
            for (int i = 0; i < ccStmtRsList.getLength(); i++) {
                Element stmtRs = (Element) ccStmtRsList.item(i);
                String accountId = extractAccountId(stmtRs, "CCACCTFROM");
                String statementCurrency = extractStatementCurrency(stmtRs);
                if (fileCurrency == null
                        && statementCurrency != null
                        && !statementCurrency.isEmpty()) {
                    fileCurrency = statementCurrency;
                }
                NodeList stmtTrnList = stmtRs.getElementsByTagName("CCSTMTTRN");
                for (int j = 0; j < stmtTrnList.getLength(); j++) {
                    Element el = (Element) stmtTrnList.item(j);
                    ImportedTransaction txn =
                            parseTransaction(el, fileName, ++index, accountId, statementCurrency);
                    if (txn != null) {
                        transactions.add(txn);
                    }
                }
            }

            NodeList invStmtRsList = doc.getElementsByTagName("INVSTMTRS");
            for (int i = 0; i < invStmtRsList.getLength(); i++) {
                Element invStmtRs = (Element) invStmtRsList.item(i);
                String accountId = extractAccountId(invStmtRs, "INVACCTFROM");
                if ((accountId == null || accountId.isEmpty())
                        && getElementText(invStmtRs, "BROKERID") != null) {
                    accountId = getElementText(invStmtRs, "BROKERID");
                }
                String statementCurrency = extractStatementCurrency(invStmtRs);
                if (fileCurrency == null
                        && statementCurrency != null
                        && !statementCurrency.isEmpty()) {
                    fileCurrency = statementCurrency;
                }
                index =
                        parseInvestmentTransactions(
                                invStmtRs, fileName, accountId, transactions, index);
            }

            log.info(
                    "OFX parsing complete: {} transactions parsed from {}",
                    transactions.size(),
                    fileName);
            return org.openfinance.dto.ImportParseResult.builder()
                    .transactions(transactions)
                    .ledgerBalance(ledgerBalance != null ? ledgerBalance : BigDecimal.ZERO)
                    .currency(fileCurrency)
                    .build();

        } catch (Exception e) {
            log.error("Error parsing OFX XML: {}", e.getMessage(), e);
            throw new IOException("Failed to parse OFX file: " + e.getMessage(), e);
        }
    }

    // -------------------------------------------------------------------------
    // Standard transaction parsing
    // -------------------------------------------------------------------------

    private ImportedTransaction parseTransaction(
            Element stmtTrn,
            String fileName,
            int lineNumber,
            String globalAccountId,
            String globalCurrency) {
        ImportedTransaction.ImportedTransactionBuilder builder = ImportedTransaction.builder();

        builder.sourceFileName(fileName);
        builder.lineNumber(lineNumber);
        if (globalAccountId != null && !globalAccountId.isEmpty()) {
            builder.accountName(globalAccountId);
        }

        // DTPOSTED — posted date (preferred)
        String dtPosted = getElementText(stmtTrn, "DTPOSTED");
        // DTUSER — user-initiated date, fallback when DTPOSTED absent
        String dtUser = getElementText(stmtTrn, "DTUSER");

        LocalDate date = null;
        if (dtPosted != null && !dtPosted.isEmpty()) {
            date = parseDate(dtPosted);
        }
        if (date == null && dtUser != null && !dtUser.isEmpty()) {
            log.debug("DTPOSTED absent or unparseable — falling back to DTUSER");
            date = parseDate(dtUser);
        }
        builder.transactionDate(date);

        // TRNAMT — amount
        String trnAmt = getElementText(stmtTrn, "TRNAMT");
        builder.amount(parseAmount(trnAmt));

        // Reference number: CHECKNUM > REFNUM > FITID
        String fitId = getElementText(stmtTrn, "FITID");
        String checkNum = getElementText(stmtTrn, "CHECKNUM");
        String refNum = getElementText(stmtTrn, "REFNUM");

        if (checkNum != null && !checkNum.isEmpty()) {
            builder.referenceNumber(checkNum);
        } else if (refNum != null && !refNum.isEmpty()) {
            builder.referenceNumber(refNum);
        } else if (fitId != null && !fitId.isEmpty()) {
            builder.referenceNumber(fitId);
        }

        // NAME — payee
        String name = getElementText(stmtTrn, "NAME");

        // MEMO
        String memo = getElementText(stmtTrn, "MEMO");

        // Use MEMO as part of payee if present, as French OFX files often put the real
        // merchant in MEMO
        if (memo != null && !memo.trim().isEmpty()) {
            if (name != null && !name.trim().isEmpty()) {
                builder.payee(name.trim() + " - " + memo.trim());
            } else {
                builder.payee(memo.trim());
            }
        } else {
            builder.payee(name);
        }

        // TRNTYPE — append to memo for visibility
        String trnType = getElementText(stmtTrn, "TRNTYPE");
        if (trnType != null && !trnType.isEmpty()) {
            String base = (memo != null && !memo.isEmpty()) ? memo + " " : "";
            memo = base + "[" + trnType + "]";
        }
        builder.memo(memo);

        // CURRENCY / CURSYM — currency code
        String currency = getElementText(stmtTrn, "CURRENCY");
        if (currency == null || currency.isEmpty()) {
            currency = getElementText(stmtTrn, "CURSYM");
        }
        if (currency == null || currency.isEmpty()) {
            currency = globalCurrency;
        }
        if (currency != null && !currency.isEmpty()) {
            builder.currency(currency);
        }

        ImportedTransaction transaction = builder.build();
        validateTransaction(transaction);
        return transaction;
    }

    // -------------------------------------------------------------------------
    // Investment transaction parsing
    // -------------------------------------------------------------------------

    /**
     * Parse investment transactions from INVTRANLIST.
     *
     * <p>Supported aggregate elements: BUYMF, BUYSTOCK, BUYDEBT, BUYOPT, BUYOTHER, SELLMF,
     * SELLSTOCK, SELLDEBT, SELLOPT, SELLOTHER, REINVEST, INCOME, INVEXPENSE, JRNLFUND, JRNLSEC,
     * MARGININTEREST, TRANSFER, CLOSUREOPT.
     *
     * <p>The INVTRAN child provides DTSETTLE (or DTTRADE) and FITID. TOTAL is used for the amount
     * when present. Security name comes from MEMO or SECID/UNIQUEID.
     */
    private int parseInvestmentTransactions(
            Element invStmtRs,
            String fileName,
            String accountId,
            List<ImportedTransaction> transactions,
            int index) {
        // All investment aggregate types that may contain an INVTRAN sub-element
        String[] invTranTypes = {
            "BUYMF", "BUYSTOCK", "BUYDEBT", "BUYOPT", "BUYOTHER",
            "SELLMF", "SELLSTOCK", "SELLDEBT", "SELLOPT", "SELLOTHER",
            "REINVEST", "INCOME", "INVEXPENSE", "JRNLFUND", "JRNLSEC",
            "MARGININTEREST", "TRANSFER", "CLOSUREOPT"
        };

        for (String typeName : invTranTypes) {
            NodeList nodes = invStmtRs.getElementsByTagName(typeName);
            for (int i = 0; i < nodes.getLength(); i++) {
                Element el = (Element) nodes.item(i);
                ImportedTransaction txn =
                        parseInvestmentTransaction(el, typeName, fileName, ++index, accountId);
                if (txn != null) {
                    transactions.add(txn);
                }
            }
        }
        return index;
    }

    private ImportedTransaction parseInvestmentTransaction(
            Element invEl,
            String typeName,
            String fileName,
            int lineNumber,
            String globalAccountId) {
        ImportedTransaction.ImportedTransactionBuilder builder = ImportedTransaction.builder();
        builder.sourceFileName(fileName);
        builder.lineNumber(lineNumber);
        if (globalAccountId != null && !globalAccountId.isEmpty()) {
            builder.accountName(globalAccountId);
        }

        // Date: prefer DTSETTLE, fall back to DTTRADE
        String dtSettle = getElementText(invEl, "DTSETTLE");
        String dtTrade = getElementText(invEl, "DTTRADE");
        LocalDate date = null;
        if (dtSettle != null && !dtSettle.isEmpty()) {
            date = parseDate(dtSettle);
        }
        if (date == null && dtTrade != null && !dtTrade.isEmpty()) {
            date = parseDate(dtTrade);
        }
        // Also check INVTRAN child
        if (date == null) {
            NodeList invTranNodes = invEl.getElementsByTagName("INVTRAN");
            if (invTranNodes.getLength() > 0) {
                Element invTran = (Element) invTranNodes.item(0);
                String dt = getElementText(invTran, "DTSETTLE");
                if (dt == null || dt.isEmpty()) {
                    dt = getElementText(invTran, "DTTRADE");
                }
                if (dt != null && !dt.isEmpty()) {
                    date = parseDate(dt);
                }
            }
        }
        builder.transactionDate(date);

        // Amount: TOTAL preferred; fallback to UNITS * UNITPRICE
        String totalStr = getElementText(invEl, "TOTAL");
        BigDecimal amount = null;
        if (totalStr != null && !totalStr.isEmpty()) {
            amount = parseAmount(totalStr);
        }
        if (amount == null) {
            String unitsStr = getElementText(invEl, "UNITS");
            String priceStr = getElementText(invEl, "UNITPRICE");
            if (unitsStr != null && priceStr != null) {
                try {
                    BigDecimal units = new BigDecimal(unitsStr.trim());
                    BigDecimal price = new BigDecimal(priceStr.trim());
                    amount = units.multiply(price);
                } catch (NumberFormatException e) {
                    log.debug("Could not compute investment amount from UNITS/UNITPRICE");
                }
            }
        }
        builder.amount(amount);

        // Reference: FITID from INVTRAN child
        NodeList invTranList = invEl.getElementsByTagName("INVTRAN");
        if (invTranList.getLength() > 0) {
            Element invTran = (Element) invTranList.item(0);
            String fitId = getElementText(invTran, "FITID");
            if (fitId != null && !fitId.isEmpty()) {
                builder.referenceNumber(fitId);
            }
        }

        // Payee: use security UNIQUEID as payee, fall back to MEMO
        String secId = getElementText(invEl, "UNIQUEID");
        String memo = getElementText(invEl, "MEMO");
        builder.payee(secId != null && !secId.isEmpty() ? secId : memo);
        builder.memo(memo);

        // Tag the transaction type for categorisation hints
        builder.category("[" + typeName + "]");

        // Currency
        String currency = getElementText(invEl, "CURRENCY");
        if (currency == null || currency.isEmpty()) {
            currency = getElementText(invEl, "CURSYM");
        }
        if (currency != null && !currency.isEmpty()) {
            builder.currency(currency);
        }

        ImportedTransaction transaction = builder.build();
        validateTransaction(transaction);
        return transaction;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private String getElementText(Element parent, String tagName) {
        NodeList nodeList = parent.getElementsByTagName(tagName);
        if (nodeList.getLength() > 0) {
            Node node = nodeList.item(0);
            String text = node.getTextContent();
            return text != null ? text.trim() : null;
        }
        return null;
    }

    private String extractAccountId(Element statement, String accountContainerTag) {
        NodeList containers = statement.getElementsByTagName(accountContainerTag);
        if (containers.getLength() == 0) {
            return null;
        }
        return getElementText((Element) containers.item(0), "ACCTID");
    }

    private String extractStatementCurrency(Element statement) {
        String currency = getElementText(statement, "CURDEF");
        if (currency == null || currency.isEmpty()) {
            currency = getElementText(statement, "CURRENCY");
        }
        if (currency == null || currency.isEmpty()) {
            currency = getElementText(statement, "CURSYM");
        }
        return currency;
    }

    private LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) {
            return null;
        }
        // Remove timezone offset (e.g., [0:GMT])
        dateStr = dateStr.replaceAll("\\[.*\\]", "").trim();
        // Keep only digits and dots (for milliseconds variant)
        dateStr = dateStr.replaceAll("[^0-9.]", "");

        for (DateTimeFormatter formatter : DATE_FORMATS) {
            try {
                if (dateStr.length() >= 8) {
                    return LocalDate.parse(dateStr, formatter);
                }
            } catch (DateTimeParseException e) {
                // try next
            }
        }
        log.warn("Unable to parse OFX date: {}", dateStr);
        return null;
    }

    private BigDecimal parseAmount(String amountStr) {
        if (amountStr == null || amountStr.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(amountStr.trim());
        } catch (NumberFormatException e) {
            log.warn("Unable to parse OFX amount: {}", amountStr);
            return null;
        }
    }

    private void validateTransaction(ImportedTransaction transaction) {
        if (transaction.getTransactionDate() == null) {
            transaction.addValidationError("Transaction date is required");
        }
        if (transaction.getAmount() == null) {
            transaction.addValidationError("Transaction amount is required");
        } else if (transaction.getAmount().compareTo(BigDecimal.ZERO) == 0) {
            transaction.addValidationError("Transaction amount cannot be zero");
        }
        if (transaction.getTransactionDate() != null
                && transaction.getTransactionDate().isAfter(LocalDate.now())) {
            transaction.addValidationError("Transaction date cannot be in the future");
        }
        if (transaction.getReferenceNumber() == null
                || transaction.getReferenceNumber().isEmpty()) {
            log.debug("Transaction missing reference number (FITID)");
        }
    }
}
