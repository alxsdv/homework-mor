package org.example.payouts.core.ports.out;

import org.example.payouts.core.domain.Payout;
import org.example.payouts.core.domain.PayoutId;

import java.util.Optional;

public interface PayoutsRepository {
    PayoutId nextId();

    /** Exact, case-sensitive match. */
    Optional<Payout> findByMerchantAndExternalId(String merchantId, String externalId);

    /**
     * SELECT … FOR UPDATE by public id. Call it inside a transaction: the row lock is held until that transaction
     * ends, so concurrent attempts to end the same payout take turns.
     */
    Optional<Payout> findByIdForUpdate(PayoutId payoutId);

    /** Throws DuplicatePayoutIdException or DuplicatePayoutExternalIdException on a unique-key violation. */
    Payout addPayout(Payout payout);

    /** Stores the status and completedAt of a payout that was PENDING. */
    void updateCompleted(Payout payout);
}
