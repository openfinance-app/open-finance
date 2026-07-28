package org.openfinance.util;

import static org.assertj.core.api.Assertions.*;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Unit tests pinning the shared {@link MathConstants} values. */
@DisplayName("MathConstants Tests")
class MathConstantsTest {

    @Test
    @DisplayName("HUNDRED is exactly 100")
    void hundredIsOneHundred() {
        assertThat(MathConstants.HUNDRED).isEqualByComparingTo(new BigDecimal("100"));
    }
}
