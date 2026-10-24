package org.example.payouts.core.ports.in;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;

/** Pay amount out of the merchant's available balance in currency. */
public record RequestPayoutCommand(
        String merchantId,
        String externalId,
        Currency currency,
        BigDecimal amount,
        Instant requestedAt) {

    /** payouts.amount is numeric(19,4): at most 15 integer digits. */
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("1000000000000000");
    /** Longer values overflow the btree entry of the (merchant_id, external_id) unique index. */
    private static final int MAX_EXTERNAL_ID_LENGTH = 255;

    public RequestPayoutCommand {
        if (merchantId.isBlank()) {
            throw new InvalidPayoutException("merchantId must not be blank");
        }
        if (externalId.isBlank()) {
            throw new InvalidPayoutException("externalId must not be blank");
        }
        if (externalId.length() > MAX_EXTERNAL_ID_LENGTH) {
            throw new InvalidPayoutException("externalId must be at most " + MAX_EXTERNAL_ID_LENGTH
                                             + " characters, got " + externalId.length());
        }
        requestedAt = PayoutTimes.validated(requestedAt, "requestedAt");
        int digits = currency.getDefaultFractionDigits();
        if (digits < 0) {
            throw new InvalidPayoutException("Currency " + currency + " has no minor units and is not supported");
        }
        if (amount.signum() <= 0) {
            throw new InvalidPayoutException("amount must be positive: " + amount);
        }
        if (amount.compareTo(MAX_AMOUNT) >= 0) {
            throw new InvalidPayoutException("amount is too large (must be below 10^15): " + amount);
        }
        if (amount.stripTrailingZeros().scale() > digits) {
            throw new InvalidPayoutException("amount has more decimal places than " + currency
                                             + " allows (" + digits + "): " + amount);
        }
        amount = amount.setScale(digits);
    }
}
