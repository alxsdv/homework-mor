package org.example.payouts.core.ports.out;

import org.example.payouts.core.domain.Payout;

/** The ledger postings of a payout's life: hold when requested, then settle or release. */
public interface PayoutLedger {

    /** Available → pending. Throws InsufficientAvailableBalanceException, writing nothing, if available doesn't cover it. */
    void hold(Payout payout);

    /** Pending → out of cash. */
    void settle(Payout payout);

    /** Pending → back to available. */
    void release(Payout payout);
}
