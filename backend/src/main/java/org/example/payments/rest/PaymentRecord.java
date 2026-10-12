package org.example.payments.rest;

import org.example.payments.core.ports.in.InvalidPaymentException;
import org.example.payments.core.ports.in.RecordSettledPaymentCommand;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Currency;

import static org.example.payments.rest.RequestFields.required;

/** POST /payments request body. Amounts are strings so no precision is lost in JSON. */
public record PaymentRecord(
        @Nullable String externalId,
        @Nullable String settledAt,
        @Nullable String merchantId,
        @Nullable Customer customer,
        @Nullable String currency,
        @Nullable String totalAmount,
        @Nullable String taxRate) {

    public RecordSettledPaymentCommand toCommand() {
        return new RecordSettledPaymentCommand(
                required(externalId, "externalId"),
                parseInstant(required(settledAt, "settledAt")),
                required(merchantId, "merchantId"),
                required(customer, "customer").toDomain(),
                parseCurrency(required(currency, "currency")),
                parseDecimal(required(totalAmount, "totalAmount"), "totalAmount"),
                parseDecimal(required(taxRate, "taxRate"), "taxRate"));
    }

    private static Instant parseInstant(String value) {
        try {
            return OffsetDateTime.parse(value).toInstant();
        } catch (DateTimeParseException e) {
            throw new InvalidPaymentException(
                    "settledAt must be an ISO-8601 timestamp with an offset, e.g. 2026-09-30T23:30:00+02:00, got: "
                    + value);
        }
    }

    private static Currency parseCurrency(String value) {
        try {
            return Currency.getInstance(value);
        } catch (IllegalArgumentException e) {
            throw new InvalidPaymentException("currency must be an ISO 4217 code, got: " + value);
        }
    }

    private static BigDecimal parseDecimal(String value, String field) {
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            throw new InvalidPaymentException(field + " must be a decimal number, got: " + value);
        }
    }
}
