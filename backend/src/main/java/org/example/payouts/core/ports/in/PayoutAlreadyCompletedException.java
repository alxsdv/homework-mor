package org.example.payouts.core.ports.in;

import org.example.payouts.core.domain.Payout;

/** The payout has already ended, with another outcome or at another time. Maps to 409. */
public class PayoutAlreadyCompletedException extends RuntimeException {

    public PayoutAlreadyCompletedException(Payout payout) {
        super("Payout " + payout.payoutId().value() + " has already ended: " + payout.status() + " at "
              + payout.completedAt());
    }
}
