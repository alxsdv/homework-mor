package org.example.ledger.core.domain;

/** Kinds of ledger accounts. */
public enum AccountType {
    /** Money we hold. Key: currency. */
    CASH_CLEARING,
    /** Fees we earned. Key: currency. */
    FEE_REVENUE,
    /** What we owe a merchant and they can be paid out now (available). Key: currency + merchant. */
    MERCHANT_PAYABLE,
    /** What we owe a merchant, held for payouts in flight (pending). Key: currency + merchant. */
    MERCHANT_PAYOUT_PENDING,
    /** What we owe a tax authority. Key: currency + country. */
    TAX_PAYABLE;

    /**
     * Only MERCHANT_PAYABLE keeps a cached balance: its row lock serializes everything that moves the merchant's
     * available money. The shared accounts would be hot rows, and pending payouts are few, so both are summed on
     * demand.
     */
    public boolean hasCachedBalance() {
        return this == MERCHANT_PAYABLE;
    }

    /** The account types keyed by merchant. */
    public boolean isPerMerchant() {
        return this == MERCHANT_PAYABLE || this == MERCHANT_PAYOUT_PENDING;
    }
}
