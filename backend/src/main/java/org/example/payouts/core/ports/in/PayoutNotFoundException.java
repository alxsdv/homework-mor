package org.example.payouts.core.ports.in;

/** No payout has this id; a malformed id can't name one either. Maps to 404. */
public class PayoutNotFoundException extends RuntimeException {

    public PayoutNotFoundException(String payoutId) {
        super("Payout not found: " + payoutId);
    }
}
