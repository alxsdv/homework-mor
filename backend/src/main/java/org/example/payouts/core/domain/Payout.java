package org.example.payouts.core.domain;

import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;

/** A payout of amount to a merchant. completedAt is set exactly when it has ended (status is not PENDING). */
public record Payout(
        PayoutId payoutId,
        String merchantId,
        String externalId,
        Currency currency,
        BigDecimal amount,
        Instant requestedAt,
        PayoutStatus status,
        @Nullable Instant completedAt) {

    public Payout {
        if ((completedAt == null) != (status == PayoutStatus.PENDING)) {
            throw new IllegalArgumentException(
                    "Payout " + payoutId.value() + " has status " + status + " but completedAt " + completedAt);
        }
    }

    public static Payout requested(PayoutId payoutId, String merchantId, String externalId, Currency currency,
                                   BigDecimal amount, Instant requestedAt) {
        return new Payout(payoutId, merchantId, externalId, currency, amount, requestedAt, PayoutStatus.PENDING,
                          null);
    }

    /** The same payout ended with outcome (SETTLED or FAILED) at completedAt. Only a PENDING payout can end. */
    public Payout complete(PayoutStatus outcome, Instant completedAt) {
        if (status != PayoutStatus.PENDING) {
            throw new IllegalStateException("Payout " + payoutId.value() + " has already ended: " + status);
        }
        if (outcome == PayoutStatus.PENDING) {
            throw new IllegalArgumentException("A payout ends SETTLED or FAILED, not " + outcome);
        }
        return new Payout(payoutId, merchantId, externalId, currency, amount, requestedAt, outcome, completedAt);
    }
}
