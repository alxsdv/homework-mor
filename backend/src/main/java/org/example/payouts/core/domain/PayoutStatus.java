package org.example.payouts.core.domain;

/** PENDING holds the amount on the ledger until the payout ends, either SETTLED or FAILED. */
public enum PayoutStatus {
    /** Requested; the amount is held and no longer available to the merchant. */
    PENDING,
    /** The bank confirmed it: the money left our account. */
    SETTLED,
    /** The bank rejected it: the hold is released back to the merchant's available balance. */
    FAILED
}
