package org.example.payouts.core.ports.out;

import org.example.payouts.core.domain.PayoutId;

public class DuplicatePayoutIdException extends RuntimeException {
    private final PayoutId payoutId;

    public DuplicatePayoutIdException(PayoutId payoutId) {
        super("Payout ID already exists: " + payoutId.value());
        this.payoutId = payoutId;
    }

    public PayoutId payoutId() {
        return payoutId;
    }
}
