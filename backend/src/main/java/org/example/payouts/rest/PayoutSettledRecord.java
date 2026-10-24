package org.example.payouts.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

/** POST /payouts/{payoutId}/settle request body: the bank confirmed the payout. */
public record PayoutSettledRecord(
        @Schema(requiredMode = REQUIRED, example = "2026-10-05T10:00:00+02:00",
                description = "When the money left our bank account; ISO-8601 timestamp with an offset")
        @Nullable String settledAt) {}
