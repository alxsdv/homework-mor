package org.example.payouts.core.ports.in;

import org.example.payouts.core.domain.PayoutId;

import java.time.Instant;

/** The bank's answer for a payout: when it settled or failed. */
public record CompletePayoutCommand(PayoutId payoutId, Instant completedAt) {

    public CompletePayoutCommand {
        completedAt = PayoutTimes.validated(completedAt, "completedAt");
    }
}
