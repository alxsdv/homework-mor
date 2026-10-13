package org.example.payments.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import org.example.payments.core.ports.in.InvalidPaymentException;
import org.example.payments.core.ports.in.RecordSettledPaymentCommand;
import org.jspecify.annotations.Nullable;

import java.util.Currency;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;
import static org.example.payments.rest.RequestFields.parseDecimal;
import static org.example.payments.rest.RequestFields.parseInstant;
import static org.example.payments.rest.RequestFields.required;

/** POST /payments request body. Amounts are strings so no precision is lost in JSON. */
public record PaymentRecord(
        @Schema(requiredMode = REQUIRED, example = "EXT-DEMO-1",
                description = "Your ID for the payment. Repeating a request with it returns the same payment.")
        @Nullable String externalId,
        @Schema(requiredMode = REQUIRED, example = "2026-10-01T12:00:00+02:00",
                description = "ISO-8601 timestamp with an offset")
        @Nullable String settledAt,
        @Schema(requiredMode = REQUIRED, example = "MERCH-001")
        @Nullable String merchantId,
        @Schema(requiredMode = REQUIRED)
        @Nullable Customer customer,
        @Schema(requiredMode = REQUIRED, example = "EUR", description = "ISO 4217 code")
        @Nullable String currency,
        @Schema(requiredMode = REQUIRED, example = "119.00",
                description = "What the customer paid, tax included, with at most the currency's decimal places")
        @Nullable String totalAmount,
        @Schema(requiredMode = REQUIRED, example = "0.19", description = "A fraction: 0.19 is 19%")
        @Nullable String taxRate) {

    public RecordSettledPaymentCommand toCommand() {
        return new RecordSettledPaymentCommand(
                required(externalId, "externalId"),
                parseInstant(required(settledAt, "settledAt"), "settledAt"),
                required(merchantId, "merchantId"),
                required(customer, "customer").toDomain(),
                parseCurrency(required(currency, "currency")),
                parseDecimal(required(totalAmount, "totalAmount"), "totalAmount"),
                parseDecimal(required(taxRate, "taxRate"), "taxRate"));
    }

    private static Currency parseCurrency(String value) {
        try {
            return Currency.getInstance(value);
        } catch (IllegalArgumentException e) {
            throw new InvalidPaymentException("currency must be an ISO 4217 code, got: " + value);
        }
    }
}
