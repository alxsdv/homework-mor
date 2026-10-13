package org.example.payments.rest;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Formats amounts and rates for response bodies. */
final class ResponseFields {

    private ResponseFields() {}

    /** At the currency's scale, as a plain string: 97.00, or 882 for JPY. */
    static String money(BigDecimal amount, int digits) {
        return amount.setScale(digits, RoundingMode.UNNECESSARY).toPlainString();
    }

    /** numeric(9,6) comes back as 0.190000; show 0.19. */
    static String rate(BigDecimal rate) {
        return rate.stripTrailingZeros().toPlainString();
    }
}
