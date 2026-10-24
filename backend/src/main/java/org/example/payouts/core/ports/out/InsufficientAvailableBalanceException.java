package org.example.payouts.core.ports.out;

import java.math.BigDecimal;

/** The merchant's available balance, read under its lock, doesn't cover a hold. */
public class InsufficientAvailableBalanceException extends RuntimeException {

    private final BigDecimal available;

    /** available is at the currency's scale. */
    public InsufficientAvailableBalanceException(BigDecimal available) {
        super("The available balance " + available.toPlainString() + " doesn't cover the hold");
        this.available = available;
    }

    public BigDecimal available() {
        return available;
    }
}
