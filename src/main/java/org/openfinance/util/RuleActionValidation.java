package org.openfinance.util;

import java.math.BigDecimal;
import org.openfinance.entity.RuleActionType;

/** Parameter checks shared by request validation and historical saved rules. */
public final class RuleActionValidation {
    private RuleActionValidation() {}

    public static boolean hasRequiredValue(RuleActionType type, String value) {
        return type == RuleActionType.SKIP_TRANSACTION || (value != null && !value.isBlank());
    }

    public static boolean hasValidAmount(RuleActionType type, String value, String splitAmount) {
        if (type != RuleActionType.ADD_SPLIT && type != RuleActionType.SET_AMOUNT) return true;
        String raw = type == RuleActionType.ADD_SPLIT ? splitAmount : value;
        if (raw == null || raw.isBlank()) return false;
        try {
            BigDecimal amount = new BigDecimal(raw.trim()).stripTrailingZeros();
            return amount.signum() != 0
                    && (type != RuleActionType.ADD_SPLIT || amount.signum() > 0)
                    && amount.scale() <= 18
                    && amount.precision() - amount.scale() <= 26;
        } catch (NumberFormatException ex) {
            return false;
        }
    }
}
