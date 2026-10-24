package org.example.payouts.core.ports.in;

import org.example.payouts.core.domain.Payout;

public interface RequestPayoutUseCase {

    /**
     * Records a PENDING payout and holds its amount: it moves from the merchant's available balance to pending,
     * checked under the merchant account's row lock. Repeating a request (same merchantId + externalId and the
     * same currency, amount and requestedAt) returns the same payout and holds nothing more.
     * Throws PayoutExceedsAvailableException, leaving nothing behind, if the available balance doesn't cover it.
     */
    Payout requestPayout(RequestPayoutCommand command);
}
