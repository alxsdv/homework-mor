package org.example.ledger.core.domain;

/** Kinds of ledger accounts. */
public enum AccountType {
    /** Money we hold. Key: currency. */
    CASH_CLEARING,
    /** Fees we earned. Key: currency. */
    FEE_REVENUE,
    /** What we owe a merchant. Key: currency + merchant. */
    MERCHANT_PAYABLE,
    /** What we owe a tax authority. Key: currency + country. */
    TAX_PAYABLE;

    /** Only merchant accounts keep a cached balance; the others are shared hot rows and are summed on demand. */
    public boolean hasCachedBalance() {
        return this == MERCHANT_PAYABLE;
    }
}
