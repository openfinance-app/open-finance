package org.openfinance.service.parser;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Set;
import org.openfinance.dto.ImportedTransaction;
import org.openfinance.entity.AssetType;

/** Common investment semantics: signed units and cash are distinct movements. */
final class InvestmentParseSupport {
    private InvestmentParseSupport() {}

    static void qif(
            ImportedTransaction.ImportedTransactionBuilder builder,
            BigDecimal quantity,
            BigDecimal price,
            Locale locale) {
        ImportedTransaction row = builder.build();
        String action =
                row.getReferenceNumber() == null
                        ? ""
                        : row.getReferenceNumber().trim().toLowerCase(Locale.ROOT);
        builder.authoritativeReference(false);
        if (Set.of("div", "intinc", "miscinc", "miscexp", "xin", "xout").contains(action)) {
            boolean debit = Set.of("miscexp", "xout").contains(action);
            if (row.getAmount() != null)
                builder.amount(debit ? row.getAmount().abs().negate() : row.getAmount().abs());
            return;
        }
        boolean buy = "buy".equals(action);
        boolean sell = "sell".equals(action);
        boolean reinvest =
                Set.of("reinvdiv", "reinvint", "reinvlg", "reinvmd", "reinvsh").contains(action);
        boolean shares = Set.of("shrsin", "shrsout").contains(action);
        if (!buy && !sell && !reinvest && !shares) {
            error(builder, "import.validation.investment.unsupported", locale);
            return;
        }
        apply(
                builder,
                quantity,
                price,
                row.getInvestmentName(),
                "qif:" + row.getInvestmentName(),
                AssetType.OTHER,
                sell || "shrsout".equals(action),
                buy || sell,
                buy || "shrsout".equals(action),
                locale);
    }

    static void ofx(
            ImportedTransaction.ImportedTransactionBuilder builder,
            String action,
            BigDecimal quantity,
            BigDecimal price,
            String security,
            String securityType,
            Locale locale) {
        if (Set.of("INCOME", "INVEXPENSE").contains(action)) return;
        boolean buy = Set.of("BUYSTOCK", "BUYMF", "BUYOTHER").contains(action);
        boolean sell = Set.of("SELLSTOCK", "SELLMF", "SELLOTHER").contains(action);
        if (!buy && !sell && !"REINVEST".equals(action)) {
            error(builder, "import.validation.investment.unsupported", locale);
            return;
        }
        AssetType type =
                action.endsWith("MF")
                        ? AssetType.MUTUAL_FUND
                        : action.endsWith("STOCK") ? AssetType.STOCK : AssetType.OTHER;
        apply(
                builder,
                quantity,
                price,
                security,
                "ofx:" + securityType + ":" + security,
                type,
                sell,
                buy || sell,
                buy,
                locale);
    }

    private static void apply(
            ImportedTransaction.ImportedTransactionBuilder builder,
            BigDecimal quantity,
            BigDecimal price,
            String name,
            String identity,
            AssetType type,
            boolean outgoingUnits,
            boolean cash,
            boolean debit,
            Locale locale) {
        BigDecimal amount = builder.build().getAmount();
        if (quantity == null || quantity.signum() == 0 || name == null || name.isBlank()) {
            error(builder, "import.validation.investment.incomplete", locale);
            return;
        }
        if (price == null && amount != null)
            price = amount.abs().divide(quantity.abs(), 18, RoundingMode.HALF_UP);
        if (price == null || price.signum() <= 0 || amount == null || amount.signum() == 0) {
            error(builder, "import.validation.investment.incomplete", locale);
            return;
        }
        builder.investmentQuantity(outgoingUnits ? quantity.abs().negate() : quantity.abs())
                .investmentSecurity(identity)
                .investmentName(name)
                .investmentType(type)
                .investmentPrice(price)
                .investmentCash(cash)
                .amount(debit ? amount.abs().negate() : amount.abs());
    }

    private static void error(
            ImportedTransaction.ImportedTransactionBuilder builder, String key, Locale locale) {
        java.util.List<String> errors =
                new java.util.ArrayList<>(builder.build().getValidationErrors());
        errors.add(ImportParseSupport.message(key, locale));
        builder.validationErrors(errors);
    }
}
