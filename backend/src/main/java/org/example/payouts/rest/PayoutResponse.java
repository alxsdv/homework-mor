package org.example.payouts.rest;

import org.example.payouts.core.domain.Payout;
import org.jspecify.annotations.Nullable;

import java.math.RoundingMode;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

/** Body of every payout response: the amount as a string, times in UTC, completedAt null while PENDING. */
public record PayoutResponse(
        String payoutId,
        String externalId,
        String merchantId,
        String currency,
        String amount,
        String status,
        String requestedAt,
        @Nullable String completedAt) {

    public static PayoutResponse fromDomain(Payout payout) {
        Instant completedAt = payout.completedAt();
        return new PayoutResponse(
                payout.payoutId().value(),
                payout.externalId(),
                payout.merchantId(),
                payout.currency().getCurrencyCode(),
                payout.amount().setScale(payout.currency().getDefaultFractionDigits(), RoundingMode.UNNECESSARY)
                        .toPlainString(),
                payout.status().name(),
                DateTimeFormatter.ISO_INSTANT.format(payout.requestedAt()),
                completedAt == null ? null : DateTimeFormatter.ISO_INSTANT.format(completedAt));
    }
}
