package org.example.payouts.core.ports.in;

import java.math.BigDecimal;
import java.util.Currency;

/** The merchant's available balance doesn't cover the payout. Maps to 409. */
public class PayoutExceedsAvailableException extends RuntimeException {

    public PayoutExceedsAvailableException(String merchantId, Currency currency, BigDecimal amount,
                                           BigDecimal available) {
        super("Payout of " + amount.toPlainString() + " " + currency + " exceeds the available balance "
              + available.toPlainString() + " " + currency + " of merchant " + merchantId);
    }
}
