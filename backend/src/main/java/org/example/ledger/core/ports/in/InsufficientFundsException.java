package org.example.ledger.core.ports.in;

import org.example.ledger.core.domain.AccountKey;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** A transaction would take more out of an account than it has available; nothing was written. */
public class InsufficientFundsException extends RuntimeException {

    private final AccountKey account;
    private final BigDecimal available;
    private final BigDecimal requested;

    /** Both amounts are positive when money is owed out, at the currency's scale. */
    public InsufficientFundsException(AccountKey account, BigDecimal available, BigDecimal requested) {
        super(message(account, available, requested));
        this.account = account;
        this.available = available;
        this.requested = requested;
    }

    private static String message(AccountKey account, BigDecimal available, BigDecimal requested) {
        int digits = account.currency().getDefaultFractionDigits();
        return "Requested " + requested.setScale(digits, RoundingMode.UNNECESSARY).toPlainString() + " "
               + account.currency() + " exceeds the available "
               + available.setScale(digits, RoundingMode.UNNECESSARY).toPlainString();
    }

    public AccountKey account() {
        return account;
    }

    public BigDecimal available() {
        return available;
    }

    public BigDecimal requested() {
        return requested;
    }
}
