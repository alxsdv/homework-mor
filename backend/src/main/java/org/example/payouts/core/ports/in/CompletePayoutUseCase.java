package org.example.payouts.core.ports.in;

import org.example.payouts.core.domain.Payout;

/**
 * Ends a PENDING payout. Each payout ends once: repeating the same outcome and time returns it unchanged, anything
 * else throws PayoutAlreadyCompletedException.
 */
public interface CompletePayoutUseCase {

    /** The money left our bank account: the held amount leaves cash. */
    Payout settle(CompletePayoutCommand command);

    /** The bank rejected the payout: the held amount goes back to the merchant's available balance. */
    Payout fail(CompletePayoutCommand command);
}
