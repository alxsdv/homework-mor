package org.example.payouts.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import org.example.payouts.core.ports.in.RequestPayoutCommand;
import org.jspecify.annotations.Nullable;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;
import static org.example.payouts.rest.RequestFields.parseCurrency;
import static org.example.payouts.rest.RequestFields.parseDecimal;
import static org.example.payouts.rest.RequestFields.parseInstant;
import static org.example.payouts.rest.RequestFields.required;

/** POST /payouts request body. The amount is a string so no precision is lost in JSON. */
public record PayoutRecord(
        @Schema(requiredMode = REQUIRED, example = "EXT-DEMO-PAYOUT-1",
                description = "Your ID for the payout. Repeating a request with it returns the same payout.")
        @Nullable String externalId,
        @Schema(requiredMode = REQUIRED, example = "MERCH-001")
        @Nullable String merchantId,
        @Schema(requiredMode = REQUIRED, example = "EUR", description = "ISO 4217 code")
        @Nullable String currency,
        @Schema(requiredMode = REQUIRED, example = "48.50",
                description = "What to pay out of the merchant's available balance, with at most the currency's "
                              + "decimal places")
        @Nullable String amount,
        @Schema(requiredMode = REQUIRED, example = "2026-10-03T09:00:00+02:00",
                description = "ISO-8601 timestamp with an offset")
        @Nullable String requestedAt) {

    public RequestPayoutCommand toCommand() {
        return new RequestPayoutCommand(
                required(merchantId, "merchantId"),
                required(externalId, "externalId"),
                parseCurrency(required(currency, "currency")),
                parseDecimal(required(amount, "amount"), "amount"),
                parseInstant(required(requestedAt, "requestedAt"), "requestedAt"));
    }
}
